package com.eickrono.api.identidade.dominio.modelo;

/**
 * Estados do ciclo de vida do cadastro nativo até a liberação para autenticação.
 */
public enum StatusCadastroConta {
    PENDENTE_EMAIL,
    EMAIL_CONFIRMADO
}
