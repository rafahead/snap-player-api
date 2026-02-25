package com.oddplayerapi.mvp.service;

import com.oddplayerapi.mvp.config.MvpProperties;
import com.oddplayerapi.mvp.dto.MvpBatchResponse;
import com.oddplayerapi.mvp.dto.MvpFilmagemRequest;
import com.oddplayerapi.mvp.dto.MvpFilmagemResponse;
import com.oddplayerapi.mvp.dto.MvpFrameResponse;
import com.oddplayerapi.mvp.dto.MvpSnapshotVideoResponse;
import com.oddplayerapi.mvp.dto.MvpSubjectAttributeRequest;
import com.oddplayerapi.mvp.dto.MvpSubjectRequest;
import com.oddplayerapi.mvp.dto.MvpVideoProbeResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MvpVideoFrameService {

    private final MvpProperties properties;
    private final TempStorageService tempStorageService;
    private final FfmpegService ffmpegService;
    private final VideoProbeService videoProbeService;

    public MvpVideoFrameService(
            MvpProperties properties,
            TempStorageService tempStorageService,
            FfmpegService ffmpegService,
            VideoProbeService videoProbeService
    ) {
        this.properties = properties;
        this.tempStorageService = tempStorageService;
        this.ffmpegService = ffmpegService;
        this.videoProbeService = videoProbeService;
    }

    public MvpBatchResponse process(List<MvpFilmagemRequest> requests) {
        validateBatch(requests);

        String requestId = UUID.randomUUID().toString();
        Path requestDir;
        try {
            requestDir = tempStorageService.createRequestDir(requestId);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create request temp directory", e);
        }

        List<MvpFilmagemResponse> results = new ArrayList<>();
        int successCount = 0;

        // Process every item and keep per-item failure details, so the caller receives a consolidated result
        // even when some videos fail probe/extraction.
        for (int i = 0; i < requests.size(); i++) {
            MvpFilmagemRequest request = requests.get(i);
            Path expectedItemDir = requestDir.resolve("item-%03d".formatted(i));
            VideoProbeService.ProbeResult probe = null;
            try {
                ResolvedFilmagem resolved = resolveAndValidateItem(request);
                FfmpegService.OverlaySettings overlay = FfmpegService.resolveOverlay(request.overlay(), request.subject());
                probe = videoProbeService.probe(request.videoUrl());
                if (!probe.compatible()) {
                    throw new IncompatibleVideoException(probe.reason() != null ? probe.reason() : "Video is not compatible for extraction");
                }

                double resolvedStartSeconds = resolveStartSeconds(request, probe);
                validateRequestedRange(
                        probe,
                        resolvedStartSeconds,
                        resolved.imageDurationSeconds(),
                        resolved.snapshotDurationSeconds()
                );

                Path itemDir = tempStorageService.createItemDir(requestDir, i);
                FfmpegService.FfmpegRequest snapshotRequest = new FfmpegService.FfmpegRequest(
                        request.videoUrl(),
                        resolvedStartSeconds,
                        resolved.snapshotDurationSeconds(),
                        resolved.fps(),
                        resolved.maxWidth(),
                        resolved.format(),
                        resolved.quality(),
                        itemDir,
                        overlay
                );
                FfmpegService.FfmpegRequest framesRequest = new FfmpegService.FfmpegRequest(
                        request.videoUrl(),
                        resolvedStartSeconds,
                        resolved.imageDurationSeconds(),
                        resolved.fps(),
                        resolved.maxWidth(),
                        resolved.format(),
                        resolved.quality(),
                        itemDir,
                        overlay
                );
                ffmpegService.createSnapshotVideo(snapshotRequest);
                ffmpegService.extractFrames(framesRequest);

                List<Path> files = tempStorageService.listFrameFiles(itemDir, resolved.format());
                List<MvpFrameResponse> frames = buildFrameResponses(files, resolvedStartSeconds, resolved.fps());
                MvpSnapshotVideoResponse snapshotVideo = buildSnapshotResponse(itemDir, resolved.snapshotDurationSeconds());

                results.add(new MvpFilmagemResponse(
                        i,
                        "SUCCEEDED",
                        request.dataFilmagem().toString(),
                        request.subject(),
                        request.videoUrl(),
                        request.startSeconds(),
                        request.startFrame(),
                        resolvedStartSeconds,
                        toProbeResponse(probe),
                        itemDir.toString(),
                        snapshotVideo,
                        frames.size(),
                        frames,
                        null
                ));
                successCount++;
            } catch (Exception e) {
                VideoProbeService.ProbeResult failedProbe = probe;
                if (failedProbe != null) {
                    failedProbe = failedProbe.withReason(rootMessage(e));
                }
                results.add(failureResponse(i, request, expectedItemDir, failedProbe, rootMessage(e)));
            }
        }

        String status = overallStatus(successCount, results.size());
        return new MvpBatchResponse(
                requestId,
                status,
                Path.of(properties.getTmpBase()).toString(),
                requestDir.toString(),
                OffsetDateTime.now(ZoneOffset.UTC),
                results
        );
    }

    private void validateBatch(List<MvpFilmagemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Request body must contain at least one item");
        }
        if (requests.size() > properties.getMaxBatchItems()) {
            throw new IllegalArgumentException("Batch size exceeds app.mvp.maxBatchItems (%d)".formatted(properties.getMaxBatchItems()));
        }
    }

    private ResolvedFilmagem resolveAndValidateItem(MvpFilmagemRequest request) {
        validateSubject(request.subject());

        String format = request.format() == null ? "jpg" : request.format().toLowerCase(Locale.ROOT);
        if (!format.equals("jpg") && !format.equals("png")) {
            throw new IllegalArgumentException("format must be jpg or png");
        }

        int fps = request.fps() == null ? 5 : request.fps();
        if (fps < 1 || fps > properties.getMaxFps()) {
            throw new IllegalArgumentException("fps must be between 1 and %d".formatted(properties.getMaxFps()));
        }

        int maxWidth = request.maxWidth() == null ? 1280 : request.maxWidth();
        if (maxWidth < 320 || maxWidth > properties.getMaxWidth()) {
            throw new IllegalArgumentException("maxWidth must be between 320 and %d".formatted(properties.getMaxWidth()));
        }

        double imageDurationSeconds = request.durationSeconds();
        if (imageDurationSeconds <= 0 || imageDurationSeconds > properties.getMaxDurationSeconds()) {
            throw new IllegalArgumentException("durationSeconds must be > 0 and <= %s".formatted(properties.getMaxDurationSeconds()));
        }
        double snapshotDurationSeconds = request.snapshotDurationSeconds() == null
                ? imageDurationSeconds
                : request.snapshotDurationSeconds();
        if (snapshotDurationSeconds <= 0 || snapshotDurationSeconds > properties.getMaxDurationSeconds()) {
            throw new IllegalArgumentException(
                    "snapshotDurationSeconds must be > 0 and <= %s".formatted(properties.getMaxDurationSeconds())
            );
        }

        int quality = request.quality() == null ? 3 : request.quality();
        if (format.equals("jpg")) {
            if (quality < 2 || quality > 10) {
                throw new IllegalArgumentException("quality must be between 2 and 10 for jpg");
            }
        } else if (request.quality() != null) {
            throw new IllegalArgumentException("quality is only supported for jpg");
        }

        if (request.startSeconds() == null && request.startFrame() == null) {
            throw new IllegalArgumentException("Provide at least one of startSeconds or startFrame");
        }

        validateVideoUrl(request.videoUrl());
        return new ResolvedFilmagem(fps, maxWidth, format, quality, imageDurationSeconds, snapshotDurationSeconds);
    }

    /**
     * Validates the generic {@code subject} payload used by the extractor.
     *
     * <p>The master plan expects future queries by subject attributes (including numeric range),
     * so the MVP already enforces typed attributes and duplicate-key protection. This keeps the
     * contract stable before adding PostgreSQL persistence.</p>
     */
    private void validateSubject(MvpSubjectRequest subject) {
        if (subject == null) {
            throw new IllegalArgumentException("subject must be provided");
        }
        if (subject.id() == null || subject.id().isBlank()) {
            throw new IllegalArgumentException("subject.id must be provided");
        }

        List<MvpSubjectAttributeRequest> attributes = subject.attributes();
        if (attributes == null) {
            return;
        }
        if (attributes.size() > properties.getMaxSubjectAttributes()) {
            throw new IllegalArgumentException(
                    "subject.attributes size exceeds app.mvp.maxSubjectAttributes (%d)"
                            .formatted(properties.getMaxSubjectAttributes())
            );
        }

        Set<String> seenKeys = new HashSet<>();
        for (int index = 0; index < attributes.size(); index++) {
            MvpSubjectAttributeRequest attribute = attributes.get(index);
            if (attribute == null) {
                throw new IllegalArgumentException("subject.attributes[%d] must not be null".formatted(index));
            }
            if (attribute.key() == null || attribute.key().isBlank()) {
                throw new IllegalArgumentException("subject.attributes[%d].key must be provided".formatted(index));
            }
            String normalizedKey = attribute.key().trim().toLowerCase(Locale.ROOT);
            if (!seenKeys.add(normalizedKey)) {
                throw new IllegalArgumentException("subject.attributes contains duplicate key '%s'".formatted(attribute.key()));
            }
            if (attribute.type() == null || attribute.type().isBlank()) {
                throw new IllegalArgumentException("subject.attributes[%d].type must be provided".formatted(index));
            }

            String type = attribute.type().trim().toLowerCase(Locale.ROOT);
            switch (type) {
                case "string" -> validateStringAttribute(attribute, index);
                case "number" -> validateNumberAttribute(attribute, index);
                default -> throw new IllegalArgumentException(
                        "subject.attributes[%d].type must be 'string' or 'number'".formatted(index)
                );
            }
        }
    }

    private static void validateStringAttribute(MvpSubjectAttributeRequest attribute, int index) {
        if (attribute.stringValue() == null) {
            throw new IllegalArgumentException("subject.attributes[%d].stringValue is required for type=string".formatted(index));
        }
        if (attribute.numberValue() != null) {
            throw new IllegalArgumentException("subject.attributes[%d].numberValue must be null for type=string".formatted(index));
        }
    }

    private static void validateNumberAttribute(MvpSubjectAttributeRequest attribute, int index) {
        if (attribute.numberValue() == null) {
            throw new IllegalArgumentException("subject.attributes[%d].numberValue is required for type=number".formatted(index));
        }
        if (attribute.stringValue() != null) {
            throw new IllegalArgumentException("subject.attributes[%d].stringValue must be null for type=number".formatted(index));
        }
        if (!Double.isFinite(attribute.numberValue())) {
            throw new IllegalArgumentException("subject.attributes[%d].numberValue must be finite".formatted(index));
        }
    }

    private static double resolveStartSeconds(MvpFilmagemRequest request, VideoProbeService.ProbeResult probe) {
        if (request.startFrame() != null) {
            Double sourceFps = probe.sourceFps();
            if (sourceFps == null || sourceFps <= 0) {
                throw new IncompatibleVideoException("Could not determine source FPS to use startFrame");
            }
            return request.startFrame() / sourceFps;
        }
        if (request.startSeconds() != null) {
            return request.startSeconds();
        }
        throw new IllegalArgumentException("Provide at least one of startSeconds or startFrame");
    }

    private static void validateRequestedRange(
            VideoProbeService.ProbeResult probe,
            double resolvedStartSeconds,
            double imageDurationSeconds,
            double snapshotDurationSeconds
    ) {
        if (resolvedStartSeconds < 0) {
            throw new IllegalArgumentException("resolvedStartSeconds must be >= 0");
        }
        if (probe.durationSeconds() != null) {
            double maxRequestedDuration = Math.max(imageDurationSeconds, snapshotDurationSeconds);
            if (resolvedStartSeconds >= probe.durationSeconds()) {
                throw new IncompatibleVideoException("Requested start is beyond video duration");
            }
            if (resolvedStartSeconds + maxRequestedDuration > probe.durationSeconds() + 0.25) {
                throw new IncompatibleVideoException("Requested clip exceeds video duration");
            }
        }
    }

    private static void validateVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("videoUrl must be provided");
        }
        String lower = videoUrl.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            throw new IllegalArgumentException("videoUrl must use http or https");
        }
    }

    private static List<MvpFrameResponse> buildFrameResponses(List<Path> files, double startSeconds, int fps) {
        List<MvpFrameResponse> frames = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            int frameIndex = i + 1;
            double timestamp = startSeconds + ((double) i / fps);
            frames.add(new MvpFrameResponse(
                    frameIndex,
                    timestamp,
                    file.getFileName().toString(),
                    file.toString()
            ));
        }
        return frames;
    }

    private static MvpSnapshotVideoResponse buildSnapshotResponse(Path itemDir, double durationSeconds) throws IOException {
        Path snapshot = itemDir.resolve("snapshot.mp4");
        if (!Files.exists(snapshot)) {
            throw new IllegalStateException("snapshot.mp4 was not generated");
        }
        return new MvpSnapshotVideoResponse(snapshot.getFileName().toString(), snapshot.toString(), durationSeconds);
    }

    private static MvpVideoProbeResponse toProbeResponse(VideoProbeService.ProbeResult probe) {
        if (probe == null) {
            return null;
        }
        return new MvpVideoProbeResponse(
                probe.compatible(),
                probe.containerFormat(),
                probe.codecName(),
                probe.width(),
                probe.height(),
                probe.durationSeconds(),
                probe.sourceFps(),
                probe.pixelFormat(),
                probe.reason()
        );
    }

    private static String overallStatus(int successCount, int totalCount) {
        if (successCount == totalCount) {
            return "COMPLETED";
        }
        if (successCount == 0) {
            return "FAILED";
        }
        return "PARTIAL";
    }

    private static MvpFilmagemResponse failureResponse(
            int itemIndex,
            MvpFilmagemRequest request,
            Path outputDir,
            VideoProbeService.ProbeResult probe,
            String error
    ) {
        return new MvpFilmagemResponse(
                itemIndex,
                "FAILED",
                request.dataFilmagem() != null ? request.dataFilmagem().toString() : null,
                request.subject(),
                request.videoUrl(),
                request.startSeconds(),
                request.startFrame(),
                null,
                toProbeResponse(probe),
                outputDir.toString(),
                null,
                0,
                List.of(),
                error
        );
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ResolvedFilmagem(
            int fps,
            int maxWidth,
            String format,
            int quality,
            double imageDurationSeconds,
            double snapshotDurationSeconds
    ) {
    }

    private static final class IncompatibleVideoException extends RuntimeException {
        private IncompatibleVideoException(String message) {
            super(message);
        }
    }
}
