package com.oddplayerapi.mvp.dto;

public record MvpVideoProbeResponse(
        boolean compatible,
        String containerFormat,
        String codecName,
        Integer width,
        Integer height,
        Double durationSeconds,
        Double sourceFps,
        String pixelFormat,
        String reason
) {
}
