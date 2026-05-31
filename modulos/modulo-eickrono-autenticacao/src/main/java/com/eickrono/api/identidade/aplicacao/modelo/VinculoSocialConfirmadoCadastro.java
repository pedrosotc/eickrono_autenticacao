package com.eickrono.api.identidade.aplicacao.modelo;

public record VinculoSocialConfirmadoCadastro(
        String provedor,
        String identificadorExterno,
        String nomeUsuarioExterno,
        String email,
        String nomeCompleto,
        String urlAvatarExterno,
        boolean avatarPreferido
) {
}
