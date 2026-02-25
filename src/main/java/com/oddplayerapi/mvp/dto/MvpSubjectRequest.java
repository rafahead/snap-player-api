package com.oddplayerapi.mvp.dto;

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
public record MvpSubjectRequest(
        @NotBlank String id,
        List<@Valid MvpSubjectAttributeRequest> attributes
) {
}
