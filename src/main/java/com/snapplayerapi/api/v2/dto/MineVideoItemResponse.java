package com.snapplayerapi.api.v2.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Aggregated video activity row for `GET /v2/videos/mine`.
 */
public record MineVideoItemResponse(
        UUID videoId,
        String videoUrl,
        int snapCount,
        UUID latestSnapId,
        OffsetDateTime latestSnapCreatedAt
) {
}
