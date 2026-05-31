package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CriarSessaoSocialApiRequest(
        @NotBlank String aplicacaoId,
        @NotBlank String provedor,
        @NotBlank String tokenExterno,
        String identificadorExterno,
        String email,
        String nomeUsuarioExterno,
        String nomeCompleto,
        String urlAvatarExterno,
        @Valid @NotNull DispositivoSessaoApiRequest dispositivo,
        @Valid @NotNull AtestacaoOperacaoApiRequest atestacao,
        @Valid @NotNull SegurancaAplicativoApiRequest segurancaAplicativo
) {
}
