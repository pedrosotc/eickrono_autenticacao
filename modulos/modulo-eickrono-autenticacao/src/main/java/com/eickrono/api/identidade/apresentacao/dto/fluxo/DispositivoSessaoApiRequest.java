package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record DispositivoSessaoApiRequest(
        @NotBlank String plataforma,
        String aplicacaoId,
        String identificadorInstalacao,
        String modelo,
        String fabricante,
        String sistemaOperacional,
        String versaoSistema,
        String versaoApp
) {
}
