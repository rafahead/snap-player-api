package com.snapplayerapi.api.v2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;
import com.snapplayerapi.api.dto.ProcessingFilmagemResponse;
import com.snapplayerapi.api.dto.ProcessingOverlayRequest;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingSubjectRequest;
import com.snapplayerapi.api.v2.config.SnapProperties;
import com.snapplayerapi.api.v2.dto.V2SubjectRequest;
import com.snapplayerapi.api.v2.entity.SnapEntity;
import com.snapplayerapi.api.v2.entity.SnapProcessingJobEntity;
import com.snapplayerapi.api.v2.repo.SnapProcessingJobRepository;
import com.snapplayerapi.api.v2.repo.SnapRepository;
import com.snapplayerapi.api.v2.repo.VideoRepository;
import com.snapplayerapi.api.web.SnapJobObservabilityRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local DB-polling worker for asynchronous snap processing (Entrega 4).
 *
 * <p>The worker claims jobs using `FOR UPDATE SKIP LOCKED`, executes the existing synchronous
 * processing pipeline through {@link SnapProcessingGateway}, and writes the result back into the
 * persisted `snap` row. This keeps the public API contract stable while decoupling request latency
 * from FFmpeg execution.</p>
 */
@Service
public class SnapProcessingJobWorker {

    private static final Logger log = LoggerFactory.getLogger(SnapProcessingJobWorker.class);
    private final SnapProperties snapProperties;
    private final SnapProcessingJobRepository jobRepository;
    private final SnapRepository snapRepository;
    private final VideoRepository videoRepository;
    private final SnapProcessingGateway snapProcessingGateway;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final SnapJobObservabilityRegistry snapJobObservabilityRegistry;

    public SnapProcessingJobWorker(
            SnapProperties snapProperties,
            SnapProcessingJobRepository jobRepository,
            SnapRepository snapRepository,
            VideoRepository videoRepository,
            SnapProcessingGateway snapProcessingGateway,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            SnapJobObservabilityRegistry snapJobObservabilityRegistry
    ) {
        this.snapProperties = snapProperties;
        this.jobRepository = jobRepository;
        this.snapRepository = snapRepository;
        this.videoRepository = videoRepository;
        this.snapProcessingGateway = snapProcessingGateway;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.snapJobObservabilityRegistry = snapJobObservabilityRegistry;
    }

    /**
     * Scheduled polling loop used in local/dev runtime.
     *
     * <p>Feature flags control activation so tests and staged rollouts can keep the worker dormant.</p>
     */
    @Scheduled(fixedDelayString = "${app.snap.workerPollDelayMs:1000}")
    public void pollAndProcess() {
        if (!snapProperties.isAsyncCreateEnabled() || !snapProperties.isWorkerEnabled()) {
            return;
        }
        processPendingJobsOnce();
    }

    /**
     * Processes up to `workerBatchSize` claimable jobs.
     *
     * <p>This method is public on purpose so integration tests and internal tools can invoke one
     * deterministic processing cycle without relying on scheduler timing.</p>
     */
    public void processPendingJobsOnce() {
        // First recover abandoned RUNNING rows so they become eligible again (or fail terminally).
        recoverStaleRunningJobsOnce();

        int processed = 0;
        int batchSize = Math.max(1, snapProperties.getWorkerBatchSize());
        while (processed < batchSize) {
            Optional<ClaimedJob> claimed = claimNextJob();
            if (claimed.isEmpty()) {
                break;
            }
            processed++;
            processClaimedJob(claimed.get());
        }
    }

    /**
     * Claims one job in a short transaction to minimize lock duration.
     */
    protected Optional<ClaimedJob> claimNextJob() {
        return Optional.ofNullable(transactionTemplate.execute(status -> {
            Optional<SnapProcessingJobEntity> maybeJob = jobRepository.findNextClaimableJobForUpdate();
            if (maybeJob.isEmpty()) {
                return null;
            }

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            SnapProcessingJobEntity job = maybeJob.get();
            job.setStatus("RUNNING");
            job.setAttempts((job.getAttempts() == null ? 0 : job.getAttempts()) + 1);
            job.setLockedAt(now);
            job.setLockOwner(snapProperties.getWorkerInstanceId());
            job.setStartedAt(job.getStartedAt() == null ? now : job.getStartedAt());
            job.setUpdatedAt(now);
            jobRepository.save(job);
            snapJobObservabilityRegistry.recordClaim();

            return new ClaimedJob(job.getId(), job.getSnapId(), job.getAttempts(), job.getMaxAttempts());
        }));
    }

