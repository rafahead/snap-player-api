package com.snapplayerapi.api.service;

import com.snapplayerapi.api.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Persists generated processing artifacts (snapshot + frames) outside the temp working directory.
 *
 * <p>Entrega 5 requirement: the FFmpeg temp directory is always cleaned up after processing, so
 * artifacts must be copied/uploaded before cleanup runs. This service abstracts that persistence and
 * returns the final artifact paths/URLs used in API payloads and snap snapshots.</p>
 */
@Service
public class StorageService {

    private final StorageProperties storageProperties;
    private final Optional<S3Client> s3Client;

    public StorageService(StorageProperties storageProperties, Optional<S3Client> s3Client) {
        this.storageProperties = storageProperties;
        this.s3Client = s3Client;
    }

    /**
     * Stores one processing item's artifacts and returns the final public/persistent references.
     *
     * <p>Backend precedence:
     * 1) S3-compatible storage when enabled
     * 2) local persistent storage when enabled
     * 3) passthrough temp paths (defensive fallback; not intended for production)</p>
     *
     * <p>{@code artifactIdHint} is used to keep stable storage keys. In `v2`, callers pass
     * {@code snapId} so retries and worker reprocessing overwrite the same logical object keys.</p>
     */
    public StoredArtifacts storeProcessingArtifacts(
            String requestId,
            int itemIndex,
            String artifactIdHint,
            Path itemDir,
            Path snapshotFile,
            List<Path> frameFiles
    ) throws IOException {
        String artifactId = resolveArtifactId(requestId, itemIndex, artifactIdHint);

        String snapshotKey = key(snapshotDirPrefix(artifactId), snapshotFile.getFileName().toString());
        List<String> frameKeys = new ArrayList<>(frameFiles.size());
        for (Path frame : frameFiles) {
            frameKeys.add(key(frameDirPrefix(artifactId), frame.getFileName().toString()));
        }

        if (storageProperties.getS3().isEnabled()) {
            return storeInS3(artifactId, snapshotFile, snapshotKey, frameFiles, frameKeys);
        }
        if (storageProperties.getLocal().isEnabled()) {
            return storeLocally(artifactId, snapshotFile, snapshotKey, frameFiles, frameKeys);
        }
        // Defensive fallback used only when both backends are disabled.
        return passthrough(itemDir, snapshotFile, frameFiles);
    }

    private StoredArtifacts storeInS3(
            String artifactId,
            Path snapshotFile,
            String snapshotKey,
            List<Path> frameFiles,
            List<String> frameKeys
    ) {
        S3Client client = s3Client.orElseThrow(() -> new IllegalStateException("S3 storage enabled but no S3Client bean is available"));
        StorageProperties.S3 s3 = storageProperties.getS3();
        String bucket = s3.getBucket().trim();

        // Upload snapshot first, then frames. Ordering is not functionally required but keeps logs
        // and future retries easier to follow during manual diagnosis.
        putObject(client, bucket, snapshotKey, snapshotFile, "video/mp4");
        for (int i = 0; i < frameFiles.size(); i++) {
            Path frame = frameFiles.get(i);
            putObject(client, bucket, frameKeys.get(i), frame, contentTypeFor(frame));
        }

        List<String> frameUrls = frameKeys.stream().map(key -> joinUrl(s3.getPublicBaseUrl(), key)).toList();
        String snapshotUrl = joinUrl(s3.getPublicBaseUrl(), snapshotKey);
        String outputDir = joinUrl(s3.getPublicBaseUrl(), frameDirPrefix(artifactId));
        return new StoredArtifacts(outputDir, snapshotUrl, frameUrls);
    }

