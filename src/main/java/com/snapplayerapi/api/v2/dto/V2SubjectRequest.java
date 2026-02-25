package com.snapplayerapi.api.v2.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.snapplayerapi.api.dto.ProcessingSubjectAttributeRequest;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Representação de `subject` na v2.
 *
 * <p>Diferente do MVP, `id` pode vir ausente no request para permitir o fallback de negócio
 * (`subject.id = snapId`) definido no ADR 0004.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record V2SubjectRequest(
        String id,
        List<@Valid ProcessingSubjectAttributeRequest> attributes
) {
}
