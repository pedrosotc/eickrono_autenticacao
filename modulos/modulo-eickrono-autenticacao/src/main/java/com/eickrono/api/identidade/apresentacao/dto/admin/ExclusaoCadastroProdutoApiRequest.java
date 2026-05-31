package com.eickrono.api.identidade.apresentacao.dto.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ExclusaoCadastroProdutoApiRequest(
        @NotBlank String produto,
        String usuarioPublicoProduto,
        String perfilProdutoId,
        @AssertTrue boolean dryRun,
        @NotBlank String motivo
) {
}
