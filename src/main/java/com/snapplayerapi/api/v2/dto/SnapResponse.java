package com.snapplayerapi.api.v2.dto;

import com.snapplayerapi.api.dto.ProcessingFrameResponse;
import com.snapplayerapi.api.dto.ProcessingSnapshotVideoResponse;
import com.snapplayerapi.api.dto.ProcessingVideoProbeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta consolidada de um snap persistido.
 *
 * <p>Na Entrega 1 mantemos, por design, vários campos "ecoando" o contrato do MVP
 * (`videoProbe`, `snapshotVideo`, `frames`) para facilitar migração da base técnica e permitir
 * inspeção/auditoria completa do processamento síncrono.</p>
 */
public record SnapResponse(
        UUID snapId,
        UUID videoId,
        String status,
        String tipoSnap,
        String nickname,
        String email,
        Long subjectTemplateId,
        String dataFilmagem,
        V2SubjectRequest subject,
        String videoUrl,
        Double startSeconds,
        Long startFrame,
        Double resolvedStartSeconds,
        Double durationSeconds,
        Double snapshotDurationSeconds,
        Integer fps,
        Integer maxWidth,
        String format,
        Integer quality,
        ProcessingVideoProbeResponse videoProbe,
        ProcessingSnapshotVideoResponse snapshotVideo,
        Integer frameCount,
        List<ProcessingFrameResponse> frames,
        String error,
        SnapJobResponse job,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {
}
