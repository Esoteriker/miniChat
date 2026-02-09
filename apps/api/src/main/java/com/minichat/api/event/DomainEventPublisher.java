package com.minichat.api.event;

import com.minichat.api.config.RabbitConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUsage(UUID userId, UUID generationId, int inputTokens, int outputTokens, String model) {
        Map<String, Object> payload = Map.of(
            "type", "usage_event",
            "userId", userId.toString(),
            "generationId", generationId.toString(),
            "inputTokens", inputTokens,
            "outputTokens", outputTokens,
            "model", model
        );
        publishAfterCommit(RabbitConfig.USAGE_EVENT_QUEUE, payload);
    }

    public void publishAudit(UUID userId, String action, Map<String, Object> metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "audit_event");
        payload.put("userId", userId == null ? null : userId.toString());
        payload.put("action", action);
        payload.put("metadata", metadata);
        publishAfterCommit(RabbitConfig.AUDIT_EVENT_QUEUE, payload);
    }

    private void publishAfterCommit(String queue, Map<String, Object> payload) {
        Runnable task = () -> safePublish(queue, payload);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void safePublish(String queue, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend("", queue, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish {}: {}", queue, ex.getMessage());
        }
    }
}
