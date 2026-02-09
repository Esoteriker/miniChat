package com.minichat.api.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record CreateChatRequest(
        @Size(max = 255) String title
    ) {
    }

    public record RenameChatRequest(
        @NotBlank @Size(max = 255) String title
    ) {
    }

    public record ChatResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
