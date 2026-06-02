package com.eickrono.api.identidade.apresentacao.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record ExclusaoCadastroProdutoApiRequest(
        @NotBlank String produto,
        String usuarioPublicoProduto,
        String perfilProdutoId,
        boolean dryRun,
        @NotBlank String motivo,
        String correlacaoId
) {
}
