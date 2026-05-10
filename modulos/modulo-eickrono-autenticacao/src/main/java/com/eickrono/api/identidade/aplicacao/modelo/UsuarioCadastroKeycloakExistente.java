package com.eickrono.api.identidade.aplicacao.modelo;

public record UsuarioCadastroKeycloakExistente(
        String subjectRemoto,
        String emailPrincipal,
        boolean emailVerificado,
        boolean habilitado,
        long createdTimestamp
) {
}
