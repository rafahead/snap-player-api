package com.oddplayerapi.mvp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oddplayerapi.mvp.config.MvpProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoProbeServiceTest {

    @Test
    void shouldBuildSafeProbeCommandAsArgumentList() {
        MvpProperties properties = new MvpProperties();
        properties.getFfprobe().setPath("ffprobe");
        VideoProbeService service = new VideoProbeService(properties, new ObjectMapper());

        List<String> command = service.buildProbeCommand("https://example.com/video.mp4");

        assertEquals("ffprobe", command.get(0));
        assertTrue(command.contains("-print_format"));
        assertTrue(command.contains("json"));
        assertTrue(command.contains("-show_streams"));
        assertTrue(command.contains("https://example.com/video.mp4"));
    }

    @Test
    void shouldParseFrameRateRational() {
        assertEquals(30.0, VideoProbeService.parseFrameRate("30/1"));
        assertEquals(29.97002997002997, VideoProbeService.parseFrameRate("30000/1001"));
        assertNull(VideoProbeService.parseFrameRate("0/0"));
        assertNull(VideoProbeService.parseFrameRate("N/A"));
    }
}
