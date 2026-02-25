package com.oddplayerapi.mvp.service;

import com.oddplayerapi.mvp.config.MvpProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateRequestAndItemDirsAndListFrames() throws IOException {
        MvpProperties properties = new MvpProperties();
        properties.setTmpBase(tempDir.toString());
        TempStorageService service = new TempStorageService(properties);

        Path requestDir = service.createRequestDir("req-123");
        Path itemDir = service.createItemDir(requestDir, 0);

        Files.createFile(itemDir.resolve("frame_00002.jpg"));
        Files.createFile(itemDir.resolve("frame_00001.jpg"));
        Files.createFile(itemDir.resolve("ignore.txt"));

        List<Path> frames = service.listFrameFiles(itemDir, "jpg");

        assertTrue(Files.isDirectory(requestDir));
        assertTrue(Files.isDirectory(itemDir));
        assertEquals(2, frames.size());
        assertEquals("frame_00001.jpg", frames.get(0).getFileName().toString());
        assertEquals("frame_00002.jpg", frames.get(1).getFileName().toString());
    }
}
