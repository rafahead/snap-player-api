package com.oddplayerapi.mvp.dto;

import java.util.List;

/**
 * Per-item result returned by the synchronous MVP endpoint.
 *
 * <p>The response intentionally echoes {@code subject} because the async version will later persist
 * and query results by this metadata. Keeping it in the MVP response validates the contract early.</p>
 */
public record MvpFilmagemResponse(
        int itemIndex,
        String status,
        String dataFilmagem,
        MvpSubjectRequest subject,
        String videoUrl,
        Double startSeconds,
        Long startFrame,
        Double resolvedStartSeconds,
        MvpVideoProbeResponse videoProbe,
        String outputDir,
        MvpSnapshotVideoResponse snapshotVideo,
        int frameCount,
        List<MvpFrameResponse> frames,
        String error
) {
}
