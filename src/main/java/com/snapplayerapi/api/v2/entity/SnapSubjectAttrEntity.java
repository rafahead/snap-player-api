package com.snapplayerapi.api.v2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Denormalized subject-attribute projection used for indexed snap search.
 *
 * <p>Rows are generated from `snap.subject_json` during snap creation. This keeps the public JSON
 * payload intact while enabling DB-level filtering by attribute.</p>
 */
@Entity
@Table(name = "snap_subject_attr")
public class SnapSubjectAttrEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snap_id", nullable = false)
    private UUID snapId;

    @Column(name = "assinatura_id", nullable = false)
    private Long assinaturaId;

    @Column(name = "video_id", nullable = false)
    private UUID videoId;

    @Column(name = "subject_id", nullable = false, length = 200)
    private String subjectId;

    @Column(name = "attr_key", nullable = false, length = 120)
    private String attrKey;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "string_value")
    private String stringValue;

    @Column(name = "number_value", precision = 20, scale = 6)
    private BigDecimal numberValue;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getSnapId() { return snapId; }
    public void setSnapId(UUID snapId) { this.snapId = snapId; }
    public Long getAssinaturaId() { return assinaturaId; }
    public void setAssinaturaId(Long assinaturaId) { this.assinaturaId = assinaturaId; }
    public UUID getVideoId() { return videoId; }
    public void setVideoId(UUID videoId) { this.videoId = videoId; }
    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }
    public String getAttrKey() { return attrKey; }
    public void setAttrKey(String attrKey) { this.attrKey = attrKey; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getStringValue() { return stringValue; }
    public void setStringValue(String stringValue) { this.stringValue = stringValue; }
    public BigDecimal getNumberValue() { return numberValue; }
    public void setNumberValue(BigDecimal numberValue) { this.numberValue = numberValue; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
