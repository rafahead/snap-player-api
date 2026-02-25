package com.snapplayerapi.api.v2.dto;

/**
 * Standard pagination/sorting metadata for list endpoints.
 *
 * <p>Entrega 3 introduces this object so all list/search endpoints expose the same paging contract
 * (`offset`/`limit`) and the effective ordering applied (`sortBy`/`sortDir`). The `total` remains
 * in each envelope for backward compatibility with earlier clients.</p>
 */
public record PageMetaResponse(
        int offset,
        int limit,
        int returned,
        boolean hasMore,
        String sortBy,
        String sortDir
) {
}
