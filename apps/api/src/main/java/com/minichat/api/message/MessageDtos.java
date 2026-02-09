package com.minichat.api.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessageDtos {
    private MessageDtos() {
    }

    public record CreateMessageRequest(
        @NotBlank @Size(max = 20000) String content
    ) {
    }

    public record MessageResponse(
        UUID id,
        String role,
        String content,
        Instant createdAt
    ) {
    }

    public record MessagePageResponse(
        List<MessageResponse> items,
        String nextCursor
    ) {
    }
}
