package com.eickrono.api.identidade.aplicacao.modelo;

public record PessoaCanonicaConfirmada(
        Long pessoaId,
        String sub,
        String emailPrincipal
) {
}
