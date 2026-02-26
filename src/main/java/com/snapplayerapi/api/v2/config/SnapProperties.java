package com.snapplayerapi.api.v2.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações operacionais da API v2 Snap-first.
 *
 * <p>Na Entrega 1 operamos com uma assinatura/template padrão fixos para reduzir complexidade
 * (sem autenticação e sem seleção explícita de contexto). Estas chaves centralizam esse
 * comportamento para facilitar a migração da fase síncrona para fases multi-assinatura.</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.snap")
public class SnapProperties {

    @NotBlank
    private String defaultAssinaturaCodigo = "default";

    @NotBlank
    private String defaultTemplateSlug = "default";

    /**
     * Optional absolute base URL used to build `publicUrl` in share responses.
     *
     * <p>If absent/blank, the API returns a relative path (e.g. `/public/snaps/{token}`), which is
     * enough for local development and test environments.</p>
     */
    private String publicBaseUrl;

    /**
     * Feature flag for assinatura API token validation on private `/v2/*` endpoints.
     *
     * <p>Entrega 3 step 2 introduces the validation path but keeps it disabled by default to avoid
     * breaking existing local/manual flows. Future deliveries can enable it per environment.</p>
     */
    private boolean requireApiToken = false;

    /**
     * Header used by private API routes to receive the assinatura token.
     */
    @NotBlank
    private String apiTokenHeader = "X-Assinatura-Token";

    /**
     * Feature flag for asynchronous snap creation (`POST /v2/snaps` returns `PENDING` and enqueues a job).
     *
     * <p>Disabled by default to preserve Entregas 1-3 behavior while Entrega 4 is introduced
     * incrementally. When enabled, the worker (local polling) is responsible for FFmpeg execution.</p>
     */
    private boolean asyncCreateEnabled = false;

    /**
     * Enables the local DB-polling worker that processes `snap_processing_job`.
     *
     * <p>Kept separate from `asyncCreateEnabled` so tests/tools can enqueue jobs without running the
     * scheduler automatically (manual triggering), and vice versa.</p>
     */
    private boolean workerEnabled = true;

    /**
     * Fixed delay between worker polling cycles (milliseconds).
     */
    private long workerPollDelayMs = 1000L;

    /**
     * Maximum number of jobs processed per polling cycle.
     */
    private int workerBatchSize = 1;

    /**
     * Maximum retry attempts for a job before final failure.
     */
    private int workerMaxAttempts = 3;

    /**
     * Delay before retrying a failed job (seconds).
     */
    private long workerRetryDelaySeconds = 10L;

    /**
     * Exponential backoff multiplier applied on retries (`baseDelay * multiplier^(attempt-1)`).
     */
    private double workerRetryBackoffMultiplier = 2.0d;

    /**
     * Upper bound for retry delay after exponential backoff (seconds).
     */
    private long workerRetryMaxDelaySeconds = 300L;

    /**
     * Marks `RUNNING` jobs as stale when no heartbeat/lock refresh is seen beyond this timeout.
     *
     * <p>Must be greater than `workerHeartbeatIntervalMs` — a rule of thumb is
     * `workerLockTimeoutSeconds ≥ workerHeartbeatIntervalMs / 1000 * 3`.
     * With the default heartbeat of 30 s, a timeout of 120 s provides 4 heartbeat windows
     * before a job is declared stale, tolerating transient DB slowness.</p>
     */
    private long workerLockTimeoutSeconds = 120L;

    /**
     * Interval between heartbeat cycles (milliseconds).
     *
     * <p>Each cycle updates `locked_at` for all `RUNNING` jobs owned by this instance,
     * preventing stale-recovery from reclaiming jobs that are still actively executing.
     * Rule of thumb: {@code workerHeartbeatIntervalMs < workerLockTimeoutSeconds * 1000 / 3}.</p>
     */
    private long workerHeartbeatIntervalMs = 30000L;

    /**
     * Enables periodic cleanup of terminal job rows (`COMPLETED`/`FAILED`) older than retention.
     */
    private boolean jobCleanupEnabled = true;

    /**
     * Fixed delay between cleanup cycles (milliseconds).
     */
    private long jobCleanupDelayMs = 60000L;

    /**
     * Retention for terminal job rows before cleanup (hours).
     */
    private long jobRetentionHours = 168L;

    /**
     * Maximum terminal jobs deleted per cleanup cycle.
     */
    private int jobCleanupBatchSize = 100;

