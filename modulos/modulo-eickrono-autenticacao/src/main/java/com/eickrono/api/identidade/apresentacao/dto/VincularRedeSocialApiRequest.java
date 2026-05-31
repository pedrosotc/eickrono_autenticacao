package com.eickrono.api.identidade.apresentacao.dto;

public record VincularRedeSocialApiRequest(
        String aplicacaoId,
        String nomeExibicaoExterno,
        String urlAvatarExterno,
        String identificadorExterno,
        String nomeUsuarioExterno,
        String email,
        String nomeCompleto,
        Boolean avatarPreferido
) {
}
