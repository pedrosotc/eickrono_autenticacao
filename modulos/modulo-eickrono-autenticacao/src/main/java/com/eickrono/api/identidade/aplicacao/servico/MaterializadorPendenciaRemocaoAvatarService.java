package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.apresentacao.dto.admin.BloqueioExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ItemPlanoExclusaoCadastroProdutoApiResposta;
import java.util.List;

public interface MaterializadorPendenciaRemocaoAvatarService {

    Resultado materializar(String correlacaoId, String produto, List<String> vinculosProdutoIds);

    record Resultado(
            List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoesExecutadas,
            List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
    ) {
        public Resultado {
            acoesExecutadas = acoesExecutadas == null ? List.of() : List.copyOf(acoesExecutadas);
            bloqueios = bloqueios == null ? List.of() : List.copyOf(bloqueios);
        }
    }
}
