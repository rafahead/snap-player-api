package com.snapplayerapi.api.v2.dto;

import java.util.List;

/**
 * Resposta simples de busca da Entrega 1.
 */
public record SnapSearchResponse(
        int total,
        List<SnapResponse> items
) {
}
