package com.snapplayerapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

/**
 * Input item for the synchronous MVP endpoint.
 *
 * <p>Unknown fields are ignored on purpose so the MVP can accept the richer payload
 * from the master plan while only implementing the local extraction subset.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessingFilmagemRequest(
        @NotBlank String videoUrl,
        @PositiveOrZero Double startSeconds,
        @PositiveOrZero Long startFrame,
        @NotNull @Positive Double durationSeconds,
        @Positive Double snapshotDurationSeconds,
        Integer fps,
        Integer maxWidth,
        String format,
        Integer quality,
        @NotNull OffsetDateTime dataFilmagem,
        @NotNull @Valid ProcessingSubjectRequest subject,
        @Valid ProcessingOverlayRequest overlay,
        String clientRequestId
) {
}
