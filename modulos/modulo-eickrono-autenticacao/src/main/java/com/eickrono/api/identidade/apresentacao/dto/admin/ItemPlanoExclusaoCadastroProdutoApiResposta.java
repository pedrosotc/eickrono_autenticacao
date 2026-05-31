package com.eickrono.api.identidade.apresentacao.dto.admin;

public record ItemPlanoExclusaoCadastroProdutoApiResposta(
        String sistema,
        String tipo,
        String recurso,
        long quantidade
) {
}
