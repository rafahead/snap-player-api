package com.snapplayerapi.api.v2.dto;

import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Public-safe representation returned by `GET /public/snaps/{token}`.
 *
 * <p>Compared to the internal `SnapResponse`, this DTO intentionally excludes private/internal
 * fields such as `email`, `outputDir` and processing error details.</p>
 */
public record PublicSnapResponse(
        UUID snapId,
        String publicShareToken,
        String status,
        String tipoSnap,
        String nickname,
        String dataFilmagem,
        V2SubjectRequest subject,
        Double resolvedStartSeconds,
        Double durationSeconds,
        Double snapshotDurationSeconds,
        Integer frameCount,
        ProcessingVideoProbeResponse videoProbe,
        ProcessingSnapshotVideoResponse snapshotVideo,
        List<ProcessingFrameResponse> frames,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {
}
