package com.oddplayerapi.mvp.dto;

public record MvpFrameResponse(
        int index,
        double timestampSeconds,
        String fileName,
        String path
) {
}
