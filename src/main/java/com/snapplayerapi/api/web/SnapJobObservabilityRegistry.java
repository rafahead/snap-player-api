package com.snapplayerapi.api.web;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/**
 * In-memory telemetry registry for asynchronous snap-processing jobs (Entrega 4).
 *
 * <p>This registry complements the HTTP observability snapshot by tracking worker-specific events
 * and timings: claims, retries, stale recoveries, terminal outcomes and processing durations.</p>
 */
@Component
public class SnapJobObservabilityRegistry {

    private final LongAdder claimedCount = new LongAdder();
    private final LongAdder retryScheduledCount = new LongAdder();
    private final LongAdder staleRecoveredCount = new LongAdder();
    private final LongAdder cleanupDeletedCount = new LongAdder();
    private final LongAdder completedCount = new LongAdder();
    private final LongAdder failedCount = new LongAdder();
    private final LongAdder totalDurationMs = new LongAdder();
    private final AtomicLong maxDurationMs = new AtomicLong();
    private final ConcurrentMap<String, LongAdder> terminalStatusCounts = new ConcurrentHashMap<>();

    public SnapJobObservabilityRegistry(MeterRegistry meterRegistry) {
        Gauge.builder("snap.jobs.claimed", this, SnapJobObservabilityRegistry::claimedCountValue)
                .description("Total async snap-processing jobs claimed by workers since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.retry.scheduled", this, SnapJobObservabilityRegistry::retryScheduledCountValue)
                .description("Total async job retry schedules since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.stale.recovered", this, SnapJobObservabilityRegistry::staleRecoveredCountValue)
                .description("Total stale RUNNING jobs recovered since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.cleanup.deleted", this, SnapJobObservabilityRegistry::cleanupDeletedCountValue)
                .description("Total terminal job rows deleted by cleanup since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.processed", this, SnapJobObservabilityRegistry::processedCountValue)
                .description("Total terminal async jobs observed since startup (COMPLETED + FAILED)")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.completed", this, SnapJobObservabilityRegistry::completedCountValue)
                .description("Total completed async jobs observed since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.failed", this, SnapJobObservabilityRegistry::failedCountValue)
                .description("Total failed async jobs observed since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.terminal.avg.duration.ms", this, SnapJobObservabilityRegistry::avgTerminalDurationMsValue)
                .description("Average terminal async job duration in milliseconds since startup")
                .register(meterRegistry);
        Gauge.builder("snap.jobs.terminal.max.duration.ms", this, SnapJobObservabilityRegistry::maxTerminalDurationMsValue)
                .description("Maximum terminal async job duration in milliseconds since startup")
                .register(meterRegistry);
    }

    /**
     * Records that a worker successfully claimed a job row.
     */
    public void recordClaim() {
        claimedCount.increment();
    }

    /**
     * Records a retry scheduling event after a worker failure.
     */
    public void recordRetryScheduled() {
        retryScheduledCount.increment();
    }

    /**
     * Records a stale `RUNNING` job recovered back to a runnable/terminal state.
     */
    public void recordStaleRecovered() {
        staleRecoveredCount.increment();
    }

    /**
     * Records cleanup of old terminal jobs.
     */
    public void recordCleanupDeleted(long count) {
        if (count > 0) {
            cleanupDeletedCount.add(count);
        }
    }

    /**
     * Records terminal outcome and processing duration.
     *
     * <p>`terminalStatus` is expected to be `COMPLETED` or `FAILED`, but the registry remains
     * generic and stores any status key defensively.</p>
     */
    public void recordTerminal(String terminalStatus, long durationMs) {
        String key = (terminalStatus == null || terminalStatus.isBlank()) ? "UNKNOWN" : terminalStatus;
        terminalStatusCounts.computeIfAbsent(key, ignored -> new LongAdder()).increment();

        if ("COMPLETED".equals(key)) {
            completedCount.increment();
        } else if ("FAILED".equals(key)) {
            failedCount.increment();
        }

        long safeDuration = Math.max(0L, durationMs);
        totalDurationMs.add(safeDuration);
        maxDurationMs.accumulateAndGet(safeDuration, Math::max);
    }

    /**
     * Returns immutable telemetry snapshot for the internal observability endpoint.
     */
    public SnapJobObservabilitySnapshotResponse snapshot() {
        Map<String, Long> terminalByStatus = new TreeMap<>();
        for (Map.Entry<String, LongAdder> entry : terminalStatusCounts.entrySet()) {
            terminalByStatus.put(entry.getKey(), entry.getValue().sum());
        }

        long terminalTotal = terminalByStatus.values().stream().mapToLong(Long::longValue).sum();
        double avgDuration = terminalTotal == 0 ? 0.0 : (double) totalDurationMs.sum() / (double) terminalTotal;

        return new SnapJobObservabilitySnapshotResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                claimedCount.sum(),
                retryScheduledCount.sum(),
                staleRecoveredCount.sum(),
                cleanupDeletedCount.sum(),
                completedCount.sum(),
                failedCount.sum(),
                avgDuration,
                maxDurationMs.get(),
                terminalByStatus
        );
    }

    long claimedCountValue() {
        return claimedCount.sum();
    }

    long retryScheduledCountValue() {
        return retryScheduledCount.sum();
    }

    long staleRecoveredCountValue() {
        return staleRecoveredCount.sum();
    }

    long cleanupDeletedCountValue() {
        return cleanupDeletedCount.sum();
    }

    long completedCountValue() {
        return completedCount.sum();
    }

    long failedCountValue() {
        return failedCount.sum();
    }

    long processedCountValue() {
        return completedCountValue() + failedCountValue();
    }

    double avgTerminalDurationMsValue() {
        long terminalTotal = terminalTotalCountValue();
        if (terminalTotal == 0L) {
            return 0.0;
        }
        return (double) totalDurationMs.sum() / (double) terminalTotal;
    }

    long maxTerminalDurationMsValue() {
        return maxDurationMs.get();
    }

    private long terminalTotalCountValue() {
        return terminalStatusCounts.values().stream().mapToLong(counter -> counter.sum()).sum();
    }
}
