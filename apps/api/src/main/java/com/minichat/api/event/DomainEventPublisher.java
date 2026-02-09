package com.minichat.api.event;

import com.minichat.api.config.RabbitConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

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
        safePublish(RabbitConfig.USAGE_EVENT_QUEUE, payload);
    }

    public void publishAudit(UUID userId, String action, Map<String, Object> metadata) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "audit_event");
        payload.put("userId", userId == null ? null : userId.toString());
        payload.put("action", action);
        payload.put("metadata", metadata);
        safePublish(RabbitConfig.AUDIT_EVENT_QUEUE, payload);
    }

    private void safePublish(String queue, Map<String, Object> payload) {
        try {
            rabbitTemplate.convertAndSend("", queue, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish {}: {}", queue, ex.getMessage());
        }
    }
}
