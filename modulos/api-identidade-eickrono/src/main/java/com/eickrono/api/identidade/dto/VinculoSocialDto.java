package com.eickrono.api.identidade.dto;

import java.time.OffsetDateTime;

/**
 * DTO que representa o vínculo de login social.
 */
public record VinculoSocialDto(
        Long id,
        String provedor,
        String identificador,
        OffsetDateTime vinculadoEm) {
}
