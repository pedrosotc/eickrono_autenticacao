package com.eickrono.api.identidade.aplicacao.servico;

public interface SondagemOperacionalProdutoService {

    boolean produtoDisponivel(int timeoutMillis);
}
