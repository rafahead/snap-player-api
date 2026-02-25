package com.snapplayerapi.api.v2.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Minimal assinatura (tenant) persistence model used by Entrega 1.
 *
 * <p>Even though phase 1 runs with a fixed `default` assinatura, the schema and service logic are
 * already scoped by `assinatura_id` to avoid a later disruptive migration.</p>
 */
@Entity
@Table(name = "assinatura")
public class AssinaturaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "api_token", length = 200)
    private String apiToken;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
