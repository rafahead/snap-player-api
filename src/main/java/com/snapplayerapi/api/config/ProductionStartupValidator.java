package com.snapplayerapi.api.config;

import com.snapplayerapi.api.v2.config.SnapProperties;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail-fast validation for the production profile used in the current VM (Ubuntu/Linode) deploy.
 *
 * <p>The goal is to reject obviously unsafe or incomplete runtime configuration before the API
 * starts accepting traffic behind Nginx/systemd.</p>
 */
@Component
@Profile("prod")
public class ProductionStartupValidator implements ApplicationRunner {

    private final ProcessingProperties processingProperties;
    private final StorageProperties storageProperties;
    private final SnapProperties snapProperties;
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;

    public ProductionStartupValidator(
            ProcessingProperties processingProperties,
            StorageProperties storageProperties,
            SnapProperties snapProperties,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword) {
        this.processingProperties = processingProperties;
        this.storageProperties = storageProperties;
        this.snapProperties = snapProperties;
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateForProduction();
    }

    void validateForProduction() {
        requireText("spring.datasource.url", datasourceUrl);
        requireText("spring.datasource.username", datasourceUsername);
        requireText("spring.datasource.password", datasourcePassword);

        if (datasourceUrl.trim().startsWith("jdbc:h2:")) {
            throw new IllegalStateException("Production profile cannot use H2 datasource");
        }

        if (!storageProperties.getS3().isEnabled()) {
            throw new IllegalStateException("Production profile requires app.storage.s3.enabled=true");
        }
        if (storageProperties.getLocal().isEnabled()) {
            throw new IllegalStateException("Production profile requires app.storage.local.enabled=false");
        }

        requireAbsolutePath("app.processing.tmpBase", processingProperties.getTmpBase());

        if (snapProperties.isAsyncCreateEnabled() && !snapProperties.isWorkerEnabled()) {
            throw new IllegalStateException(
                    "app.snap.workerEnabled must be true when app.snap.asyncCreateEnabled=true");
        }

        long heartbeatMs = snapProperties.getWorkerHeartbeatIntervalMs();
        long lockTimeoutSeconds = snapProperties.getWorkerLockTimeoutSeconds();
        if (heartbeatMs * 3L >= lockTimeoutSeconds * 1000L) {
            throw new IllegalStateException(
                    "Invalid worker timing: workerHeartbeatIntervalMs must be < workerLockTimeoutSeconds * 1000 / 3");
        }
    }

    private static void requireText(String propertyName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property for production profile: " + propertyName);
        }
    }

    private static void requireAbsolutePath(String propertyName, String value) {
        requireText(propertyName, value);
        try {
            if (!Path.of(value.trim()).isAbsolute()) {
                throw new IllegalStateException("Production profile requires absolute path: " + propertyName);
            }
        } catch (InvalidPathException ex) {
            throw new IllegalStateException("Invalid filesystem path for property: " + propertyName, ex);
        }
    }
}
