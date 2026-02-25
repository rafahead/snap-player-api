package com.snapplayerapi.api.v2.dto;

import java.util.List;

/**
 * Response envelope for `GET /v2/snaps/mine`.
 *
 * <p>Entrega 2 uses `nickname` as the caller identity placeholder (before auth/token support).
 * The envelope mirrors other list responses to keep the contract stable for future pagination.</p>
 */
public record MineSnapsResponse(
        String nickname,
        int total,
        PageMetaResponse page,
        List<SnapResponse> items
) {
}