    /**
     * Recovers stale `RUNNING` jobs whose lock exceeded `workerLockTimeoutSeconds`.
     *
     * <p>Typical causes are process crashes or restarts after claiming a row and before persisting a
     * terminal outcome. Recovery sends the job back to `RETRY_WAIT` (or `FAILED` if max attempts
     * were exhausted), making the queue self-healing across worker restarts.</p>
     *
     * @return number of stale jobs recovered in this cycle
     */
    public int recoverStaleRunningJobsOnce() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusSeconds(Math.max(1L, snapProperties.getWorkerLockTimeoutSeconds()));
        return transactionTemplate.execute(status -> {
            int recovered = 0;
            for (SnapProcessingJobEntity job : jobRepository.findByStatusAndLockedAtBeforeOrderByLockedAtAsc("RUNNING", cutoff)) {
                recovered++;
                snapJobObservabilityRegistry.recordStaleRecovered();

                SnapEntity snap = snapRepository.findById(job.getSnapId())
                        .orElseThrow(() -> new NoSuchElementException("Snap not found while recovering stale job: " + job.getSnapId()));
                String staleError = "Recovered stale RUNNING job after lock timeout";
                boolean canRetry = (job.getAttempts() == null ? 0 : job.getAttempts()) < (job.getMaxAttempts() == null ? 1 : job.getMaxAttempts());

                if (canRetry) {
                    job.setStatus("RETRY_WAIT");
                    job.setNextRunAt(now.plusSeconds(computeRetryDelaySeconds(job.getAttempts())));
                    job.setFinishedAt(null);
                    snapJobObservabilityRegistry.recordRetryScheduled();
                } else {
                    job.setStatus("FAILED");
                    job.setFinishedAt(now);
                    snap.setStatus("FAILED");
                    snap.setProcessedAt(now);
                    long durationMs = estimateTerminalDurationMs(job, now);
                    snapJobObservabilityRegistry.recordTerminal("FAILED", durationMs);
                }

                job.setLastError(staleError);
                job.setLockedAt(null);
                job.setLockOwner(null);
                job.setUpdatedAt(now);
                jobRepository.save(job);

                snap.setErrorMessage(staleError);
                snap.setUpdatedAt(now);
                snapRepository.save(snap);

                log.warn(
                        "snap_job_stale_recovered jobId={} snapId={} canRetry={} attempts={} maxAttempts={}",
                        job.getId(),
                        job.getSnapId(),
                        canRetry,
                        job.getAttempts(),
                        job.getMaxAttempts()
                );
            }
            return recovered;
        });
    }

    /**
     * Executes one claimed job and persists success/failure outcome.
     */
    protected void processClaimedJob(ClaimedJob claimedJob) {
        long startedAtNanos = System.nanoTime();
        try {
            SnapEntity snap = snapRepository.findById(claimedJob.snapId())
                    .orElseThrow(() -> new NoSuchElementException("Snap not found for job: " + claimedJob.snapId()));
            ProcessingFilmagemRequest request = buildProcessingRequestFromSnap(snap);
            ProcessingBatchResponse batchResponse = snapProcessingGateway.processSingle(request);
            ProcessingFilmagemResponse item = batchResponse.filmagens().get(0);
            long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
            finalizeJobSuccess(claimedJob, snap, item, durationMs);
        } catch (Exception e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
            finalizeJobFailure(claimedJob, e, durationMs);
        }
    }

    /**
     * Persists successful worker execution into both `snap` and `snap_processing_job`.
     */
    protected void finalizeJobSuccess(ClaimedJob claimedJob, SnapEntity loadedSnap, ProcessingFilmagemResponse item, long durationMs) {
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            SnapEntity snap = snapRepository.findById(loadedSnap.getId())
                    .orElseThrow(() -> new NoSuchElementException("Snap not found while finalizing job: " + loadedSnap.getId()));
            SnapProcessingJobEntity job = jobRepository.findById(claimedJob.jobId())
                    .orElseThrow(() -> new NoSuchElementException("Job not found while finalizing: " + claimedJob.jobId()));

            if (item.videoProbe() != null) {
                videoRepository.findById(snap.getVideoId()).ifPresent(video -> {
                    video.setVideoProbeJson(writeJson(item.videoProbe()));
                    videoRepository.save(video);
                });
            }

            snap.setStatus(mapSnapStatus(item.status()));
            snap.setResolvedStartSeconds(item.resolvedStartSeconds());
            snap.setSnapshotDurationSeconds(resolveSnapshotDuration(snap, item.snapshotVideo()));
            snap.setVideoProbeJson(writeJsonOrNull(item.videoProbe()));
            snap.setSnapshotVideoJson(writeJsonOrNull(item.snapshotVideo()));
            snap.setFramesJson(writeJson(item.frames()));
            snap.setFrameCount(item.frameCount());
            snap.setOutputDir(item.outputDir());
            snap.setErrorMessage(item.error());
            snap.setUpdatedAt(now);
            snap.setProcessedAt(now);
            snapRepository.save(snap);

            job.setStatus("COMPLETED");
            job.setLockedAt(null);
            job.setLockOwner(null);
            job.setFinishedAt(now);
            job.setLastError(null);
            job.setUpdatedAt(now);
            jobRepository.save(job);
            snapJobObservabilityRegistry.recordTerminal("COMPLETED", durationMs);

            log.info(
                    "snap_job_completed jobId={} snapId={} status={} attempts={} durationMs={}",
                    job.getId(),
                    snap.getId(),
                    snap.getStatus(),
                    job.getAttempts(),
                    durationMs
            );
        });
    }

    /**
     * Persists failure outcome and schedules retry when attempts remain.
     */
    protected void finalizeJobFailure(ClaimedJob claimedJob, Exception error, long durationMs) {
        transactionTemplate.executeWithoutResult(status -> {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            SnapProcessingJobEntity job = jobRepository.findById(claimedJob.jobId())
                    .orElseThrow(() -> new NoSuchElementException("Job not found while handling failure: " + claimedJob.jobId()));
            SnapEntity snap = snapRepository.findById(claimedJob.snapId())
                    .orElseThrow(() -> new NoSuchElementException("Snap not found while handling failure: " + claimedJob.snapId()));

            String errorMessage = truncateError(error);
            boolean canRetry = claimedJob.attempts() < claimedJob.maxAttempts() && isRetryableFailure(error);

            if (canRetry) {
                job.setStatus("RETRY_WAIT");
                job.setNextRunAt(now.plusSeconds(computeRetryDelaySeconds(claimedJob.attempts())));
                job.setFinishedAt(null);
                snapJobObservabilityRegistry.recordRetryScheduled();
            } else {
                job.setStatus("FAILED");
                job.setFinishedAt(now);
                snap.setStatus("FAILED");
                snap.setErrorMessage(errorMessage);
                snap.setProcessedAt(now);
                snapJobObservabilityRegistry.recordTerminal("FAILED", durationMs);
            }
            job.setLastError(errorMessage);
            job.setLockedAt(null);
            job.setLockOwner(null);
            job.setUpdatedAt(now);
            jobRepository.save(job);

            snap.setUpdatedAt(now);
            if (canRetry) {
                // Even while waiting for retry we persist the latest error message for diagnostics.
                snap.setErrorMessage(errorMessage);
            }
            snapRepository.save(snap);

            log.warn(
                    "snap_job_failed jobId={} snapId={} attempts={} maxAttempts={} retryScheduled={} durationMs={} error={}",
                    job.getId(),
                    snap.getId(),
                    claimedJob.attempts(),
                    claimedJob.maxAttempts(),
                    canRetry,
                    durationMs,
                    errorMessage
            );
        });
    }

    /**
     * Rebuilds the processing request from the persisted `snap` request snapshot.
     */
    private ProcessingFilmagemRequest buildProcessingRequestFromSnap(SnapEntity snap) {
        V2SubjectRequest subject = readJson(snap.getSubjectJson(), V2SubjectRequest.class);
        ProcessingOverlayRequest overlay = readJsonOrNull(snap.getOverlayJson(), ProcessingOverlayRequest.class);
        ProcessingSubjectRequest processingSubject = new ProcessingSubjectRequest(subject.id(), subject.attributes());

        return new ProcessingFilmagemRequest(
                snap.getVideoUrl(),
                snap.getStartSeconds(),
                snap.getStartFrame(),
                snap.getDurationSeconds(),
                snap.getSnapshotDurationSeconds(),
                snap.getFps(),
                snap.getMaxWidth(),
                snap.getFormat(),
                snap.getQuality(),
                snap.getDataFilmagem(),
                processingSubject,
                overlay,
                null
        );
    }

    /**
     * Worker success path prefers the actual snapshot duration returned by processing when present.
     */
    private static double resolveSnapshotDuration(SnapEntity snap, ProcessingSnapshotVideoResponse snapshotVideo) {
        if (snapshotVideo != null) {
            return snapshotVideo.durationSeconds();
        }
        return snap.getSnapshotDurationSeconds() != null ? snap.getSnapshotDurationSeconds() : snap.getDurationSeconds();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize worker JSON payload", e);
        }
    }

    private String writeJsonOrNull(Object value) {
        return value == null ? null : writeJson(value);
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize persisted snap JSON for " + type.getSimpleName(), e);
        }
    }

    private <T> T readJsonOrNull(String json, Class<T> type) {
        return json == null ? null : readJson(json, type);
    }

    /**
     * Aligns worker result statuses with the public `v2` status vocabulary.
     */
    private static String mapSnapStatus(String processingStatus) {
        return switch (processingStatus) {
            case "SUCCEEDED", "COMPLETED" -> "COMPLETED";
            case "FAILED" -> "FAILED";
            case "PARTIAL" -> "PARTIAL";
            default -> processingStatus == null ? "FAILED" : processingStatus;
        };
    }

    /**
     * Truncates error payloads before persistence/logging to avoid unbounded rows.
     */
    private static String truncateError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ');
        int max = 4000;
        return compact.length() <= max ? compact : compact.substring(0, max);
    }

    /**
     * Basic retry classification to avoid retrying deterministic validation/data errors forever.
     */
    private static boolean isRetryableFailure(Exception error) {
        return !(error instanceof IllegalArgumentException) && !(error instanceof NoSuchElementException);
    }

    /**
     * Computes retry delay with exponential backoff and upper bound.
     *
     * <p>`attemptsCompleted` is the number of attempts already consumed by the job. Example:
     * first failure (`attemptsCompleted=1`) waits `base`, second failure waits `base*multiplier`.</p>
     */
    private long computeRetryDelaySeconds(int attemptsCompleted) {
        long base = Math.max(1L, snapProperties.getWorkerRetryDelaySeconds());
        double multiplier = Math.max(1.0d, snapProperties.getWorkerRetryBackoffMultiplier());
        long maxDelay = Math.max(base, snapProperties.getWorkerRetryMaxDelaySeconds());
        double rawDelay = base * Math.pow(multiplier, Math.max(0, attemptsCompleted - 1));
        long boundedDelay = (long) Math.ceil(rawDelay);
        return Math.max(1L, Math.min(maxDelay, boundedDelay));
    }

    /**
     * Estimates terminal duration from job timestamps for stale-recovery terminal failures.
     */
    private static long estimateTerminalDurationMs(SnapProcessingJobEntity job, OffsetDateTime now) {
        if (job.getStartedAt() == null) {
            return 0L;
        }
        OffsetDateTime finished = job.getFinishedAt() != null ? job.getFinishedAt() : now;
        long millis = finished.toInstant().toEpochMilli() - job.getStartedAt().toInstant().toEpochMilli();
        return Math.max(0L, millis);
    }

    /**
     * Compact immutable projection returned after job claim to avoid leaking managed entities across
     * transaction boundaries.
     */
    protected record ClaimedJob(Long jobId, UUID snapId, int attempts, int maxAttempts) {
    }
}
