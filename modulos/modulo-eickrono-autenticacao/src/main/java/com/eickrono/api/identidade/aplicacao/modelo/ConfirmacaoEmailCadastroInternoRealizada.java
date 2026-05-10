package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.UUID;

public record ConfirmacaoEmailCadastroInternoRealizada(
        UUID cadastroId,
        String subjectRemoto,
        String emailPrincipal,
        boolean emailConfirmado,
        boolean podeAutenticar
) {
}
