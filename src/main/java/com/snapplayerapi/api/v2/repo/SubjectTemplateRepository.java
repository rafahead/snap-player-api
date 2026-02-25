package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.SubjectTemplateEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for subject templates scoped by assinatura.
 */
public interface SubjectTemplateRepository extends JpaRepository<SubjectTemplateEntity, Long> {
    /**
     * Explicit template selection constrained to the active assinatura.
     */
    Optional<SubjectTemplateEntity> findByAssinaturaIdAndId(Long assinaturaId, Long id);
    /**
     * Main fallback path for Entrega 1.
     */
    Optional<SubjectTemplateEntity> findByAssinaturaIdAndIsDefaultTrue(Long assinaturaId);
    /**
     * Secondary fallback by slug (`default`) for compatibility with seed/template changes.
     */
    Optional<SubjectTemplateEntity> findByAssinaturaIdAndSlug(Long assinaturaId, String slug);
}
