package com.eickrono.api.identidade.apresentacao.dto.cadastro;

import jakarta.validation.constraints.NotBlank;

public record ConfirmarEmailCadastroInternoApiRequest(
        @NotBlank String codigo
) {
}
