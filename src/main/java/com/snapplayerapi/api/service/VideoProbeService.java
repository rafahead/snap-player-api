package com.snapplayerapi.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snapplayerapi.api.config.ProcessingProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class VideoProbeService {

    private static final int OUTPUT_LIMIT_BYTES = 16384;

    private final ProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public VideoProbeService(ProcessingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ProbeResult probe(String videoUrl) {
        ProbeCommandResult commandResult = runProbeCommand(videoUrl);
        if (!commandResult.success()) {
            return ProbeResult.incompatible(null, null, null, null, null, null, null,
                    "ffprobe failed to open video: " + commandResult.output());
        }

        try {
            JsonNode root = objectMapper.readTree(commandResult.output());
            JsonNode formatNode = root.path("format");
            JsonNode streamsNode = root.path("streams");

            String containerFormat = textOrNull(formatNode, "format_name");
            Double formatDuration = parseDouble(textOrNull(formatNode, "duration"));

            JsonNode videoStream = null;
            if (streamsNode.isArray()) {
                for (JsonNode stream : streamsNode) {
                    if ("video".equals(stream.path("codec_type").asText())) {
                        videoStream = stream;
                        break;
                    }
                }
            }

            if (videoStream == null) {
                return ProbeResult.incompatible(containerFormat, null, null, null, formatDuration, null, null,
                        "No video stream found in media");
            }

            String codecName = textOrNull(videoStream, "codec_name");
            Integer width = intOrNull(videoStream, "width");
            Integer height = intOrNull(videoStream, "height");
            Double streamDuration = parseDouble(textOrNull(videoStream, "duration"));
            Double durationSeconds = streamDuration != null ? streamDuration : formatDuration;
            Double sourceFps = parseFrameRate(textOrNull(videoStream, "avg_frame_rate"));
            if (sourceFps == null) {
                sourceFps = parseFrameRate(textOrNull(videoStream, "r_frame_rate"));
            }
            String pixelFormat = textOrNull(videoStream, "pix_fmt");

            String incompatibilityReason = compatibilityReason(containerFormat, codecName, width, height);
            if (incompatibilityReason != null) {
                return ProbeResult.incompatible(containerFormat, codecName, width, height, durationSeconds, sourceFps, pixelFormat,
                        incompatibilityReason);
            }

            return ProbeResult.compatible(containerFormat, codecName, width, height, durationSeconds, sourceFps, pixelFormat);
        } catch (Exception e) {
            return ProbeResult.incompatible(null, null, null, null, null, null, null,
                    "Failed to parse ffprobe output: " + rootMessage(e));
        }
    }

    List<String> buildProbeCommand(String videoUrl) {
        List<String> args = new ArrayList<>();
        args.add(properties.getFfprobe().getPath());
        args.add("-v");
        args.add("error");
        args.add("-print_format");
        args.add("json");
        args.add("-show_format");
        args.add("-show_streams");
        args.add(videoUrl);
        return List.copyOf(args);
    }

    static Double parseFrameRate(String frameRate) {
        if (frameRate == null || frameRate.isBlank()) {
            return null;
        }
        if (!frameRate.contains("/")) {
            return parseDouble(frameRate);
        }
        String[] parts = frameRate.split("/", 2);
        Double numerator = parseDouble(parts[0]);
        Double denominator = parseDouble(parts[1]);
        if (numerator == null || denominator == null || denominator == 0.0) {
            return null;
        }
        double value = numerator / denominator;
        if (!Double.isFinite(value) || value <= 0) {
            return null;
        }
        return value;
    }

    private ProbeCommandResult runProbeCommand(String videoUrl) {
        ProcessBuilder processBuilder = new ProcessBuilder(buildProbeCommand(videoUrl));
        processBuilder.redirectErrorStream(true);

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start ffprobe process", e);
        }

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread readerThread = new Thread(() -> drainStream(process.getInputStream(), captured), "ffprobe-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished;
        try {
            finished = process.waitFor(properties.getFfprobe().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                joinQuietly(readerThread);
                return new ProbeCommandResult(false, "ffprobe timed out after %d seconds".formatted(properties.getFfprobe().getTimeoutSeconds()));
            }
            joinQuietly(readerThread);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Interrupted while waiting for ffprobe", e);
        }

        String output = captured.toString(StandardCharsets.UTF_8).trim();
        return new ProbeCommandResult(process.exitValue() == 0, output);
    }

    private String compatibilityReason(String containerFormat, String codecName, Integer width, Integer height) {
        if (containerFormat == null || containerFormat.isBlank()) {
            return "Could not detect container format";
        }
        Set<String> accepted = properties.getAcceptedContainers().stream()
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        boolean containerAccepted = Arrays.stream(containerFormat.split(","))
                .map(String::trim)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(accepted::contains);
        if (!containerAccepted) {
            return "Unsupported container format '%s'. Accepted: %s".formatted(containerFormat, properties.getAcceptedContainers());
        }
        if (codecName == null || codecName.isBlank()) {
            return "Could not detect video codec";
        }
        if (width == null || height == null || width <= 0 || height <= 0) {
            return "Invalid or missing video resolution";
        }
        return null;
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
                if (total < OUTPUT_LIMIT_BYTES) {
                    int allowed = Math.min(read, OUTPUT_LIMIT_BYTES - total);
                    sink.write(buffer, 0, allowed);
                    total += allowed;
                }
            }
        } catch (IOException ignored) {
            // best-effort capture only
        }
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        String text = field.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Integer intOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull()) {
            return null;
        }
        return field.isInt() || field.isLong() ? field.asInt() : null;
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank() || "N/A".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ProbeCommandResult(boolean success, String output) {
    }

    public record ProbeResult(
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
        public static ProbeResult compatible(String containerFormat, String codecName, Integer width, Integer height,
                                             Double durationSeconds, Double sourceFps, String pixelFormat) {
            return new ProbeResult(true, containerFormat, codecName, width, height, durationSeconds, sourceFps, pixelFormat, null);
        }

        public static ProbeResult incompatible(String containerFormat, String codecName, Integer width, Integer height,
                                               Double durationSeconds, Double sourceFps, String pixelFormat, String reason) {
            return new ProbeResult(false, containerFormat, codecName, width, height, durationSeconds, sourceFps, pixelFormat, reason);
        }

        public ProbeResult withReason(String newReason) {
            return new ProbeResult(false, containerFormat, codecName, width, height, durationSeconds, sourceFps, pixelFormat, newReason);
        }
    }
}
