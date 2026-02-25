package com.snapplayerapi.api.dto;

public record ProcessingVideoProbeResponse(
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
