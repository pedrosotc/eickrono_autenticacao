package com.eickrono.api.identidade.dominio.modelo;

/**
 * Estados possíveis para o registro de um dispositivo móvel.
 */
public enum StatusRegistroDispositivo {
    PENDENTE,
    CONFIRMADO,
    EXPIRADO,
    BLOQUEADO,
    CANCELADO
}
