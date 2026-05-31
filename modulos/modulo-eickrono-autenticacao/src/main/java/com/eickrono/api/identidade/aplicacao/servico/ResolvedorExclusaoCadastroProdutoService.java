package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import java.util.List;

public interface ResolvedorExclusaoCadastroProdutoService {

    boolean suporta(String produto);

    Resultado simular(String usuarioPublicoProduto, String perfilProdutoId);

    record Resultado(
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
            List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
    ) {
    }
}
