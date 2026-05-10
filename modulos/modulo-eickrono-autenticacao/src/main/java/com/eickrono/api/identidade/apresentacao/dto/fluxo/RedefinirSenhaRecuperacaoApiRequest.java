package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaRecuperacaoApiRequest(
        @NotBlank String senha,
        @NotBlank String confirmacaoSenha
) {
}
