package com.minichat.api.generation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class GenerationDtos {

    private GenerationDtos() {
    }

    public record CreateGenerationRequest(
        @NotBlank @Size(max = 20000) String userMessage,
        @Size(max = 100) String model,
        @Size(max = 10000) String systemPrompt,
        @Min(0) @Max(2) Double temperature,
        @Min(1) @Max(4096) Integer maxTokens,
        @Size(max = 255) String requestId
    ) {
    }

    public record CreateGenerationResponse(UUID generationId) {
    }

    public record CancelGenerationResponse(String status) {
    }
}
