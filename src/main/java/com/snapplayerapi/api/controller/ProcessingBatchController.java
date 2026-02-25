package com.snapplayerapi.api.controller;

import com.snapplayerapi.api.dto.ProcessingBatchResponse;
import com.snapplayerapi.api.dto.ProcessingFilmagemRequest;
import com.snapplayerapi.api.service.ProcessingVideoFrameService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/v1/video-frames", "/v1/video-frames/mvp"})
@Validated
public class ProcessingBatchController {

    private final ProcessingVideoFrameService processingVideoFrameService;

    public ProcessingBatchController(ProcessingVideoFrameService processingVideoFrameService) {
        this.processingVideoFrameService = processingVideoFrameService;
    }

    /**
     * Legacy synchronous processing endpoint kept for compatibility with the former v1 contract.
     *
     * <p>The controller now also answers under `/v1/video-frames/process` (without `/mvp`) so the
     * current branch can move away from the old naming while preserving older clients.</p>
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessingBatchResponse> process(
            @RequestBody @NotEmpty List<@Valid ProcessingFilmagemRequest> requests) {
        return ResponseEntity.ok(processingVideoFrameService.process(requests));
    }
}
