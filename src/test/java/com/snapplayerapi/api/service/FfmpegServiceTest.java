package com.snapplayerapi.api.service;

import com.snapplayerapi.api.config.ProcessingProperties;
import com.snapplayerapi.api.dto.ProcessingOverlayRequest;
import com.snapplayerapi.api.dto.ProcessingSubjectAttributeRequest;
import com.snapplayerapi.api.dto.ProcessingSubjectRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegServiceTest {

    @Test
    void shouldBuildSafeCommandAsArgumentListForJpg() {
        ProcessingProperties properties = new ProcessingProperties();
        properties.getFfmpeg().setPath("ffmpeg");
        FfmpegService service = new FfmpegService(properties);

        List<String> command = service.buildCommand(new FfmpegService.FfmpegRequest(
                "https://example.com/video.mp4",
                1.5,
                2.0,
                5,
                800,
                "jpg",
                3,
                Path.of("/tmp/out"),
                null
        ));

        assertEquals("ffmpeg", command.get(0));
        assertFalse(command.contains("bash"));
        assertFalse(command.contains("-c"));
        assertTrue(command.contains("-vf"));
        assertTrue(command.contains("fps=5,scale=800:-2"));
        assertTrue(command.contains("-q:v"));
        assertEquals("/tmp/out/frame_%05d.jpg", command.get(command.size() - 1));
    }

    @Test
    void shouldNotIncludeQualityForPng() {
        ProcessingProperties properties = new ProcessingProperties();
        FfmpegService service = new FfmpegService(properties);

        List<String> command = service.buildCommand(new FfmpegService.FfmpegRequest(
                "https://example.com/video.mp4",
                0.0,
                1.0,
                2,
                640,
                "png",
                3,
                Path.of("/tmp/out"),
                null
        ));

        assertFalse(command.contains("-q:v"));
        assertEquals("/tmp/out/frame_%05d.png", command.get(command.size() - 1));
    }

    @Test
    void shouldBuildSnapshotCommand() {
        ProcessingProperties properties = new ProcessingProperties();
        FfmpegService service = new FfmpegService(properties);

        List<String> command = service.buildSnapshotCommand(new FfmpegService.FfmpegRequest(
                "https://example.com/video.mp4",
                12.0,
                3.0,
                30,
                1280,
                "jpg",
                3,
                Path.of("/tmp/out"),
                null
        ));

        assertEquals("ffmpeg", command.get(0));
        assertTrue(command.contains("libx264"));
        assertTrue(command.contains("veryfast"));
        assertTrue(command.contains("-an"));
        assertTrue(command.contains("scale='min(1280,iw)':-2"));
        assertEquals("/tmp/out/snapshot.mp4", command.get(command.size() - 1));
    }

    @Test
    void shouldIncludeDrawtextFilterWhenOverlayEnabled() {
        ProcessingProperties properties = new ProcessingProperties();
        properties.getFfmpeg().setFontFile("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
        FfmpegService service = new FfmpegService(properties);

        FfmpegService.OverlaySettings overlay = FfmpegService.resolveOverlay(new ProcessingOverlayRequest(
                true,
                "SUBJECT_AND_BOTH",
                "TOP_RIGHT",
                40,
                "black@0.7",
                "white",
                20,
                10
        ), new ProcessingSubjectRequest(
                "animal-123",
                List.of(
                        new ProcessingSubjectAttributeRequest("brinco", "string", "12334234534", null),
                        new ProcessingSubjectAttributeRequest("peso", "number", null, 450.0)
                )
        ));

        List<String> command = service.buildCommand(new FfmpegService.FfmpegRequest(
                "https://example.com/video.mp4",
                1.0,
                1.0,
                5,
                640,
                "jpg",
                3,
                Path.of("/tmp/out"),
                overlay
        ));

        String vf = command.get(command.indexOf("-vf") + 1);
        assertTrue(vf.contains("fps=5,scale=640:-2"));
        assertTrue(vf.contains("drawtext="));
        assertEquals(2, vf.split("drawtext=", -1).length - 1);
        assertFalse(vf.contains("id=animal-123"));
        assertTrue(vf.contains("12334234534"));
        assertTrue(vf.contains("450.0"));
        assertFalse(vf.contains("%{n}"));
        assertFalse(vf.contains("%{pts\\:hms}"));
        assertTrue(vf.contains("x=w-tw-20"));
        assertTrue(vf.contains(":y=20"));
        assertTrue(vf.contains(":y=86"));
        assertTrue(vf.contains("boxcolor=black@0.7"));
    }
}
