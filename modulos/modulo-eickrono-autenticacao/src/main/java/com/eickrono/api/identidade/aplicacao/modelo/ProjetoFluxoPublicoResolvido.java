package com.eickrono.api.identidade.aplicacao.modelo;

public record ProjetoFluxoPublicoResolvido(
        Long clienteEcossistemaId,
        String codigoProjeto,
        String nomeProjeto,
        String tipoProdutoExibicao,
        String produtoExibicao,
        String canalExibicao,
        boolean exigeValidacaoTelefone
) {
}
