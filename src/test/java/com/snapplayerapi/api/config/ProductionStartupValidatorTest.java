package com.snapplayerapi.api.config;

import com.snapplayerapi.api.v2.config.SnapProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for production startup validation rules introduced in Entrega 6.
 */
class ProductionStartupValidatorTest {

    @Test
    void shouldAcceptValidProductionConfiguration() {
        ProductionStartupValidator validator = newValidator();

        assertDoesNotThrow(validator::validateForProduction);
    }

    @Test
    void shouldRejectH2DatasourceInProdProfile() {
        ProductionStartupValidator validator = new ProductionStartupValidator(
                processingProperties(),
                storageProperties(),
                snapProperties(),
                "jdbc:h2:file:./.data/snapplayerapi",
                "sa",
                "secret"
        );

        assertThrows(IllegalStateException.class, validator::validateForProduction);
    }

    @Test
    void shouldRejectRelativeTmpBaseInProdProfile() {
        ProcessingProperties processingProperties = processingProperties();
        processingProperties.setTmpBase("./tmp/video-frames-processing");

        ProductionStartupValidator validator = new ProductionStartupValidator(
                processingProperties,
                storageProperties(),
                snapProperties(),
                "jdbc:postgresql://127.0.0.1:5432/snap_player",
                "snapplayer",
                "secret"
        );

        assertThrows(IllegalStateException.class, validator::validateForProduction);
    }

    @Test
    void shouldRejectAsyncWithoutWorkerInProdProfile() {
        SnapProperties snapProperties = snapProperties();
        snapProperties.setAsyncCreateEnabled(true);
        snapProperties.setWorkerEnabled(false);

        ProductionStartupValidator validator = new ProductionStartupValidator(
                processingProperties(),
                storageProperties(),
                snapProperties,
                "jdbc:postgresql://127.0.0.1:5432/snap_player",
                "snapplayer",
                "secret"
        );

        assertThrows(IllegalStateException.class, validator::validateForProduction);
    }

    @Test
    void shouldRejectInvalidWorkerHeartbeatTiming() {
        SnapProperties snapProperties = snapProperties();
        snapProperties.setWorkerHeartbeatIntervalMs(50_000L);
        snapProperties.setWorkerLockTimeoutSeconds(120L);

        ProductionStartupValidator validator = new ProductionStartupValidator(
                processingProperties(),
                storageProperties(),
                snapProperties,
                "jdbc:postgresql://127.0.0.1:5432/snap_player",
                "snapplayer",
                "secret"
        );

        assertThrows(IllegalStateException.class, validator::validateForProduction);
    }

    @Test
    void shouldRejectLocalStorageEnabledInProdProfile() {
        StorageProperties storageProperties = storageProperties();
        storageProperties.getLocal().setEnabled(true);

        ProductionStartupValidator validator = new ProductionStartupValidator(
                processingProperties(),
                storageProperties,
                snapProperties(),
                "jdbc:postgresql://127.0.0.1:5432/snap_player",
                "snapplayer",
                "secret"
        );

        assertThrows(IllegalStateException.class, validator::validateForProduction);
    }

    private static ProductionStartupValidator newValidator() {
        return new ProductionStartupValidator(
                processingProperties(),
                storageProperties(),
                snapProperties(),
                "jdbc:postgresql://127.0.0.1:5432/snap_player",
                "snapplayer",
                "secret"
        );
    }

    private static ProcessingProperties processingProperties() {
        ProcessingProperties properties = new ProcessingProperties();
        properties.setTmpBase("/data/tmp/video-frames-processing");
        return properties;
    }

    private static StorageProperties storageProperties() {
        StorageProperties properties = new StorageProperties();
        properties.getLocal().setEnabled(false);
        properties.getS3().setEnabled(true);
        properties.getS3().setRegion("us-east-1");
        properties.getS3().setBucket("bucket");
        properties.getS3().setAccessKey("key");
        properties.getS3().setSecretKey("secret");
        properties.getS3().setPublicBaseUrl("https://cdn.example.com");
        return properties;
    }

    private static SnapProperties snapProperties() {
        SnapProperties properties = new SnapProperties();
        properties.setAsyncCreateEnabled(true);
        properties.setWorkerEnabled(true);
        properties.setWorkerHeartbeatIntervalMs(30_000L);
        properties.setWorkerLockTimeoutSeconds(120L);
        return properties;
    }
}
