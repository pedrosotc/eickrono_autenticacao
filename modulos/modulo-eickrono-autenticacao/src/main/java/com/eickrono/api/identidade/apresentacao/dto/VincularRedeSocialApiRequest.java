package com.eickrono.api.identidade.apresentacao.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record VincularRedeSocialApiRequest(
        @NotBlank String tokenExterno,
        UUID contextoSocialPendenteId,
        String aplicacaoId,
        String nomeExibicaoExterno,
        String urlAvatarExterno
) {
}
