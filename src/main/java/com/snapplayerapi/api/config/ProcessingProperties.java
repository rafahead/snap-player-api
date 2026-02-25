package com.snapplayerapi.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.processing")
public class ProcessingProperties {

    @NotBlank
    private String tmpBase = "/data/tmp/video-frames-processing";

    @Min(1)
    private int maxBatchItems = 10;

    @Min(1)
    private int maxSubjectAttributes = 100;

    @Positive
    private double maxDurationSeconds = 5.0;

    @Min(1)
    private int maxFps = 10;

    @Min(320)
    private int maxWidth = 1280;

    private List<String> acceptedContainers = new ArrayList<>(List.of("mp4", "mov", "mkv", "webm"));

    @Valid
    private final Ffmpeg ffmpeg = new Ffmpeg();

    @Valid
    private final Ffprobe ffprobe = new Ffprobe();

    public String getTmpBase() {
        return tmpBase;
    }

    public void setTmpBase(String tmpBase) {
        this.tmpBase = tmpBase;
    }

    public int getMaxBatchItems() {
        return maxBatchItems;
    }

    public void setMaxBatchItems(int maxBatchItems) {
        this.maxBatchItems = maxBatchItems;
    }

    public int getMaxSubjectAttributes() {
        return maxSubjectAttributes;
    }

    public void setMaxSubjectAttributes(int maxSubjectAttributes) {
        this.maxSubjectAttributes = maxSubjectAttributes;
    }

    public double getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(double maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public int getMaxFps() {
        return maxFps;
    }

    public void setMaxFps(int maxFps) {
        this.maxFps = maxFps;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public List<String> getAcceptedContainers() {
        return acceptedContainers;
    }

    public void setAcceptedContainers(List<String> acceptedContainers) {
        this.acceptedContainers = acceptedContainers == null ? new ArrayList<>() : new ArrayList<>(acceptedContainers);
    }

    public Ffmpeg getFfmpeg() {
        return ffmpeg;
    }

    public Ffprobe getFfprobe() {
        return ffprobe;
    }

    public static class Ffmpeg {
        @NotBlank
        private String path = "ffmpeg";

        @Min(1)
        private long timeoutSeconds = 60;

        @NotBlank
        private String fontFile = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public String getFontFile() {
            return fontFile;
        }

        public void setFontFile(String fontFile) {
            this.fontFile = fontFile;
        }
    }

    public static class Ffprobe {
        @NotBlank
        private String path = "ffprobe";

        @Min(1)
        private long timeoutSeconds = 30;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
