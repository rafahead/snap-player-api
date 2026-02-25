package com.oddplayerapi.mvp.dto;

public record MvpSnapshotVideoResponse(
        String fileName,
        String path,
        double durationSeconds
) {
}
