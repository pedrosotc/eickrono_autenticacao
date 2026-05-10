package com.eickrono.api.identidade.apresentacao.dto.atestacao;

import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CriarDesafioAtestacaoApiRequest(
        @NotNull OperacaoAtestacaoApp operacao,
        @NotNull PlataformaAtestacaoApp plataforma,
        String usuarioSub,
        Long pessoaIdPerfil,
        UUID cadastroId,
        UUID registroDispositivoId
) {
}
