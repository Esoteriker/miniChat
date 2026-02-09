package com.minichat.api.message;

import com.minichat.api.chat.ChatEntity;
import com.minichat.api.chat.ChatRepository;
import com.minichat.api.common.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;

    public MessageService(MessageRepository messageRepository, ChatRepository chatRepository) {
        this.messageRepository = messageRepository;
        this.chatRepository = chatRepository;
    }

    @Transactional(readOnly = true)
    public MessageDtos.MessagePageResponse list(UUID userId, UUID chatId, String cursor, Integer limit) {
        ensureChatOwnership(userId, chatId);

        int pageSize = sanitizeLimit(limit);
        Instant cursorCreatedAt = null;
        UUID cursorId = null;

        if (cursor != null && !cursor.isBlank()) {
            cursorId = parseCursor(cursor);
            MessageEntity cursorMessage = messageRepository.findByIdAndChatId(cursorId, chatId)
                .orElseThrow(() -> new NotFoundException("Cursor message not found"));
            cursorCreatedAt = cursorMessage.getCreatedAt();
        }

        List<MessageEntity> page = messageRepository.findPage(
            chatId,
            cursorCreatedAt,
            PageRequest.of(0, pageSize + 1)
        );

        boolean hasMore = page.size() > pageSize;
        List<MessageEntity> data = hasMore ? page.subList(0, pageSize) : page;
        List<MessageDtos.MessageResponse> items = new ArrayList<>(data.size());
        for (MessageEntity msg : data) {
            items.add(toResponse(msg));
        }

        String nextCursor = null;
        if (hasMore) {
            nextCursor = data.get(data.size() - 1).getId().toString();
        }

        return new MessageDtos.MessagePageResponse(items, nextCursor);
    }

    @Transactional
    public MessageDtos.MessageResponse createUserMessage(UUID userId, UUID chatId, String content) {
        ChatEntity chat = chatRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new NotFoundException("Chat not found"));

        MessageEntity message = new MessageEntity();
        message.setChatId(chatId);
        message.setRole("user");
        message.setContent(content.trim());

        MessageEntity saved = messageRepository.save(message);
        chat.touch();
        chatRepository.save(chat);

        return toResponse(saved);
    }

    private void ensureChatOwnership(UUID userId, UUID chatId) {
        if (!chatRepository.existsByIdAndUserId(chatId, userId)) {
            throw new NotFoundException("Chat not found");
        }
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private UUID parseCursor(String cursor) {
        try {
            return UUID.fromString(cursor);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("cursor must be a valid UUID");
        }
    }

    private MessageDtos.MessageResponse toResponse(MessageEntity entity) {
        return new MessageDtos.MessageResponse(entity.getId(), entity.getRole(), entity.getContent(), entity.getCreatedAt());
    }
}
