package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CriarSessaoApiRequest(
        @NotBlank String aplicacaoId,
        @NotBlank String login,
        @NotBlank String senha,
        UUID contextoSocialPendenteId,
        @Valid @NotNull DispositivoSessaoApiRequest dispositivo,
        @Valid @NotNull AtestacaoOperacaoApiRequest atestacao,
        @Valid @NotNull SegurancaAplicativoApiRequest segurancaAplicativo
) {
}
