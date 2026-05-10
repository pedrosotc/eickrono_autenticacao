package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

/**
 * Porta de validacao da confianca do device token durante o refresh token.
 */
interface ValidadorRefreshDispositivo {

    ResultadoValidacaoRefreshDispositivo validar(String usuarioSub, String deviceToken);
}
