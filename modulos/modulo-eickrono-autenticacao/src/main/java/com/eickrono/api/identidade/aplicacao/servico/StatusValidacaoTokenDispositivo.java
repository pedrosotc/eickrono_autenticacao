package com.eickrono.api.identidade.aplicacao.servico;

/**
 * Estados possíveis ao validar um token de dispositivo.
 */
public enum StatusValidacaoTokenDispositivo {
    VALIDO("DEVICE_TOKEN_VALID", "Token de dispositivo valido"),
    AUSENTE("DEVICE_TOKEN_MISSING", "Cabecalho X-Device-Token e obrigatorio"),
    INVALIDO("DEVICE_TOKEN_INVALID", "Token de dispositivo invalido"),
    REVOGADO("DEVICE_TOKEN_REVOKED", "Token de dispositivo revogado"),
    EXPIRADO("DEVICE_TOKEN_EXPIRED", "Token de dispositivo expirado");

    private final String codigo;
    private final String mensagemPadrao;

    StatusValidacaoTokenDispositivo(String codigo, String mensagemPadrao) {
        this.codigo = codigo;
        this.mensagemPadrao = mensagemPadrao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getMensagemPadrao() {
        return mensagemPadrao;
    }
}
