package com.eickrono.api.identidade.apresentacao.dto.sessao;

import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;

public record SessaoInternaApiResposta(
        boolean autenticado,
        String tipoToken,
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    public static SessaoInternaApiResposta de(final SessaoInternaAutenticada sessao) {
        return new SessaoInternaApiResposta(
                sessao.autenticado(),
                sessao.tipoToken(),
                sessao.accessToken(),
                sessao.refreshToken(),
                sessao.expiresIn()
        );
    }
}
