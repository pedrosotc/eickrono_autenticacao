package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import jakarta.validation.constraints.NotBlank;

public record AvatarCadastroConfirmadoApiRequest(
        @NotBlank String origem,
        String urlAvatar,
        String storageKey,
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,
        String hashConteudo,
        String versao,
        String conteudoBase64,
        Boolean preferido
) {
}
