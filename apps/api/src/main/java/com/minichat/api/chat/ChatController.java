package com.minichat.api.chat;

import com.minichat.api.common.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatDtos.ChatResponse create(@Valid @RequestBody ChatDtos.CreateChatRequest request) {
        return chatService.create(SecurityUtils.currentUserId(), request.title());
    }

    @GetMapping
    public List<ChatDtos.ChatResponse> list() {
        return chatService.list(SecurityUtils.currentUserId());
    }

    @PatchMapping("/{id}")
    public ChatDtos.ChatResponse rename(@PathVariable("id") UUID chatId,
                                        @Valid @RequestBody ChatDtos.RenameChatRequest request) {
        return chatService.rename(SecurityUtils.currentUserId(), chatId, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID chatId) {
        chatService.delete(SecurityUtils.currentUserId(), chatId);
        return ResponseEntity.noContent().build();
    }
}
