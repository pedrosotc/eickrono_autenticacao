package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record ConfirmarEmailCadastroApiRequest(
        @NotBlank String codigo
) {
}
