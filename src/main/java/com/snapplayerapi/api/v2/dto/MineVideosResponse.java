package com.snapplayerapi.api.v2.dto;

import java.util.List;

/**
 * Response envelope for `GET /v2/videos/mine`.
 *
 * <p>Items are grouped by `videoId` and sorted by most recent snap activity in Entrega 2.</p>
 */
public record MineVideosResponse(
        String nickname,
        int total,
        List<MineVideoItemResponse> items
) {
}
