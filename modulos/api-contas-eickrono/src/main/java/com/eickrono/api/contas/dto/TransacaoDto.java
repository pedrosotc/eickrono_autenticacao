package com.eickrono.api.contas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * DTO de transação.
 */
public record TransacaoDto(
        Long id,
        String tipo,
        BigDecimal valor,
        OffsetDateTime efetivadaEm,
        String descricao) {
}
