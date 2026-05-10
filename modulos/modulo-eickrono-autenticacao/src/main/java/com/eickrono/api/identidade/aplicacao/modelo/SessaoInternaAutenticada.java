package com.eickrono.api.identidade.aplicacao.modelo;

public record SessaoInternaAutenticada(
        boolean autenticado,
        String tipoToken,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
