package com.eickrono.api.identidade.apresentacao.dto.cadastro;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroInternoRealizado;
import java.util.UUID;

public record CadastroInternoApiResposta(
        UUID cadastroId,
        String subjectRemoto,
        String emailPrincipal,
        boolean verificacaoEmailObrigatoria
) {

    public static CadastroInternoApiResposta de(final CadastroInternoRealizado cadastro) {
        return new CadastroInternoApiResposta(
                cadastro.cadastroId(),
                cadastro.subjectRemoto(),
                cadastro.emailPrincipal(),
                cadastro.verificacaoEmailObrigatoria()
        );
    }
}
