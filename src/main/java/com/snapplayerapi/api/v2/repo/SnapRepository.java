package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.SnapEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for snap reads/writes.
 *
 * <p>All list queries return {@link Slice} with a {@link Pageable} parameter so that the database
 * applies {@code LIMIT}/{@code OFFSET} before returning rows to the JVM. This prevents full table
 * scans for large assinaturas. Use {@link OffsetBasedPageRequest} to pass arbitrary offset+limit
 * values from the API layer. {@code Slice.hasNext()} replaces a count query — Spring Data fetches
 * {@code limit + 1} rows internally and trims the extra one from the content.</p>
 */
public interface SnapRepository extends JpaRepository<SnapEntity, UUID> {

    /**
     * Tenant-scoped lookup used by {@code GET /v2/snaps/{snapId}}.
     */
    Optional<SnapEntity> findByIdAndAssinaturaId(UUID id, Long assinaturaId);

    /**
     * Paginated video snap list ordered by the client-supplied {@link Pageable} sort.
     * Replaces the old unbounded {@code findByVideoIdAndAssinaturaIdOrderBy...} method.
     */
    Slice<SnapEntity> findByVideoIdAndAssinaturaId(UUID videoId, Long assinaturaId, Pageable pageable);

    /**
     * Same list with case-insensitive nickname filter.
     */
    Slice<SnapEntity> findByVideoIdAndAssinaturaIdAndNicknameSnapshotIgnoreCase(
            UUID videoId,
            Long assinaturaId,
            String nicknameSnapshot,
            Pageable pageable
    );

    /**
     * Subject-id lookup for the basic search endpoint. Paginated.
     */
    Slice<SnapEntity> findByAssinaturaIdAndSubjectId(Long assinaturaId, String subjectId, Pageable pageable);

    /**
     * Lists snaps by nickname for the "mine" view. Paginated.
     */
    Slice<SnapEntity> findByAssinaturaIdAndNicknameSnapshotIgnoreCase(
            Long assinaturaId,
            String nicknameSnapshot,
            Pageable pageable
    );

    /**
     * Public lookup by share token. {@code isPublic=true} guards against tokens that were stored
     * but not yet activated.
     */
    Optional<SnapEntity> findByPublicShareTokenAndIsPublicTrue(String publicShareToken);

    /**
     * Exact-match search on string attributes using the flattened {@code snap_subject_attr} table.
     * Optionally narrows by {@code subjectId} when provided. Paginated.
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
            """)
    Slice<SnapEntity> searchByStringAttr(
            @Param("assinaturaId") Long assinaturaId,
            @Param("subjectId") String subjectId,
            @Param("attrKey") String attrKey,
            @Param("attrValue") String attrValue,
            Pageable pageable
    );

    /**
     * Aggregated video activity for {@code GET /v2/videos/mine}.
     *
     * <p>Groups snaps by {@code videoId} and returns per-video aggregates:
     * {@code [videoId, videoUrl, snapCount, latestSnapCreatedAt]}. The result is paginated at the
     * database level via the supplied {@link Pageable}. Use {@code JpaSort.unsafe} for sort fields
     * that map to aggregate expressions ({@code max(s.createdAt)}, {@code count(s)}).</p>
     *
     * <p>{@code max(s.videoUrl)} is used to select the URL for the group — all snaps for the same
     * video share the same URL so any would be equivalent.</p>
     */
    @Query("""
            select s.videoId, max(s.videoUrl) as videoUrl, count(s) as snapCount, max(s.createdAt) as latestSnapCreatedAt
            from SnapEntity s
            where s.assinaturaId = :assinaturaId
              and lower(s.nicknameSnapshot) = lower(:nickname)
            group by s.videoId
            """)
    Slice<Object[]> findVideoAggregatesForNickname(
            @Param("assinaturaId") Long assinaturaId,
            @Param("nickname") String nickname,
            Pageable pageable
    );

    /**
     * Returns the most-recent snap ID per video for the provided video IDs.
     * Used to populate {@code latestSnapId} in the {@code GET /v2/videos/mine} response
     * after the paginated group-by query has determined the active page of videos.
     *
     * <p>Returns {@code Object[]} rows of {@code [videoId, snapId]} where {@code snapId} is the
     * ID of the snap with the maximum {@code createdAt} for that video and nickname.</p>
     */
    @Query("""
            select s.videoId, s.id
            from SnapEntity s
            where s.assinaturaId = :assinaturaId
              and lower(s.nicknameSnapshot) = lower(:nickname)
              and s.videoId in :videoIds
              and s.createdAt = (
                  select max(s2.createdAt)
                  from SnapEntity s2
                  where s2.videoId = s.videoId
                    and s2.assinaturaId = :assinaturaId
                    and lower(s2.nicknameSnapshot) = lower(:nickname)
              )
            """)
    List<Object[]> findLatestSnapIdPerVideo(
            @Param("assinaturaId") Long assinaturaId,
            @Param("nickname") String nickname,
            @Param("videoIds") java.util.Collection<UUID> videoIds
    );
}
