package com.snapplayerapi.api.v2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.snapplayerapi.api.dto.ProcessingOverlayRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

/**
 * Payload de criação de snap da API v2.
 *
 * <p>O contrato aceita `videoId` <strong>ou</strong> `videoUrl` para suportar dois fluxos:
 * reaproveitamento de vídeo já conhecido e criação/reuso automático por URL. Campos extras são
 * ignorados para manter compatibilidade progressiva com fases futuras do master.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSnapRequest(
        String videoId,
        String videoUrl,
        Long subjectTemplateId,
        @NotBlank String nickname,
        @NotBlank @Email String email,
        @NotNull OffsetDateTime dataFilmagem,
        @PositiveOrZero Double startSeconds,
        @PositiveOrZero Long startFrame,
        @NotNull @Positive Double durationSeconds,
        @Positive Double snapshotDurationSeconds,
        Integer fps,
        Integer maxWidth,
        String format,
        Integer quality,
        @NotNull @Valid V2SubjectRequest subject,
        @Valid ProcessingOverlayRequest overlay
) {
}
