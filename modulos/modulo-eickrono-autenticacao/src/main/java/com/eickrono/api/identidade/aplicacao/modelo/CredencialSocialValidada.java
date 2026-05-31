package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.Locale;
import org.springframework.util.StringUtils;

public record CredencialSocialValidada(
        String provedor,
        String identificadorExterno,
        String email,
        String nomeUsuarioExterno,
        String nomeCompleto,
        String urlAvatarExterno
) {

    public CredencialSocialValidada {
        provedor = normalizarObrigatorio(provedor, "provedor");
        identificadorExterno = normalizarObrigatorio(identificadorExterno, "identificadorExterno");
        email = normalizarEmail(email);
        nomeUsuarioExterno = normalizarOpcional(nomeUsuarioExterno);
        nomeCompleto = normalizarOpcional(nomeCompleto);
        urlAvatarExterno = normalizarOpcional(urlAvatarExterno);
    }

    private static String normalizarObrigatorio(final String valor, final String nomeCampo) {
        if (!StringUtils.hasText(valor)) {
            throw new IllegalArgumentException(nomeCampo + " é obrigatório");
        }
        return valor.trim();
    }

    private static String normalizarEmail(final String valor) {
        String normalizado = normalizarOpcional(valor);
        return normalizado == null ? null : normalizado.toLowerCase(Locale.ROOT);
    }

    private static String normalizarOpcional(final String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }
}
