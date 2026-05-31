package com.eickrono.api.identidade.aplicacao.modelo;

public record CredencialSocialDeclarada(
        String identificadorExterno,
        String email,
        String nomeUsuarioExterno,
        String nomeCompleto,
        String urlAvatarExterno
) {
}
