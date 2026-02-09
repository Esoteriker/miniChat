package com.minichat.api.message;

import com.minichat.api.common.SecurityUtils;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/chats/{id}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public MessageDtos.MessagePageResponse list(@PathVariable("id") UUID chatId,
                                                @RequestParam(value = "cursor", required = false) String cursor,
                                                @RequestParam(value = "limit", required = false) Integer limit) {
        return messageService.list(SecurityUtils.currentUserId(), chatId, cursor, limit);
    }

    @PostMapping
    public MessageDtos.MessageResponse create(@PathVariable("id") UUID chatId,
                                              @Valid @RequestBody MessageDtos.CreateMessageRequest request) {
        return messageService.createUserMessage(SecurityUtils.currentUserId(), chatId, request.content());
    }
}
