package com.snapplayerapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Overlay configuration accepted by the MVP.
 *
 * <p>The master plan supports richer overlay semantics, but the MVP keeps a compact set of visual
 * options and renders a drawtext banner in the top-right corner. Unknown fields are ignored so the
 * payload can evolve without breaking the MVP parser.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessingOverlayRequest(
        Boolean enabled,
        String mode,
        String position,
        Integer fontSize,
        String boxColor,
        String fontColor,
        Integer margin,
        Integer padding
) {
}
