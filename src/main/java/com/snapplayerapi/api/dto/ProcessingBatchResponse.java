package com.snapplayerapi.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProcessingBatchResponse(
        String requestId,
        String status,
        String tmpBaseDir,
        String requestDir,
        OffsetDateTime processedAt,
        List<ProcessingFilmagemResponse> filmagens
) {
}
