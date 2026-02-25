package com.snapplayerapi.api.dto;

public record ProcessingFrameResponse(
        int index,
        double timestampSeconds,
        String fileName,
        String path
) {
}
