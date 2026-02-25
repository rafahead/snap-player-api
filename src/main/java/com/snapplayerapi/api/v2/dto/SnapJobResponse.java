package com.snapplayerapi.api.v2.dto;

import java.time.OffsetDateTime;

/**
 * Optional job lifecycle payload attached to `SnapResponse` for async-processing observability.
 *
 * <p>Entrega 4 uses this object to expose queue/worker state without changing the core snap
 * contract. It is populated on endpoints where job introspection is useful (`POST /v2/snaps` and
 * `GET /v2/snaps/{id}`), while list/search endpoints may omit it to avoid unnecessary DB lookups.</p>
 */
public record SnapJobResponse(
        Long jobId,
        String status,
        Integer attempts,
        Integer maxAttempts,
        OffsetDateTime nextRunAt,
        OffsetDateTime lockedAt,
        String lockOwner,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String lastError
) {
}

