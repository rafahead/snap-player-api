package com.oddplayerapi.mvp.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MvpBatchResponse(
        String requestId,
        String status,
        String tmpBaseDir,
        String requestDir,
        OffsetDateTime processedAt,
        List<MvpFilmagemResponse> filmagens
) {
}
