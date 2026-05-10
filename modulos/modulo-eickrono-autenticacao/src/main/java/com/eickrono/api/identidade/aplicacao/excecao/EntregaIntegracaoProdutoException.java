package com.eickrono.api.identidade.aplicacao.excecao;

public class EntregaIntegracaoProdutoException extends RuntimeException {

    private final String codigoErro;
    private final boolean passivelNovaTentativa;

    public EntregaIntegracaoProdutoException(final String codigoErro,
                                             final String mensagem,
                                             final boolean passivelNovaTentativa) {
        super(mensagem);
        this.codigoErro = codigoErro;
        this.passivelNovaTentativa = passivelNovaTentativa;
    }

    public EntregaIntegracaoProdutoException(final String codigoErro,
                                             final String mensagem,
                                             final boolean passivelNovaTentativa,
                                             final Throwable causa) {
        super(mensagem, causa);
        this.codigoErro = codigoErro;
        this.passivelNovaTentativa = passivelNovaTentativa;
    }

    public String getCodigoErro() {
        return codigoErro;
    }

    public boolean isPassivelNovaTentativa() {
        return passivelNovaTentativa;
    }
}
