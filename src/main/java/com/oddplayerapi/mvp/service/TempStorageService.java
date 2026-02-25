package com.oddplayerapi.mvp.service;

import com.oddplayerapi.mvp.config.MvpProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class TempStorageService {

    private final MvpProperties properties;

    public TempStorageService(MvpProperties properties) {
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
}