    /**
     * Logical identifier of the worker instance, persisted in `lock_owner` for diagnostics.
     */
    @NotBlank
    private String workerInstanceId = "local-worker";

    public String getDefaultAssinaturaCodigo() {
        return defaultAssinaturaCodigo;
    }

    public void setDefaultAssinaturaCodigo(String defaultAssinaturaCodigo) {
        this.defaultAssinaturaCodigo = defaultAssinaturaCodigo;
    }

    public String getDefaultTemplateSlug() {
        return defaultTemplateSlug;
    }

    public void setDefaultTemplateSlug(String defaultTemplateSlug) {
        this.defaultTemplateSlug = defaultTemplateSlug;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public boolean isRequireApiToken() {
        return requireApiToken;
    }

    public void setRequireApiToken(boolean requireApiToken) {
        this.requireApiToken = requireApiToken;
    }

    public String getApiTokenHeader() {
        return apiTokenHeader;
    }

    public void setApiTokenHeader(String apiTokenHeader) {
        this.apiTokenHeader = apiTokenHeader;
    }

    public boolean isAsyncCreateEnabled() {
        return asyncCreateEnabled;
    }

    public void setAsyncCreateEnabled(boolean asyncCreateEnabled) {
        this.asyncCreateEnabled = asyncCreateEnabled;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getWorkerPollDelayMs() {
        return workerPollDelayMs;
    }

    public void setWorkerPollDelayMs(long workerPollDelayMs) {
        this.workerPollDelayMs = workerPollDelayMs;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public int getWorkerMaxAttempts() {
        return workerMaxAttempts;
    }

    public void setWorkerMaxAttempts(int workerMaxAttempts) {
        this.workerMaxAttempts = workerMaxAttempts;
    }

    public long getWorkerRetryDelaySeconds() {
        return workerRetryDelaySeconds;
    }

    public void setWorkerRetryDelaySeconds(long workerRetryDelaySeconds) {
        this.workerRetryDelaySeconds = workerRetryDelaySeconds;
    }

    public double getWorkerRetryBackoffMultiplier() {
        return workerRetryBackoffMultiplier;
    }

    public void setWorkerRetryBackoffMultiplier(double workerRetryBackoffMultiplier) {
        this.workerRetryBackoffMultiplier = workerRetryBackoffMultiplier;
    }

    public long getWorkerRetryMaxDelaySeconds() {
        return workerRetryMaxDelaySeconds;
    }

    public void setWorkerRetryMaxDelaySeconds(long workerRetryMaxDelaySeconds) {
        this.workerRetryMaxDelaySeconds = workerRetryMaxDelaySeconds;
    }

    public long getWorkerLockTimeoutSeconds() {
        return workerLockTimeoutSeconds;
    }

    public void setWorkerLockTimeoutSeconds(long workerLockTimeoutSeconds) {
        this.workerLockTimeoutSeconds = workerLockTimeoutSeconds;
    }

    public boolean isJobCleanupEnabled() {
        return jobCleanupEnabled;
    }

    public void setJobCleanupEnabled(boolean jobCleanupEnabled) {
        this.jobCleanupEnabled = jobCleanupEnabled;
    }

    public long getJobCleanupDelayMs() {
        return jobCleanupDelayMs;
    }

    public void setJobCleanupDelayMs(long jobCleanupDelayMs) {
        this.jobCleanupDelayMs = jobCleanupDelayMs;
    }

    public long getJobRetentionHours() {
        return jobRetentionHours;
    }

    public void setJobRetentionHours(long jobRetentionHours) {
        this.jobRetentionHours = jobRetentionHours;
    }

    public int getJobCleanupBatchSize() {
        return jobCleanupBatchSize;
    }

    public void setJobCleanupBatchSize(int jobCleanupBatchSize) {
        this.jobCleanupBatchSize = jobCleanupBatchSize;
    }

    public long getWorkerHeartbeatIntervalMs() {
        return workerHeartbeatIntervalMs;
    }

    public void setWorkerHeartbeatIntervalMs(long workerHeartbeatIntervalMs) {
        this.workerHeartbeatIntervalMs = workerHeartbeatIntervalMs;
    }

    public String getWorkerInstanceId() {
        return workerInstanceId;
    }

    public void setWorkerInstanceId(String workerInstanceId) {
        this.workerInstanceId = workerInstanceId;
    }
}
