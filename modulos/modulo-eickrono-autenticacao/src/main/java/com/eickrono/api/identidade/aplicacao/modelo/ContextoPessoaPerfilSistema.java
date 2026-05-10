package com.eickrono.api.identidade.aplicacao.modelo;

public record ContextoPessoaPerfilSistema(
        Long pessoaId,
        String sub,
        String emailPrincipal,
        String nome,
        String perfilSistemaId,
        String statusPerfilSistema
) {
}
