package com.snapplayerapi.api.service;

import com.snapplayerapi.api.config.ProcessingProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class TempStorageService {

    private final ProcessingProperties properties;

    public TempStorageService(ProcessingProperties properties) {
        this.properties = properties;
    }

    public Path createRequestDir(String requestId) throws IOException {
        Path baseDir = Path.of(properties.getTmpBase());
        Files.createDirectories(baseDir);
        Path requestDir = baseDir.resolve(requestId);
        return Files.createDirectories(requestDir);
    }

    public Path createItemDir(Path requestDir, int itemIndex) throws IOException {
        String dirName = "item-%03d".formatted(itemIndex);
        return Files.createDirectories(requestDir.resolve(dirName));
    }

    public List<Path> listFrameFiles(Path itemDir, String format) throws IOException {
        String suffix = "." + format;
        try (Stream<Path> stream = Files.list(itemDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("frame_"))
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    /**
     * Deletes a directory and all its contents recursively.
     *
     * <p>Used to clean up per-request temp directories after processing completes (success or
     * failure). Errors are silently swallowed — cleanup failures must not mask the original
     * processing result. The scheduled cleanup job handles any directories left behind by crashes.</p>
     */
    public void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                            // Best-effort: scheduler will clean up on next run
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }
}
