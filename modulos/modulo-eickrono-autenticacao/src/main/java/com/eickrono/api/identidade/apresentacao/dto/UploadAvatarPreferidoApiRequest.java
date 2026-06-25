package com.eickrono.api.identidade.apresentacao.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadAvatarPreferidoApiRequest(
        @NotBlank String aplicacaoId,
        String nomeArquivo,
        @NotBlank String contentType,
        Long tamanhoBytes,
        @NotBlank String conteudoBase64
) {
}
