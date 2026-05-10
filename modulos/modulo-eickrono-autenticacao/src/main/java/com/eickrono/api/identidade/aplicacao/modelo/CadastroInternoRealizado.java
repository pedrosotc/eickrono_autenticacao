package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.UUID;

public record CadastroInternoRealizado(
        UUID cadastroId,
        String subjectRemoto,
        String emailPrincipal,
        boolean verificacaoEmailObrigatoria
) {
}
