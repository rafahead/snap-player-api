package com.snapplayerapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents one typed attribute inside the generic {@code subject} object.
 *
 * <p>The MVP intentionally keeps the contract flexible (string/number) because the
 * same extraction pipeline should work for multiple domains, not only cattle data.
 * Type/value consistency is enforced in service-level validation to produce clear,
 * contextual error messages per item.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessingSubjectAttributeRequest(
        @NotBlank String key,
        @NotBlank String type,
        String stringValue,
        Double numberValue
) {
}
