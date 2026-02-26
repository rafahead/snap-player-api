package com.snapplayerapi.api.v2.repo;

import com.snapplayerapi.api.v2.entity.SnapProcessingJobEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the asynchronous snap processing queue (Entrega 4).
 */
public interface SnapProcessingJobRepository extends JpaRepository<SnapProcessingJobEntity, Long> {

    /**
     * Job lookup by snap id (useful for diagnostics and idempotent enqueue protections).
     */
    Optional<SnapProcessingJobEntity> findBySnapId(UUID snapId);

    /**
     * Finds `RUNNING` jobs whose lock is older than the configured timeout and likely abandoned.
     */
    List<SnapProcessingJobEntity> findByStatusAndLockedAtBeforeOrderByLockedAtAsc(String status, OffsetDateTime lockedBefore);

    /**
     * Reads old terminal jobs for bounded cleanup batches (slice 3 maintenance).
     */
    List<SnapProcessingJobEntity> findTop100ByStatusInAndFinishedAtBeforeOrderByFinishedAtAsc(
            Collection<String> statuses,
            OffsetDateTime finishedBefore
    );

    /**
     * Claims one runnable job using DB row locking (`FOR UPDATE SKIP LOCKED`).
     *
     * <p>This query is intentionally native because JPQL does not provide a portable way to express
     * `SKIP LOCKED`. The service wraps this call in a short transaction and immediately updates the
     * row to `RUNNING`, minimizing lock duration.</p>
     */
    @Query(value = """
            select *
            from snap_processing_job j
            where j.status in ('PENDING', 'RETRY_WAIT')
              and j.next_run_at <= current_timestamp
            order by j.next_run_at asc, j.created_at asc
            limit 1
            for update skip locked
            """, nativeQuery = true)
    Optional<SnapProcessingJobEntity> findNextClaimableJobForUpdate();

    /**
     * Aggregates counts by status for the internal observability endpoint.
     */
    @Query("""
            select j.status, count(j)
            from SnapProcessingJobEntity j
            group by j.status
            """)
    List<Object[]> countByStatus();

    /**
     * Aggregates terminal jobs count and average completion duration in milliseconds.
     *
     * <p>Duration is estimated as `finishedAt - startedAt` and only includes rows where both
     * timestamps are present (completed or failed after actual execution started).
     * Uses ANSI SQL interval arithmetic compatible with PostgreSQL and H2 in Postgres mode.</p>
     */
    @Query(value = """
            select
              count(*) as jobs_count,
              coalesce(avg(extract(epoch from (finished_at - started_at)) * 1000), 0)
            from snap_processing_job
            where status in ('COMPLETED', 'FAILED')
              and started_at is not null
              and finished_at is not null
            """, nativeQuery = true)
    Object[] terminalDurationSummary();
}
