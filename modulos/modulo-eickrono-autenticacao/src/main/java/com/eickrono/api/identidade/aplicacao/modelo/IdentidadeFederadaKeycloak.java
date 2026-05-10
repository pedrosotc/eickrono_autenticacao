package com.eickrono.api.identidade.aplicacao.modelo;

import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.util.Objects;

/**
 * Representa uma identidade federada vinculada ao usuário no Keycloak.
 */
public record IdentidadeFederadaKeycloak(
        ProvedorVinculoSocial provedor,
        String identificadorExterno,
        String nomeUsuarioExterno,
        String nomeExibicaoExterno,
        String urlAvatarExterno) {

    public IdentidadeFederadaKeycloak(
            final ProvedorVinculoSocial provedor, final String identificadorExterno, final String nomeUsuarioExterno) {
        this(provedor, identificadorExterno, nomeUsuarioExterno, null, null);
    }

    public IdentidadeFederadaKeycloak {
        Objects.requireNonNull(provedor, "provedor é obrigatório");
        identificadorExterno = normalizarOpcional(identificadorExterno);
        nomeUsuarioExterno = normalizarOpcional(nomeUsuarioExterno);
        nomeExibicaoExterno = normalizarOpcional(nomeExibicaoExterno);
        urlAvatarExterno = normalizarOpcional(urlAvatarExterno);
    }

    public String identificadorCanonico() {
        if (identificadorExterno != null && !identificadorExterno.isBlank()) {
            return identificadorExterno.trim();
        }
        if (nomeUsuarioExterno != null && !nomeUsuarioExterno.isBlank()) {
            return nomeUsuarioExterno.trim();
        }
        throw new IllegalStateException("Identidade federada sem identificador utilizável");
    }

    public String identificadorExibicao() {
        if (nomeUsuarioExterno != null && !nomeUsuarioExterno.isBlank()) {
            return nomeUsuarioExterno.trim();
        }
        return identificadorCanonico();
    }

    public String nomeExibicaoResolvido() {
        if (nomeExibicaoExterno != null && !nomeExibicaoExterno.isBlank()) {
            return nomeExibicaoExterno.trim();
        }
        return identificadorExibicao();
    }

    private static String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
