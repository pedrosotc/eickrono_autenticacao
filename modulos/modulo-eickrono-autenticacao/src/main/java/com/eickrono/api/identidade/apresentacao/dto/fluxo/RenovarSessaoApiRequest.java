package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record RenovarSessaoApiRequest(
        @NotBlank String refreshToken,
        String tokenDispositivo,
        String aplicacaoId,
        DispositivoSessaoApiRequest dispositivo
) {
}
