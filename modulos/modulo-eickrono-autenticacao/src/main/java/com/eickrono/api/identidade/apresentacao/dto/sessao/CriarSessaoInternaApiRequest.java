package com.eickrono.api.identidade.apresentacao.dto.sessao;

import jakarta.validation.constraints.NotBlank;

public record CriarSessaoInternaApiRequest(
        @NotBlank String login,
        @NotBlank String senha
) {
}
