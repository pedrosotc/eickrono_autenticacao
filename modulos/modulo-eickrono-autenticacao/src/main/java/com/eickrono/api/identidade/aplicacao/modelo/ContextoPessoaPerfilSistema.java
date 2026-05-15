package com.eickrono.api.identidade.aplicacao.modelo;

public record ContextoPessoaPerfilSistema(
        Long pessoaId,
        String sub,
        String emailPrincipal,
        String nome,
        String usuario,
        String perfilSistemaId,
        String statusPerfilSistema
) {
    public ContextoPessoaPerfilSistema(final Long pessoaId,
                                       final String sub,
                                       final String emailPrincipal,
                                       final String nome,
                                       final String perfilSistemaId,
                                       final String statusPerfilSistema) {
        this(pessoaId, sub, emailPrincipal, nome, null, perfilSistemaId, statusPerfilSistema);
    }
}
