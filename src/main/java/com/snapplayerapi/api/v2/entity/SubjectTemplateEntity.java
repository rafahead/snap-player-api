package com.snapplayerapi.api.v2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Subject template catalog per assinatura.
 *
 * <p>Entrega 1 uses a seeded default template, but the entity already supports multiple templates
 * so later phases can expose management endpoints without schema changes.</p>
 */
@Entity
@Table(name = "subject_template")
public class SubjectTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assinatura_id", nullable = false)
    private Long assinaturaId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false)
    private boolean ativo;

    @Lob
    @Column(name = "schema_json")
    private String schemaJson;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssinaturaId() { return assinaturaId; }
    public void setAssinaturaId(Long assinaturaId) { this.assinaturaId = assinaturaId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
