package com.snapplayerapi.api.v2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snap aggregate persistence record (Entrega 1 synchronous version).
 *
 * <p>This table stores both request parameters and processing outputs as a durable execution
 * snapshot, including JSON blobs for structured payloads such as subject, probe, snapshot video and
 * frame list.</p>
 */
@Entity
@Table(name = "snap")
public class SnapEntity {

    @Id
    private UUID id;

    @Column(name = "assinatura_id", nullable = false)
    private Long assinaturaId;

    @Column(name = "video_id", nullable = false)
    private UUID videoId;

    @Column(name = "created_by_usuario_id", nullable = false)
    private Long createdByUsuarioId;

    @Column(name = "subject_template_id")
    private Long subjectTemplateId;

    @Column(name = "nickname_snapshot", nullable = false, length = 120)
    private String nicknameSnapshot;

    @Column(name = "email_snapshot", nullable = false, length = 320)
    private String emailSnapshot;

    @Column(name = "tipo_snap", nullable = false, length = 20)
    private String tipoSnap;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "public_share_token", length = 200)
    private String publicShareToken;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "data_filmagem", nullable = false)
    private OffsetDateTime dataFilmagem;

    @Column(name = "video_url", nullable = false, columnDefinition = "text")
    private String videoUrl;

    @Column(name = "start_seconds")
    private Double startSeconds;

    @Column(name = "start_frame")
    private Long startFrame;

    @Column(name = "resolved_start_seconds")
    private Double resolvedStartSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private Double durationSeconds;

    @Column(name = "snapshot_duration_seconds", nullable = false)
    private Double snapshotDurationSeconds;

    @Column(nullable = false)
    private Integer fps;

    @Column(name = "max_width", nullable = false)
    private Integer maxWidth;

    @Column(nullable = false, length = 10)
    private String format;

    @Column
    private Integer quality;

    @Column(name = "subject_id", nullable = false, length = 200)
    private String subjectId;

    @Column(name = "subject_json", nullable = false, columnDefinition = "text")
    private String subjectJson;

    @Column(name = "overlay_json", columnDefinition = "text")
    private String overlayJson;

    @Column(name = "video_probe_json", columnDefinition = "text")
    private String videoProbeJson;

    @Column(name = "snapshot_video_json", columnDefinition = "text")
    private String snapshotVideoJson;

    @Column(name = "frames_json", columnDefinition = "text")
    private String framesJson;

    @Column(name = "frame_count", nullable = false)
    private Integer frameCount;

    @Column(name = "output_dir", columnDefinition = "text")
    private String outputDir;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getAssinaturaId() { return assinaturaId; }
    public void setAssinaturaId(Long assinaturaId) { this.assinaturaId = assinaturaId; }
    public UUID getVideoId() { return videoId; }
    public void setVideoId(UUID videoId) { this.videoId = videoId; }
    public Long getCreatedByUsuarioId() { return createdByUsuarioId; }
    public void setCreatedByUsuarioId(Long createdByUsuarioId) { this.createdByUsuarioId = createdByUsuarioId; }
    public Long getSubjectTemplateId() { return subjectTemplateId; }
    public void setSubjectTemplateId(Long subjectTemplateId) { this.subjectTemplateId = subjectTemplateId; }
    public String getNicknameSnapshot() { return nicknameSnapshot; }
    public void setNicknameSnapshot(String nicknameSnapshot) { this.nicknameSnapshot = nicknameSnapshot; }
    public String getEmailSnapshot() { return emailSnapshot; }
    public void setEmailSnapshot(String emailSnapshot) { this.emailSnapshot = emailSnapshot; }
    public String getTipoSnap() { return tipoSnap; }
    public void setTipoSnap(String tipoSnap) { this.tipoSnap = tipoSnap; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublicShareToken() { return publicShareToken; }
    public void setPublicShareToken(String publicShareToken) { this.publicShareToken = publicShareToken; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public OffsetDateTime getDataFilmagem() { return dataFilmagem; }
    public void setDataFilmagem(OffsetDateTime dataFilmagem) { this.dataFilmagem = dataFilmagem; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public Double getStartSeconds() { return startSeconds; }
    public void setStartSeconds(Double startSeconds) { this.startSeconds = startSeconds; }
    public Long getStartFrame() { return startFrame; }
    public void setStartFrame(Long startFrame) { this.startFrame = startFrame; }
    public Double getResolvedStartSeconds() { return resolvedStartSeconds; }
    public void setResolvedStartSeconds(Double resolvedStartSeconds) { this.resolvedStartSeconds = resolvedStartSeconds; }
    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }
    public Double getSnapshotDurationSeconds() { return snapshotDurationSeconds; }
    public void setSnapshotDurationSeconds(Double snapshotDurationSeconds) { this.snapshotDurationSeconds = snapshotDurationSeconds; }
    public Integer getFps() { return fps; }
    public void setFps(Integer fps) { this.fps = fps; }
    public Integer getMaxWidth() { return maxWidth; }
    public void setMaxWidth(Integer maxWidth) { this.maxWidth = maxWidth; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Integer getQuality() { return quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getSubjectJson() { return subjectJson; }
    public void setSubjectJson(String subjectJson) { this.subjectJson = subjectJson; }
    public String getOverlayJson() { return overlayJson; }
    public void setOverlayJson(String overlayJson) { this.overlayJson = overlayJson; }
    public String getVideoProbeJson() { return videoProbeJson; }
    public void setVideoProbeJson(String videoProbeJson) { this.videoProbeJson = videoProbeJson; }
    public String getSnapshotVideoJson() { return snapshotVideoJson; }
    public void setSnapshotVideoJson(String snapshotVideoJson) { this.snapshotVideoJson = snapshotVideoJson; }
    public String getFramesJson() { return framesJson; }
    public void setFramesJson(String framesJson) { this.framesJson = framesJson; }
    public Integer getFrameCount() { return frameCount; }
    public void setFrameCount(Integer frameCount) { this.frameCount = frameCount; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime processedAt) { this.processedAt = processedAt; }
}
