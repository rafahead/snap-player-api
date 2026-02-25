package com.snapplayerapi.api.web;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Internal snapshot response for worker/job telemetry.
 */
public record SnapJobObservabilitySnapshotResponse(
        OffsetDateTime generatedAt,
        long claimedCount,
        long retryScheduledCount,
        long staleRecoveredCount,
        long cleanupDeletedCount,
        long completedCount,
        long failedCount,
        double avgTerminalDurationMs,
        long maxTerminalDurationMs,
        Map<String, Long> terminalByStatus
) {
}

