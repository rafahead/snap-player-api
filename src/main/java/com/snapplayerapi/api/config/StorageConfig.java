package com.snapplayerapi.api.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Creates the S3 client only when S3 storage is enabled.
 *
 * <p>We keep this bean conditional so local development does not require AWS SDK credentials and
 * so tests can run with the local storage fallback by default.</p>
 */
@Configuration
public class StorageConfig {

    /**
     * Builds an S3-compatible client (Linode Object Storage) with optional endpoint override.
     *
     * <p>Path-style access is enabled for broader compatibility with S3-compatible providers.</p>
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
    public S3Client s3Client(StorageProperties storageProperties) {
        StorageProperties.S3 s3 = storageProperties.getS3();
        require("app.storage.s3.bucket", s3.getBucket());
        require("app.storage.s3.accessKey", s3.getAccessKey());
        require("app.storage.s3.secretKey", s3.getSecretKey());
        require("app.storage.s3.publicBaseUrl", s3.getPublicBaseUrl());
        require("app.storage.s3.region", s3.getRegion());

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3.getRegion().trim()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        s3.getAccessKey().trim(),
                        s3.getSecretKey().trim()
                )))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());

        if (hasText(s3.getEndpoint())) {
            builder.endpointOverride(URI.create(s3.getEndpoint().trim()));
        }
        return builder.build();
    }

    /**
     * Fails fast when S3 is enabled but required configuration is missing.
     */
    private static void require(String name, String value) {
        if (!hasText(value)) {
            throw new IllegalStateException("Missing required property when S3 storage is enabled: " + name);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
