package com.snapplayerapi.api.service;

import com.snapplayerapi.api.config.StorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Entrega 5 storage persistence behavior.
 */
class StorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCopyArtifactsToLocalStorageAndReturnStablePaths() throws Exception {
        // Simulate one processing item still inside the temp working directory.
        Path itemDir = Files.createDirectories(tempDir.resolve("tmp/request-1/item-000"));
        Path snapshot = Files.writeString(itemDir.resolve("snapshot.mp4"), "snapshot-bytes");
        Path frame1 = Files.writeString(itemDir.resolve("frame_00001.jpg"), "frame1");
        Path frame2 = Files.writeString(itemDir.resolve("frame_00002.jpg"), "frame2");

        StorageProperties properties = new StorageProperties();
        properties.getLocal().setEnabled(true);
        properties.getLocal().setBasePath(tempDir.resolve("storage").toString());
        properties.getS3().setEnabled(false);

        StorageService service = new StorageService(properties, Optional.empty());
        StorageService.StoredArtifacts stored = service.storeProcessingArtifacts(
                "req-1",
                0,
                "123e4567-e89b-12d3-a456-426614174000",
                itemDir,
                snapshot,
                List.of(frame1, frame2)
        );

        Path base = tempDir.resolve("storage");
        Path expectedSnapshot = base.resolve("snapshots/123e4567-e89b-12d3-a456-426614174000/snapshot.mp4");
        Path expectedFrame1 = base.resolve("frames/123e4567-e89b-12d3-a456-426614174000/frame_00001.jpg");
        Path expectedFrame2 = base.resolve("frames/123e4567-e89b-12d3-a456-426614174000/frame_00002.jpg");

        // The service must persist artifacts outside temp and return the persisted references.
        assertTrue(Files.exists(expectedSnapshot));
        assertTrue(Files.exists(expectedFrame1));
        assertTrue(Files.exists(expectedFrame2));
        assertEquals(expectedSnapshot.toString(), stored.snapshotPath());
        assertEquals(List.of(expectedFrame1.toString(), expectedFrame2.toString()), stored.framePaths());
        assertTrue(stored.outputDir().endsWith("frames/123e4567-e89b-12d3-a456-426614174000"));
    }
}
