package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import java.util.List;

public interface ResolvedorExclusaoCadastroProdutoService {

    boolean suporta(String produto);

    Resultado simular(String usuarioPublicoProduto, String perfilProdutoId);

    default ResultadoExecucao executar(
            final String usuarioPublicoProduto,
            final String perfilProdutoId,
            final String correlacaoId) {
        return new ResultadoExecucao(
                List.of(),
                List.of(new BloqueioExclusaoCadastroProdutoApiResposta(
                        "EICKRONO_PRODUTO",
                        "execucao_produto_nao_implementada",
                        "O resolvedor do produto ainda nao implementa execucao real."
                ))
        );
    }

    record Resultado(
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
            List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
    ) {
    }

    record ResultadoExecucao(
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoesExecutadas,
            List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
    ) {
    }
}
