package com.snapplayerapi.api.v2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Video catalog entry reused across snaps inside an assinatura.
 *
 * <p>Deduplication is based on a canonical URL hash so multiple snaps can reference the same video
 * row and share persisted probe metadata.</p>
 */
@Entity
@Table(name = "video")
public class VideoEntity {

    @Id
    private UUID id;

    @Column(name = "assinatura_id", nullable = false)
    private Long assinaturaId;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(name = "canonical_url", columnDefinition = "text")
    private String canonicalUrl;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "video_probe_json", columnDefinition = "text")
    private String videoProbeJson;

    @Column(name = "created_by_usuario_id")
    private Long createdByUsuarioId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getAssinaturaId() { return assinaturaId; }
    public void setAssinaturaId(Long assinaturaId) { this.assinaturaId = assinaturaId; }
    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }
    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }
    public String getVideoProbeJson() { return videoProbeJson; }
    public void setVideoProbeJson(String videoProbeJson) { this.videoProbeJson = videoProbeJson; }
    public Long getCreatedByUsuarioId() { return createdByUsuarioId; }
    public void setCreatedByUsuarioId(Long createdByUsuarioId) { this.createdByUsuarioId = createdByUsuarioId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
