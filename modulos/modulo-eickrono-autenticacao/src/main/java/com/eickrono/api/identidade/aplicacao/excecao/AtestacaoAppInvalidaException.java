package com.eickrono.api.identidade.aplicacao.excecao;

public class AtestacaoAppInvalidaException extends RuntimeException {

    private final String codigo;

    public AtestacaoAppInvalidaException(final String codigo, final String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
