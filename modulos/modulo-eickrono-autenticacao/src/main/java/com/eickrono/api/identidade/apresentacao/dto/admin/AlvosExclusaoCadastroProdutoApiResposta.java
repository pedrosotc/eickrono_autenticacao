package com.eickrono.api.identidade.apresentacao.dto.admin;

import java.util.List;

public record AlvosExclusaoCadastroProdutoApiResposta(
        String produto,
        String usuarioPublicoProduto,
        String perfilProdutoId,
        List<String> usuariosAutenticacaoIds,
        List<String> vinculosProdutoIds
) {
}
