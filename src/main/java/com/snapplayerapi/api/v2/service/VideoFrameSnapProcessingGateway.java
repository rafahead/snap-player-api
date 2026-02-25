package com.snapplayerapi.api.v2.service;

import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;
import com.snapplayerapi.api.service.ProcessingVideoFrameService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Production adapter that reuses the synchronous frame-processing pipeline for `v2` snaps.
 */
@Service
public class VideoFrameSnapProcessingGateway implements SnapProcessingGateway {

    private final ProcessingVideoFrameService processingVideoFrameService;

    public VideoFrameSnapProcessingGateway(ProcessingVideoFrameService processingVideoFrameService) {
        this.processingVideoFrameService = processingVideoFrameService;
    }

    @Override
    public ProcessingBatchResponse processSingle(ProcessingFilmagemRequest request) {
        // The shared processing service expects a batch; v2 creates one snap per request.
        return processingVideoFrameService.process(List.of(request));
    }
}
