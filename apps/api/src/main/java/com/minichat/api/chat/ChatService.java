package com.minichat.api.chat;

import com.minichat.api.common.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private static final String DEFAULT_CHAT_TITLE = "New Chat";

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    @Transactional
    public ChatDtos.ChatResponse create(UUID userId, String title) {
        ChatEntity chat = new ChatEntity();
        chat.setUserId(userId);
        chat.setTitle(normalizeTitle(title));
        ChatEntity saved = chatRepository.save(chat);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ChatResponse> list(UUID userId) {
        return chatRepository.findAllByUserIdOrderByUpdatedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ChatDtos.ChatResponse rename(UUID userId, UUID chatId, String newTitle) {
        ChatEntity chat = chatRepository.findByIdAndUserId(chatId, userId)
            .orElseThrow(() -> new NotFoundException("Chat not found"));
        chat.setTitle(normalizeTitle(newTitle));
        chat.touch();
        return toResponse(chatRepository.save(chat));
    }

    @Transactional
    public void delete(UUID userId, UUID chatId) {
        if (!chatRepository.existsByIdAndUserId(chatId, userId)) {
            throw new NotFoundException("Chat not found");
        }
        chatRepository.deleteByIdAndUserId(chatId, userId);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_CHAT_TITLE;
        }
        return title.trim();
    }

    private ChatDtos.ChatResponse toResponse(ChatEntity chat) {
        return new ChatDtos.ChatResponse(chat.getId(), chat.getTitle(), chat.getCreatedAt(), chat.getUpdatedAt());
    }
}
