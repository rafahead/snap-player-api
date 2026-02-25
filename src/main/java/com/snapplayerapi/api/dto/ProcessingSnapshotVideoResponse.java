package com.snapplayerapi.api.dto;

public record ProcessingSnapshotVideoResponse(
        String fileName,
        String path,
        double durationSeconds
) {
}
