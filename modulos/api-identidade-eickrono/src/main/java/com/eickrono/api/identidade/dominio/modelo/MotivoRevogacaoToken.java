package com.eickrono.api.identidade.dominio.modelo;

/**
 * Motivos padronizados para revogação de tokens de dispositivo.
 */
public enum MotivoRevogacaoToken {
    NOVO_DISPOSITIVO_CONFIRMANDO,
    SOLICITACAO_CLIENTE,
    EXPIRACAO
}
