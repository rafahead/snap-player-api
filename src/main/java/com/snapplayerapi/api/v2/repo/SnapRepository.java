package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.SnapEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for snap reads/writes plus Entrega 1 query shapes.
 */
public interface SnapRepository extends JpaRepository<SnapEntity, UUID> {
    /**
     * Tenant-scoped lookup used by `GET /v2/snaps/{snapId}`.
     */
    Optional<SnapEntity> findByIdAndAssinaturaId(UUID id, Long assinaturaId);

    /**
     * Video list ordered by timeline (`resolved_start_seconds`) and then creation order.
     */
    List<SnapEntity> findByVideoIdAndAssinaturaIdOrderByResolvedStartSecondsAscCreatedAtAsc(UUID videoId, Long assinaturaId);

    /**
     * Same list query with case-insensitive nickname filter.
     */
    List<SnapEntity> findByVideoIdAndAssinaturaIdAndNicknameSnapshotIgnoreCaseOrderByResolvedStartSecondsAscCreatedAtAsc(
            UUID videoId,
            Long assinaturaId,
            String nicknameSnapshot
    );

    /**
     * Subject-id lookup used by the basic search endpoint.
     */
    List<SnapEntity> findByAssinaturaIdAndSubjectIdOrderByCreatedAtDesc(Long assinaturaId, String subjectId);

    /**
     * Lists snaps by nickname for Entrega 2 "mine" views.
     */
    List<SnapEntity> findByAssinaturaIdAndNicknameSnapshotIgnoreCaseOrderByCreatedAtDesc(Long assinaturaId, String nicknameSnapshot);

    /**
     * Public lookup by share token. `isPublic=true` guards against tokens stored but not enabled.
     */
    Optional<SnapEntity> findByPublicShareTokenAndIsPublicTrue(String publicShareToken);

    /**
     * Exact match search for string attributes using the flattened `snap_subject_attr` table.
     *
     * <p>The query optionally narrows by `subjectId` when provided.</p>
     */
    @Query("""
            select distinct s
            from SnapEntity s, SnapSubjectAttrEntity a
            where s.assinaturaId = :assinaturaId
              and a.snapId = s.id
              and a.assinaturaId = :assinaturaId
              and (:subjectId is null or s.subjectId = :subjectId)
              and lower(a.attrKey) = lower(:attrKey)
              and a.valueType = 'STRING'
              and a.stringValue = :attrValue
            order by s.createdAt desc
            """)
    List<SnapEntity> searchByStringAttr(
            @Param("assinaturaId") Long assinaturaId,
            @Param("subjectId") String subjectId,
            @Param("attrKey") String attrKey,
            @Param("attrValue") String attrValue
    );
}
