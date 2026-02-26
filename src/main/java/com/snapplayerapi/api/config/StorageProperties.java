package com.snapplayerapi.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage backends configuration used by Entrega 5.
 *
 * <p>The application supports:
 * - local persistent storage (default for dev)
 * - S3-compatible storage (Linode Object Storage in production)
 *
 * <p>The active backend is chosen by {@code app.storage.s3.enabled}/{@code app.storage.local.enabled}.</p>
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private final Local local = new Local();
    private final S3 s3 = new S3();

    public Local getLocal() {
        return local;
    }

    public S3 getS3() {
        return s3;
    }

    /**
     * Local persistent storage settings used as the default development fallback.
     */
    public static class Local {
        private boolean enabled = true;
        private String basePath = "./.data/storage";
        private String publicBaseUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }
    }

    /**
     * S3-compatible storage settings (AWS SDK v2 / Linode Object Storage).
     */
    public static class S3 {
        private boolean enabled;
        private String endpoint;
        private String region = "us-east-1";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String publicBaseUrl;
        private String prefix;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
