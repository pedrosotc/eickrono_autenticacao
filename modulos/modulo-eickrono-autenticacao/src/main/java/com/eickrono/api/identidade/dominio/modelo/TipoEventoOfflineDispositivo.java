package com.eickrono.api.identidade.dominio.modelo;

/**
 * Eventos sincronizados pelo app ao reconciliar o modo offline.
 */
public enum TipoEventoOfflineDispositivo {
    MODO_OFFLINE_ATIVADO,
    MODO_OFFLINE_ENCERRADO,
    SESSAO_EXPIRADA_OFFLINE,
    ACESSO_OFFLINE_LIBERADO,
    RECONCILIACAO_REALIZADA,
    SOBREPOSICAO_DE_USO_REPORTADA
}
