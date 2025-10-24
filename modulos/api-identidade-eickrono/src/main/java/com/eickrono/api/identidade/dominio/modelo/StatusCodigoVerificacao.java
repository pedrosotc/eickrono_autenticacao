package com.eickrono.api.identidade.dominio.modelo;

/**
 * Estados de ciclo de vida dos códigos de verificação.
 */
public enum StatusCodigoVerificacao {
    PENDENTE,
    CONFIRMADO,
    EXPIRADO,
    BLOQUEADO
}
