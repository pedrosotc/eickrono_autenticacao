package com.eickrono.api.identidade.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload para criação de vínculo social.
 */
public record CriarVinculoSocialRequisicao(
        @NotBlank(message = "Provedor é obrigatório") String provedor,
        @NotBlank(message = "Identificador é obrigatório") String identificador) {
}
