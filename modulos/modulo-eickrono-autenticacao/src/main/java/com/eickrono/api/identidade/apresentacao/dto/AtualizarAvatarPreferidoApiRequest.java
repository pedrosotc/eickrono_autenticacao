package com.eickrono.api.identidade.apresentacao.dto;

import jakarta.validation.constraints.NotBlank;

public record AtualizarAvatarPreferidoApiRequest(
        @NotBlank String aplicacaoId,
        @NotBlank String origem,
        String provedor,
        String url
) {
}
