package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record IniciarRecuperacaoSenhaApiRequest(
        @NotBlank String emailPrincipal
) {
}