    private StoredArtifacts storeLocally(
            String artifactId,
            Path snapshotFile,
            String snapshotKey,
            List<Path> frameFiles,
            List<String> frameKeys
    ) throws IOException {
        StorageProperties.Local local = storageProperties.getLocal();
        Path basePath = Path.of(local.getBasePath());
        Files.createDirectories(basePath);

        Path storedSnapshot = copyToLocal(basePath, snapshotKey, snapshotFile);
        List<String> framePaths = new ArrayList<>(frameFiles.size());
        for (int i = 0; i < frameFiles.size(); i++) {
            Path storedFrame = copyToLocal(basePath, frameKeys.get(i), frameFiles.get(i));
            framePaths.add(resolveLocalPublicPath(local.getPublicBaseUrl(), frameKeys.get(i), storedFrame));
        }

        String snapshotPath = resolveLocalPublicPath(local.getPublicBaseUrl(), snapshotKey, storedSnapshot);
        Path localOutputDir = basePath.resolve(frameDirPrefix(artifactId));
        String outputDir = hasText(local.getPublicBaseUrl())
                ? joinUrl(local.getPublicBaseUrl(), frameDirPrefix(artifactId))
                : localOutputDir.toString();
        return new StoredArtifacts(outputDir, snapshotPath, framePaths);
    }

    /**
     * Returns temp paths unchanged when no persistent backend is enabled.
     */
    private StoredArtifacts passthrough(Path itemDir, Path snapshotFile, List<Path> frameFiles) {
        List<String> framePaths = frameFiles.stream().map(Path::toString).toList();
        return new StoredArtifacts(itemDir.toString(), snapshotFile.toString(), framePaths);
    }

    /**
     * Copies a temp artifact to the local persistent storage tree, preserving the logical key path.
     */
    private static Path copyToLocal(Path basePath, String key, Path source) throws IOException {
        Path target = basePath.resolve(key);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static void putObject(S3Client client, String bucket, String key, Path file, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromFile(file)
        );
    }

    private String frameDirPrefix(String artifactId) {
        return key(prefix(), "frames", artifactId);
    }

    private String snapshotDirPrefix(String artifactId) {
        return key(prefix(), "snapshots", artifactId);
    }

    private String prefix() {
        // Prefix applies only to S3-compatible storage. Local storage keeps a simpler tree rooted at
        // `app.storage.local.basePath`.
        String prefix = storageProperties.getS3().isEnabled()
                ? storageProperties.getS3().getPrefix()
                : null;
        if (!hasText(prefix)) {
            return "";
        }
        return trimSlashes(prefix);
    }

    private static String resolveArtifactId(String requestId, int itemIndex, String artifactIdHint) {
        // Sanitization keeps object keys filesystem/S3-friendly while still preserving caller intent
        // (e.g., UUID `snapId` passed by the v2 sync/async flows).
        String candidate = hasText(artifactIdHint) ? artifactIdHint.trim() : requestId + "-item-" + itemIndex;
        String normalized = candidate
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^[.-]+|[.-]+$", "");
        if (normalized.isBlank()) {
            return requestId + "-item-" + itemIndex;
        }
        if (normalized.length() > 120) {
            return normalized.substring(0, 120);
        }
        return normalized;
    }

    private static String key(String... parts) {
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (!hasText(part)) {
                continue;
            }
            String trimmed = trimSlashes(part);
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return String.join("/", normalized);
    }

    private static String trimSlashes(String value) {
        String trimmed = value.trim();
        int start = 0;
        int end = trimmed.length();
        while (start < end && trimmed.charAt(start) == '/') {
            start++;
        }
        while (end > start && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        return trimmed.substring(start, end);
    }

    private static String joinUrl(String baseUrl, String key) {
        if (!hasText(baseUrl)) {
            throw new IllegalStateException("publicBaseUrl is required to build public object URLs");
        }
        return baseUrl.replaceAll("/+$", "") + "/" + trimSlashes(key);
    }

    private static String resolveLocalPublicPath(String publicBaseUrl, String key, Path fallbackPath) {
        if (hasText(publicBaseUrl)) {
            return joinUrl(publicBaseUrl, key);
        }
        return fallbackPath.toString();
    }

    private static String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Final storage references returned to the processing layer after persistence.
     *
     * @param outputDir logical parent path/URL for frames (diagnostic/legacy field)
     * @param snapshotPath final snapshot path/URL
     * @param framePaths final frame paths/URLs preserving frame order
     */
    public record StoredArtifacts(
            String outputDir,
            String snapshotPath,
            List<String> framePaths
    ) {
    }
}
