package com.eickrono.api.identidade.apresentacao.dto.admin;

import java.util.List;

public record ExclusaoCadastroProdutoApiResposta(
        String correlacaoId,
        boolean dryRun,
        AlvosExclusaoCadastroProdutoApiResposta alvosResolvidos,
        List<ItemPlanoExclusaoCadastroProdutoApiResposta> acoes,
        List<ItemPlanoExclusaoCadastroProdutoApiResposta> preservados,
        List<BloqueioExclusaoCadastroProdutoApiResposta> bloqueios
) {
}
