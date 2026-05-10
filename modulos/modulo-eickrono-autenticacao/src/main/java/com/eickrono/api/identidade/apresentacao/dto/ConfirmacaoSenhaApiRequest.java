package com.eickrono.api.identidade.apresentacao.dto;

public record ConfirmacaoSenhaApiRequest(
        String senhaConfirmacao
) {

    public String senhaObrigatoria() {
        return senhaConfirmacao == null ? "" : senhaConfirmacao;
    }
}
