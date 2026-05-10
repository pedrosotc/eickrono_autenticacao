package com.eickrono.api.identidade.dominio.modelo;

/**
 * Operações protegidas por prova de origem e integridade do app.
 */
public enum OperacaoAtestacaoApp {
    CADASTRO,
    CONFIRMACAO_CADASTRO,
    LOGIN,
    REFRESH_SESSAO,
    TROCA_SENHA,
    REDEFINICAO_SENHA,
    ALTERACAO_EMAIL,
    ALTERACAO_TELEFONE,
    VINCULACAO_REDE_SOCIAL,
    DESVINCULACAO_REDE_SOCIAL,
    ATIVACAO_BIOMETRIA,
    SUBSTITUICAO_DISPOSITIVO,
    REVOGACAO_SESSOES
}
