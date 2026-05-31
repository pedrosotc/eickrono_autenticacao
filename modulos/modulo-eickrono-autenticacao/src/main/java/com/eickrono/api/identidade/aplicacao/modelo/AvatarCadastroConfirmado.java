package com.eickrono.api.identidade.aplicacao.modelo;

public record AvatarCadastroConfirmado(
        String origem,
        String urlAvatar,
        String storageKey,
        String nomeArquivo,
        String contentType,
        Long tamanhoBytes,
        String hashConteudo,
        String versao,
        String conteudoBase64,
        boolean preferido
) {
}
