package com.eickrono.api.identidade.aplicacao.modelo;

public record CadastroKeycloakProvisionado(
        String subjectRemoto,
        String emailPrincipal,
        String nomeCompleto
) {
}
