package com.eickrono.api.identidade.apresentacao.dto.cadastro;

import com.eickrono.api.identidade.aplicacao.modelo.ConfirmacaoEmailCadastroInternoRealizada;
import java.util.UUID;

public record ConfirmacaoEmailCadastroInternoApiResposta(
        UUID cadastroId,
        String subjectRemoto,
        String emailPrincipal,
        boolean emailConfirmado,
        boolean podeAutenticar
) {

    public static ConfirmacaoEmailCadastroInternoApiResposta de(
            final ConfirmacaoEmailCadastroInternoRealizada confirmacao) {
        return new ConfirmacaoEmailCadastroInternoApiResposta(
                confirmacao.cadastroId(),
                confirmacao.subjectRemoto(),
                confirmacao.emailPrincipal(),
                confirmacao.emailConfirmado(),
                confirmacao.podeAutenticar()
        );
    }
}
