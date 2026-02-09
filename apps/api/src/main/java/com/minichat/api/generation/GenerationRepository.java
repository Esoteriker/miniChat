package com.minichat.api.generation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationRepository extends JpaRepository<GenerationEntity, UUID> {
    Optional<GenerationEntity> findByIdAndUserId(UUID id, UUID userId);
    Optional<GenerationEntity> findByRequestIdAndUserId(String requestId, UUID userId);
}
