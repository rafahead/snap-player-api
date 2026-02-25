package com.snapplayerapi.api.dto;

import java.util.List;

/**
 * Per-item result returned by the synchronous MVP endpoint.
 *
 * <p>The response intentionally echoes {@code subject} because the async version will later persist
 * and query results by this metadata. Keeping it in the MVP response validates the contract early.</p>
 */
public record ProcessingFilmagemResponse(
        int itemIndex,
        String status,
        String dataFilmagem,
        ProcessingSubjectRequest subject,
        String videoUrl,
        Double startSeconds,
        Long startFrame,
        Double resolvedStartSeconds,
        ProcessingVideoProbeResponse videoProbe,
        String outputDir,
        ProcessingSnapshotVideoResponse snapshotVideo,
        int frameCount,
        List<ProcessingFrameResponse> frames,
        String error
) {
}
