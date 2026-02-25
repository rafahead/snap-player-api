package com.snapplayerapi.api.v2.service;

import com.snapplayerapi.api.v2.config.SnapProperties;
import com.snapplayerapi.api.v2.entity.SnapProcessingJobEntity;
import com.snapplayerapi.api.v2.repo.SnapProcessingJobRepository;
import com.snapplayerapi.api.web.SnapJobObservabilityRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Maintenance tasks for the async snap job queue (Entrega 4 slice 3).
 *
 * <p>Currently responsible for bounded cleanup of terminal queue rows. The `snap` rows themselves
 * are intentionally preserved because they are part of the product/API contract; only the internal
 * queue bookkeeping rows are eligible for retention-based cleanup.</p>
 */
@Service
public class SnapProcessingJobMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(SnapProcessingJobMaintenanceService.class);
    private static final List<String> TERMINAL_JOB_STATUSES = List.of("COMPLETED", "FAILED");

    private final SnapProperties snapProperties;
    private final SnapProcessingJobRepository jobRepository;
    private final SnapJobObservabilityRegistry snapJobObservabilityRegistry;
    private final TransactionTemplate transactionTemplate;

    public SnapProcessingJobMaintenanceService(
            SnapProperties snapProperties,
            SnapProcessingJobRepository jobRepository,
            SnapJobObservabilityRegistry snapJobObservabilityRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.snapProperties = snapProperties;
        this.jobRepository = jobRepository;
        this.snapJobObservabilityRegistry = snapJobObservabilityRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Scheduled cleanup loop for old terminal job rows.
     *
     * <p>Feature-flagged so environments can disable automatic cleanup during debugging.</p>
     */
    @Scheduled(fixedDelayString = "${app.snap.jobCleanupDelayMs:60000}")
    public void cleanupTerminalJobsScheduled() {
        if (!snapProperties.isJobCleanupEnabled()) {
            return;
        }
        cleanupTerminalJobsOnce();
    }

    /**
     * Deletes one bounded batch of terminal jobs older than the configured retention.
     *
     * <p>Exposed as a public method so tests/tools can trigger deterministic cleanup without
     * waiting for the scheduler.</p>
     *
     * @return number of deleted rows in this cycle
     */
    public int cleanupTerminalJobsOnce() {
        return transactionTemplate.execute(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            OffsetDateTime cutoff = now.minusHours(Math.max(1L, snapProperties.getJobRetentionHours()));
            int requestedBatchSize = Math.max(1, snapProperties.getJobCleanupBatchSize());

            // Repository method is intentionally capped at 100 to keep SQL simple/portable.
            int effectiveBatchSize = Math.min(requestedBatchSize, 100);
            List<SnapProcessingJobEntity> candidates = jobRepository
                    .findTop100ByStatusInAndFinishedAtBeforeOrderByFinishedAtAsc(TERMINAL_JOB_STATUSES, cutoff);
            if (candidates.isEmpty()) {
                return 0;
            }

            List<SnapProcessingJobEntity> batch = candidates.size() > effectiveBatchSize
                    ? candidates.subList(0, effectiveBatchSize)
                    : candidates;

            List<Long> ids = batch.stream().map(SnapProcessingJobEntity::getId).toList();
            jobRepository.deleteAllByIdInBatch(ids);
            snapJobObservabilityRegistry.recordCleanupDeleted(ids.size());

            log.info(
                    "snap_job_cleanup_deleted count={} cutoff={} requestedBatchSize={} effectiveBatchSize={}",
                    ids.size(),
                    cutoff,
                    requestedBatchSize,
                    effectiveBatchSize
            );
            return ids.size();
        });
    }
}

