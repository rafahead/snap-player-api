package com.snapplayerapi.api.v2.service;

import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;

/**
 * Indirection layer between the `v2` Snap service and the actual video processing engine.
 *
 * <p>This exists mainly to make integration tests deterministic without mocking low-level FFmpeg
 * components and to ease the future migration from synchronous MVP processing to async workers.</p>
 */
public interface SnapProcessingGateway {
    /**
     * Processes a single filmagem request and returns the same shape produced by the MVP batch API.
     */
    ProcessingBatchResponse processSingle(ProcessingFilmagemRequest request);
}
