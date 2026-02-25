package com.oddplayerapi.mvp.controller;

import com.oddplayerapi.mvp.dto.MvpBatchResponse;
import com.oddplayerapi.mvp.dto.MvpFilmagemRequest;
import com.oddplayerapi.mvp.service.MvpVideoFrameService;
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
@RequestMapping("/v1/video-frames/mvp")
@Validated
public class MvpBatchController {

    private final MvpVideoFrameService mvpVideoFrameService;

    public MvpBatchController(MvpVideoFrameService mvpVideoFrameService) {
        this.mvpVideoFrameService = mvpVideoFrameService;
    }

    @PostMapping("/process")
    public ResponseEntity<MvpBatchResponse> process(
            @RequestBody @NotEmpty List<@Valid MvpFilmagemRequest> requests) {
        return ResponseEntity.ok(mvpVideoFrameService.process(requests));
    }
}
