package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record VinculoSocialPendenteApiRequest(
        @NotBlank String provedor,
        @NotBlank String identificadorExterno,
        String contextoSocialPendenteId,
        String nomeUsuarioExterno,
        String email,
        String nomeCompleto,
        String urlAvatarExterno
) {
}
