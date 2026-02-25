package com.snapplayerapi.api.v2.dto;

import java.util.UUID;

/**
 * Response for `POST /v2/snaps/{snapId}/share`.
 *
 * <p>The operation is intentionally idempotent in Entrega 2: if the snap was already public, the
 * same token/url are returned again.</p>
 */
public record ShareSnapResponse(
        UUID snapId,
        boolean isPublic,
        String publicShareToken,
        String publicUrl
) {
}
