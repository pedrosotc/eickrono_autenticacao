package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;

public interface ExecutorPendenciaIntegracaoProdutoService {

    void entregar(PendenciaIntegracaoProduto pendencia, int timeoutEntregaMillis);
}
