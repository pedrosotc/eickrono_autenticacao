package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record VinculoSocialConfirmadoApiRequest(
        @NotBlank String provedor,
        @NotBlank String identificadorExterno,
        String nomeUsuarioExterno,
        String email,
        String nomeCompleto,
        String urlAvatarExterno,
        Boolean avatarPreferido
) {
}
