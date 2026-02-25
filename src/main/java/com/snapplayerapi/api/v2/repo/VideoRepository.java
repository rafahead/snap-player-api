package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.VideoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for tenant-scoped videos reused across snaps.
 */
public interface VideoRepository extends JpaRepository<VideoEntity, UUID> {
    /**
     * URL hash lookup used for deduplication on `POST /v2/snaps` with `videoUrl`.
     */
    Optional<VideoEntity> findByAssinaturaIdAndUrlHash(Long assinaturaId, String urlHash);
    /**
     * Tenant-safe lookup used when clients reference an existing `videoId`.
     */
    Optional<VideoEntity> findByIdAndAssinaturaId(UUID id, Long assinaturaId);
}
