package com.snapplayerapi.api.v2.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response envelope for listing snaps by video.
 *
 * <p>The `total` field is included in Entrega 1 even without pagination so clients can adopt a
 * stable shape before pagination is introduced in later deliveries.</p>
 */
public record VideoSnapsResponse(
        UUID videoId,
        int total,
        PageMetaResponse page,
        List<SnapResponse> items
) {
}
