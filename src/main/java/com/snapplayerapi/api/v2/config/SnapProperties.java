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
}
