package com.oddplayerapi.mvp.service;

import com.oddplayerapi.mvp.config.MvpProperties;
import com.oddplayerapi.mvp.dto.MvpOverlayRequest;
import com.oddplayerapi.mvp.dto.MvpSubjectAttributeRequest;
import com.oddplayerapi.mvp.dto.MvpSubjectRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class FfmpegService {

    private static final int STDERR_LIMIT_BYTES = 8192;
    private static final Pattern SAFE_COLOR_PATTERN = Pattern.compile("[A-Za-z0-9@._#-]{1,64}");
    private static final long DRAW_TEXT_CHECK_TIMEOUT_SECONDS = 10;
    private static final int OVERLAY_DEFAULT_FONT_SIZE = 18;
    private static final int OVERLAY_CARD_VERTICAL_GAP = 6;
    private static final int OVERLAY_MAX_CARD_VALUE_CHARS = 48;

    private final MvpProperties properties;
    private volatile Boolean drawtextAvailable;

    public FfmpegService(MvpProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates image frames for the requested clip.
     *
     * <p>If an overlay is requested, the method validates drawtext availability once and caches the result
     * to avoid paying the discovery cost for every item.</p>
     */
    public void extractFrames(FfmpegRequest request) {
        if (request.overlay() != null) {
            ensureDrawtextAvailable();
        }
        runCommand(buildCommand(request));
    }

    public void createSnapshotVideo(FfmpegRequest request) {
        if (request.overlay() != null) {
            ensureDrawtextAvailable();
        }
        runCommand(buildSnapshotCommand(request));
    }

    List<String> buildCommand(FfmpegRequest request) {
        List<String> args = baseInputArgs(request);
        args.add("-vf");
        args.add(buildFramesFilter(request));
        if ("jpg".equals(request.format())) {
            args.add("-q:v");
            args.add(Integer.toString(request.quality()));
        }
        args.add(request.outputDir().resolve("frame_%05d." + request.format()).toString());
        return List.copyOf(args);
    }

    private String buildFramesFilter(FfmpegRequest request) {
        String base = "fps=%d,scale=%d:-2".formatted(request.fps(), request.maxWidth());
        if (request.overlay() == null) {
            return base;
        }
        return base + "," + buildDrawtextFilter(request.overlay());
    }

    private String buildSnapshotFilter(FfmpegRequest request) {
        String base = "scale='min(%d,iw)':-2".formatted(request.maxWidth());
        if (request.overlay() == null) {
            return base;
        }
        return base + "," + buildDrawtextFilter(request.overlay());
    }

    private String buildDrawtextFilter(OverlaySettings overlay) {
        List<String> filters = new ArrayList<>();
        List<String> cards = overlay.cardTexts();
        for (int i = 0; i < cards.size(); i++) {
            filters.add(buildDrawtextCardFilter(overlay, cards.get(i), i));
        }
        return String.join(",", filters);
    }

    private String buildDrawtextCardFilter(OverlaySettings overlay, String textExpr, int cardIndex) {
        int cardStride = overlay.fontSize() + (overlay.padding() * 2) + OVERLAY_CARD_VERTICAL_GAP;

        // drawtext parser is sensitive to ':' and quotes, so escape all user-provided values conservatively.
        return "drawtext="
                + "fontfile=" + escapeFilterValue(properties.getFfmpeg().getFontFile())
                + ":text='" + textExpr + "'"
                + ":fontsize=" + overlay.fontSize()
                + ":fontcolor=" + escapeFilterValue(overlay.fontColor())
                + ":box=1"
                + ":boxcolor=" + escapeFilterValue(overlay.boxColor())
                + ":boxborderw=" + overlay.padding()
                + ":x=w-tw-" + overlay.margin()
                + ":y=" + (overlay.margin() + (cardIndex * cardStride));
    }

    List<String> buildSnapshotCommand(FfmpegRequest request) {
        List<String> args = baseInputArgs(request);
        args.add("-vf");
        args.add(buildSnapshotFilter(request));
        args.add("-c:v");
        args.add("libx264");
        args.add("-preset");
        args.add("veryfast");
        args.add("-crf");
        args.add("23");
        args.add("-an");
        args.add(request.outputDir().resolve("snapshot.mp4").toString());
        return List.copyOf(args);
    }

    private List<String> baseInputArgs(FfmpegRequest request) {
        List<String> args = new ArrayList<>();
        args.add(properties.getFfmpeg().getPath());
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("error");
        args.add("-ss");
        args.add(doubleArg(request.startSeconds()));
        args.add("-t");
        args.add(doubleArg(request.durationSeconds()));
        args.add("-i");
        args.add(request.videoUrl());
        return args;
    }

    private void runCommand(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start ffmpeg process", e);
        }

        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        Thread readerThread = new Thread(() -> drainStream(process.getInputStream(), capturedOutput), "ffmpeg-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished;
        try {
            finished = process.waitFor(properties.getFfmpeg().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(readerThread);
                throw new IllegalStateException("ffmpeg timed out after %d seconds".formatted(properties.getFfmpeg().getTimeoutSeconds()));
            }
            joinQuietly(readerThread);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Interrupted while waiting for ffmpeg", e);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String output = capturedOutput.toString(StandardCharsets.UTF_8);
            throw new IllegalStateException("ffmpeg failed with exit code %d: %s".formatted(exitCode, output));
        }
    }

    private void ensureDrawtextAvailable() {
        Boolean cached = drawtextAvailable;
        if (cached != null) {
            if (!cached) {
                throw new IllegalStateException("ffmpeg drawtext filter is not available in this environment");
            }
            return;
        }

        synchronized (this) {
            if (drawtextAvailable != null) {
                if (!drawtextAvailable) {
                    throw new IllegalStateException("ffmpeg drawtext filter is not available in this environment");
                }
                return;
            }
            boolean available = checkDrawtextAvailable();
            drawtextAvailable = available;
            if (!available) {
                throw new IllegalStateException("ffmpeg drawtext filter is not available in this environment");
            }
        }
    }

    private boolean checkDrawtextAvailable() {
        List<String> command = List.of(properties.getFfmpeg().getPath(), "-hide_banner", "-filters");
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            // `ffmpeg -filters` output can exceed the normal capped stderr capture. Do not truncate here,
            // otherwise `drawtext` may be missed and produce a false negative.
            Thread readerThread = new Thread(() -> drainStreamUnbounded(process.getInputStream(), output), "ffmpeg-filters-reader");
            readerThread.setDaemon(true);
            readerThread.start();
            boolean finished = process.waitFor(DRAW_TEXT_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(readerThread);
                return false;
            }
            joinQuietly(readerThread);
            if (process.exitValue() != 0) {
                return false;
            }
            String filters = output.toString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return filters.contains("drawtext");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Resolves the overlay configuration and materializes the final drawtext body from the {@code subject}.
     *
     * <p>For the MVP we optimize for maximum information density and predictability:
     * we always render {@code subject.id} and all attributes (in request order), and optionally append
     * frame/timestamp lines depending on the selected mode.</p>
     */
    static OverlaySettings resolveOverlay(MvpOverlayRequest overlayRequest, MvpSubjectRequest subject) {
        if (overlayRequest == null || !Boolean.TRUE.equals(overlayRequest.enabled())) {
            return null;
        }
        Objects.requireNonNull(subject, "subject is required to build overlay text");

        String position = normalizeEnumValue(overlayRequest.position(), "TOP_RIGHT");
        if (!"TOP_RIGHT".equals(position)) {
            throw new IllegalArgumentException("overlay.position must be TOP_RIGHT in MVP");
        }

        String mode = normalizeOverlayMode(overlayRequest.mode());
        if (!isSupportedOverlayMode(mode)) {
            throw new IllegalArgumentException(
                    "overlay.mode must be SUBJECT, SUBJECT_AND_FRAME, SUBJECT_AND_TIMESTAMP, SUBJECT_AND_BOTH "
                            + "(legacy aliases: FRAME_NUMBER, TIMESTAMP, BOTH)"
            );
        }

        int fontSize = intOrDefault(overlayRequest.fontSize(), OVERLAY_DEFAULT_FONT_SIZE);
        if (fontSize < 8 || fontSize > 200) {
            throw new IllegalArgumentException("overlay.fontSize must be between 8 and 200");
        }

        int margin = intOrDefault(overlayRequest.margin(), 20);
        if (margin < 0 || margin > 500) {
            throw new IllegalArgumentException("overlay.margin must be between 0 and 500");
        }

        int padding = intOrDefault(overlayRequest.padding(), 10);
        if (padding < 0 || padding > 200) {
            throw new IllegalArgumentException("overlay.padding must be between 0 and 200");
        }

        String boxColor = sanitizeColorOrDefault(overlayRequest.boxColor(), "black@0.7", "overlay.boxColor");
        String fontColor = sanitizeColorOrDefault(overlayRequest.fontColor(), "white", "overlay.fontColor");
        List<String> cardTexts = buildOverlayCardTexts(mode, subject);

        return new OverlaySettings(mode, position, fontSize, boxColor, fontColor, margin, padding, cardTexts);
    }

    private static boolean isSupportedOverlayMode(String mode) {
        return mode.equals("SUBJECT")
                || mode.equals("SUBJECT_AND_FRAME")
                || mode.equals("SUBJECT_AND_TIMESTAMP")
                || mode.equals("SUBJECT_AND_BOTH");
    }

    private static String normalizeOverlayMode(String rawMode) {
        String mode = normalizeEnumValue(rawMode, "SUBJECT");
        return switch (mode) {
            case "FRAME_NUMBER" -> "SUBJECT_AND_FRAME";
            case "TIMESTAMP" -> "SUBJECT_AND_TIMESTAMP";
            case "BOTH" -> "SUBJECT_AND_BOTH";
            default -> mode;
        };
    }

    /**
     * Builds one compact drawtext "card" per attribute value.
     *
     * <p>MVP rule requested by the user: show only attribute values (no labels), one card per attribute,
     * stacked vertically from the top-right corner. In this MVP configuration we intentionally ignore
     * frame/timestamp lines even if the caller sends modes that request them.</p>
     */
    private static List<String> buildOverlayCardTexts(String mode, MvpSubjectRequest subject) {
        List<String> cards = new ArrayList<>();

        if (subject.attributes() != null) {
            for (MvpSubjectAttributeRequest attribute : subject.attributes()) {
                if (attribute == null) {
                    continue;
                }
                String value = attribute.numberValue() != null
                        ? Double.toString(attribute.numberValue())
                        : safeDisplay(attribute.stringValue());
                String compact = truncateOverlayValue(value == null ? "" : value.strip(), OVERLAY_MAX_CARD_VALUE_CHARS);
                if (!compact.isBlank()) {
                    cards.add(escapeText(compact));
                }
            }
        }

        // Fallback keeps the overlay visible when no attributes are present.
        if (cards.isEmpty()) {
            cards.add(escapeText(truncateOverlayValue(safeDisplay(subject.id()), OVERLAY_MAX_CARD_VALUE_CHARS)));
        }

        return List.copyOf(cards);
    }

    private static String truncateOverlayValue(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        if (maxChars <= 1) {
            return value.substring(0, maxChars);
        }
        return value.substring(0, maxChars - 1) + "…";
    }

    private static String safeDisplay(String value) {
        return value == null ? "" : value;
    }

    private static String sanitizeColorOrDefault(String value, String defaultValue, String fieldName) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim();
        if (!SAFE_COLOR_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }
        return normalized;
    }

    private static String normalizeEnumValue(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static int intOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String escapeFilterValue(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'");
    }

    private static String escapeText(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'");
    }

    private static String doubleArg(double value) {
        return Double.toString(value);
    }

    private static void joinQuietly(Thread readerThread) {
        try {
            readerThread.join(Duration.ofSeconds(2).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void drainStream(InputStream inputStream, ByteArrayOutputStream sink) {
        byte[] buffer = new byte[1024];
        int total = 0;
        try (inputStream) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                if (total < STDERR_LIMIT_BYTES) {
                    int allowed = Math.min(read, STDERR_LIMIT_BYTES - total);
                    sink.write(buffer, 0, allowed);
                    total += allowed;
                }
            }
        } catch (IOException ignored) {
            // Best effort capture only.
        }
    }

    private static void drainStreamUnbounded(InputStream inputStream, ByteArrayOutputStream sink) {
        byte[] buffer = new byte[1024];
        try (inputStream) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                sink.write(buffer, 0, read);
            }
        } catch (IOException ignored) {
            // Best effort capture only.
        }
    }

    public record FfmpegRequest(
            String videoUrl,
            double startSeconds,
            double durationSeconds,
            int fps,
            int maxWidth,
            String format,
            int quality,
            Path outputDir,
            OverlaySettings overlay
    ) {
    }

    public record OverlaySettings(
            String mode,
            String position,
            int fontSize,
            String boxColor,
            String fontColor,
            int margin,
            int padding,
            List<String> cardTexts
    ) {
    }
}
