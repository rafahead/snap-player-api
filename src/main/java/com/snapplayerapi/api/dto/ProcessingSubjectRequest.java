package com.snapplayerapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Generic subject metadata that identifies what the video refers to.
 *
 * <p>The API processes videos, but the business identity is intentionally generic
 * so the same pipeline can be reused across domains. The MVP stores/returns this
 * structure unchanged (after validation) to validate the future async contract.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessingSubjectRequest(
        @NotBlank String id,
        List<@Valid ProcessingSubjectAttributeRequest> attributes
) {
}
