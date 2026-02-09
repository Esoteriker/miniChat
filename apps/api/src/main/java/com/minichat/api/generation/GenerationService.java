package com.minichat.api.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.minichat.api.chat.ChatEntity;
import com.minichat.api.chat.ChatRepository;
import com.minichat.api.common.ConflictException;
import com.minichat.api.common.NotFoundException;
import com.minichat.api.event.DomainEventPublisher;
import com.minichat.api.inference.InferenceClient;
import com.minichat.api.limit.GenerationLimitService;
import com.minichat.api.message.MessageEntity;
import com.minichat.api.message.MessageRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GenerationService {

    private final GenerationRepository generationRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final InferenceClient inferenceClient;
    private final GenerationLimitService limitService;
    private final DomainEventPublisher eventPublisher;
    private final TaskExecutor generationExecutor;
    private final String defaultModel;
    private final double defaultTemperature;
    private final int defaultMaxTokens;

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Set<UUID> cancelRequested = ConcurrentHashMap.newKeySet();

    public GenerationService(GenerationRepository generationRepository,
                             ChatRepository chatRepository,
                             MessageRepository messageRepository,
                             InferenceClient inferenceClient,
                             GenerationLimitService limitService,
                             DomainEventPublisher eventPublisher,
                             @Qualifier("generationExecutor") TaskExecutor generationExecutor,
                             @Value("${app.generation.default-model}") String defaultModel,
                             @Value("${app.generation.default-temperature}") double defaultTemperature,
                             @Value("${app.generation.default-max-tokens}") int defaultMaxTokens) {
        this.generationRepository = generationRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.inferenceClient = inferenceClient;
        this.limitService = limitService;
        this.eventPublisher = eventPublisher;
        this.generationExecutor = generationExecutor;
        this.defaultModel = defaultModel;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
    }

    @Transactional
    public GenerationDtos.CreateGenerationResponse create(UUID userId, UUID chatId, GenerationDtos.CreateGenerationRequest request) {
        ChatEntity chat = chatRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new NotFoundException("Chat not found"));

        String requestId = normalizeRequestId(request.requestId());
        if (requestId != null) {
            GenerationEntity existing = generationRepository.findByRequestIdAndUserId(requestId, userId).orElse(null);
            if (existing != null) {
                return new GenerationDtos.CreateGenerationResponse(existing.getId());
            }
        } else {
            requestId = UUID.randomUUID().toString();
        }

        MessageEntity userMessage = new MessageEntity();
        userMessage.setChatId(chatId);
        userMessage.setRole("user");
        userMessage.setContent(request.userMessage().trim());
        messageRepository.save(userMessage);

        chat.touch();
        chatRepository.save(chat);

        GenerationEntity generation = new GenerationEntity();
        generation.setChatId(chatId);
        generation.setUserId(userId);
        generation.setStatus(GenerationStatus.QUEUED);
        generation.setModel(orDefault(request.model(), defaultModel));
        generation.setSystemPrompt(request.systemPrompt());
        generation.setTemperature(request.temperature() == null ? defaultTemperature : request.temperature());
        generation.setMaxTokens(request.maxTokens() == null ? defaultMaxTokens : request.maxTokens());
        generation.setRequestId(requestId);

        GenerationEntity saved = generationRepository.save(generation);

        eventPublisher.publishAudit(userId, "create_generation", Map.of(
            "generationId", saved.getId().toString(),
            "chatId", chatId.toString(),
            "requestId", requestId
        ));

        return new GenerationDtos.CreateGenerationResponse(saved.getId());
    }

    @Transactional
    public GenerationDtos.CancelGenerationResponse cancel(UUID userId, UUID generationId) {
        GenerationEntity generation = loadOwned(generationId, userId);
        GenerationStatus status = generation.getStatus();

        if (status == GenerationStatus.SUCCEEDED || status == GenerationStatus.FAILED || status == GenerationStatus.CANCELED) {
            return new GenerationDtos.CancelGenerationResponse("accepted");
        }

        if (status == GenerationStatus.QUEUED) {
            generation.setStatus(GenerationStatus.CANCELED);
            generation.setErrorCode("canceled");
            generation.setErrorMessage("Canceled before stream");
            generation.setFinishedAt(Instant.now());
            generationRepository.save(generation);
        } else {
            cancelRequested.add(generationId);
            inferenceClient.cancelGeneration(generationId);
        }

        eventPublisher.publishAudit(userId, "cancel_generation", Map.of("generationId", generationId.toString()));
        return new GenerationDtos.CancelGenerationResponse("accepted");
    }

    @Transactional
    public SseEmitter stream(UUID userId, UUID generationId) {
        GenerationEntity generation = loadOwned(generationId, userId);
        if (generation.getStatus() != GenerationStatus.QUEUED) {
            throw new ConflictException("Generation is not in queued state");
        }

        limitService.enforceQps(userId);
        if (!limitService.tryAcquireInflight(userId, generationId)) {
            throw new ConflictException("Only one in-flight generation is allowed per user");
        }

        generation.setStatus(GenerationStatus.STREAMING);
        generation.setStartedAt(Instant.now());
        generation.setErrorCode(null);
        generation.setErrorMessage(null);
        generationRepository.save(generation);

        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(generationId, emitter);
        emitter.onCompletion(() -> emitters.remove(generationId));
        emitter.onTimeout(() -> emitters.remove(generationId));

        generationExecutor.execute(() -> runStreamLoop(userId, generationId, emitter));
        return emitter;
    }

    private void runStreamLoop(UUID userId, UUID generationId, SseEmitter emitter) {
        StringBuilder assistantText = new StringBuilder();
        AtomicReference<Integer> inputTokens = new AtomicReference<>(null);
        AtomicReference<Integer> outputTokens = new AtomicReference<>(null);
        AtomicReference<String> errorCode = new AtomicReference<>(null);
        AtomicReference<String> errorMessage = new AtomicReference<>(null);
        AtomicBoolean doneReceived = new AtomicBoolean(false);

        try {
            GenerationEntity generation = loadOwned(generationId, userId);
            InferenceClient.GenerateRequest payload = buildInferencePayload(generation);

            inferenceClient.streamGenerate(payload, event -> {
                String type = event.path("type").asText();
                if ("delta".equals(type)) {
                    assistantText.append(event.path("delta").asText(""));
                } else if ("usage".equals(type)) {
                    inputTokens.set(event.path("inputTokens").asInt(0));
                    outputTokens.set(event.path("outputTokens").asInt(0));
                } else if ("error".equals(type)) {
                    errorCode.set(event.path("code").asText("inference_error"));
                    errorMessage.set(event.path("message").asText("Inference error"));
                } else if ("done".equals(type)) {
                    doneReceived.set(true);
                }

                sendEvent(emitter, event);
            });
        } catch (Exception ex) {
            if (errorCode.get() == null) {
                errorCode.set("inference_error");
                errorMessage.set(ex.getMessage() == null ? "Inference stream failed" : ex.getMessage());
            }
        } finally {
            finalizeStream(userId, generationId, assistantText.toString(), inputTokens.get(), outputTokens.get(),
                errorCode.get(), errorMessage.get(), doneReceived.get(), emitter);
        }
    }

    @Transactional
    protected void finalizeStream(UUID userId,
                                  UUID generationId,
                                  String assistantText,
                                  Integer inputTokens,
                                  Integer outputTokens,
                                  String errorCode,
                                  String errorMessage,
                                  boolean doneReceived,
                                  SseEmitter emitter) {
        try {
            GenerationEntity generation = loadOwned(generationId, userId);
            boolean canceled = cancelRequested.remove(generationId) || "canceled".equals(errorCode);

            if (canceled) {
                generation.setStatus(GenerationStatus.CANCELED);
                if (errorCode == null) {
                    errorCode = "canceled";
                }
                if (errorMessage == null) {
                    errorMessage = "Canceled by user";
                }
            } else if (errorCode != null) {
                generation.setStatus(GenerationStatus.FAILED);
            } else if (doneReceived) {
                generation.setStatus(GenerationStatus.SUCCEEDED);
            } else {
                generation.setStatus(GenerationStatus.FAILED);
                errorCode = "stream_ended";
                errorMessage = "Stream ended before done event";
            }

            generation.setInputTokens(inputTokens);
            generation.setOutputTokens(outputTokens);
            generation.setErrorCode(errorCode);
            generation.setErrorMessage(errorMessage);
            generation.setFinishedAt(Instant.now());
            generationRepository.save(generation);

            if (!assistantText.isBlank() && (generation.getStatus() == GenerationStatus.SUCCEEDED || generation.getStatus() == GenerationStatus.CANCELED)) {
                appendAssistantMessage(generation.getChatId(), assistantText);
            }

            if (generation.getStatus() == GenerationStatus.SUCCEEDED && inputTokens != null && outputTokens != null) {
                eventPublisher.publishUsage(userId, generationId, inputTokens, outputTokens, generation.getModel());
            }

            if (!doneReceived) {
                sendDone(emitter);
            }
        } finally {
            emitters.remove(generationId);
            limitService.releaseInflight(userId, generationId);
            emitter.complete();
        }
    }

    private InferenceClient.GenerateRequest buildInferencePayload(GenerationEntity generation) {
        List<InferenceClient.GenerateMessage> messages = new ArrayList<>();
        if (generation.getSystemPrompt() != null && !generation.getSystemPrompt().isBlank()) {
            messages.add(new InferenceClient.GenerateMessage("system", generation.getSystemPrompt()));
        }

        for (MessageEntity message : messageRepository.findHistoryByChatId(generation.getChatId())) {
            messages.add(new InferenceClient.GenerateMessage(message.getRole(), message.getContent()));
        }

        return new InferenceClient.GenerateRequest(
            generation.getId().toString(),
            generation.getModel(),
            generation.getSystemPrompt(),
            generation.getTemperature(),
            generation.getMaxTokens(),
            messages
        );
    }

    private void appendAssistantMessage(UUID chatId, String content) {
        MessageEntity assistant = new MessageEntity();
        assistant.setChatId(chatId);
        assistant.setRole("assistant");
        assistant.setContent(content);
        messageRepository.save(assistant);

        chatRepository.findById(chatId).ifPresent(chat -> {
            chat.touch();
            chatRepository.save(chat);
        });
    }

    private void sendEvent(SseEmitter emitter, JsonNode event) {
        try {
            emitter.send(SseEmitter.event().data(event.toString()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void sendDone(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("{\"type\":\"done\"}"));
        } catch (IOException ignored) {
            // Client may have already disconnected.
        }
    }

    private GenerationEntity loadOwned(UUID generationId, UUID userId) {
        return generationRepository.findByIdAndUserId(generationId, userId)
            .orElseThrow(() -> new NotFoundException("Generation not found"));
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return requestId.trim();
    }

    private String orDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
