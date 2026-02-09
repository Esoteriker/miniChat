package com.minichat.api.message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    @Query("""
        SELECT m FROM MessageEntity m
        WHERE m.chatId = :chatId
          AND (
                :cursorCreatedAt IS NULL
                OR m.createdAt > :cursorCreatedAt
              )
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<MessageEntity> findPage(
        @Param("chatId") UUID chatId,
        @Param("cursorCreatedAt") Instant cursorCreatedAt,
        Pageable pageable
    );

    @Query("""
        SELECT m FROM MessageEntity m
        WHERE m.chatId = :chatId
        ORDER BY m.createdAt ASC, m.id ASC
        """)
    List<MessageEntity> findHistoryByChatId(@Param("chatId") UUID chatId);

    Optional<MessageEntity> findByIdAndChatId(UUID id, UUID chatId);
}
