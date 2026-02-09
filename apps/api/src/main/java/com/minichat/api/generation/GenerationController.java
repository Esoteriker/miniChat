package com.minichat.api.generation;

import com.minichat.api.common.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Validated
@RequestMapping("/api")
public class GenerationController {

    private final GenerationService generationService;

    public GenerationController(GenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping("/chats/{id}/generations")
    public GenerationDtos.CreateGenerationResponse create(@PathVariable("id") UUID chatId,
                                                          @Valid @RequestBody GenerationDtos.CreateGenerationRequest request) {
        return generationService.create(SecurityUtils.currentUserId(), chatId, request);
    }

    @GetMapping(value = "/generations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") UUID generationId) {
        return generationService.stream(SecurityUtils.currentUserId(), generationId);
    }

    @PostMapping("/generations/{id}/cancel")
    public GenerationDtos.CancelGenerationResponse cancel(@PathVariable("id") UUID generationId) {
        return generationService.cancel(SecurityUtils.currentUserId(), generationId);
    }
}
