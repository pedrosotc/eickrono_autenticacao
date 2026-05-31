package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarSessaoApiRequest(
        @NotBlank String aplicacaoId,
        @NotBlank String login,
        @NotBlank String senha,
        @Valid @NotNull DispositivoSessaoApiRequest dispositivo,
        @Valid @NotNull AtestacaoOperacaoApiRequest atestacao,
        @Valid @NotNull SegurancaAplicativoApiRequest segurancaAplicativo
) {
}
