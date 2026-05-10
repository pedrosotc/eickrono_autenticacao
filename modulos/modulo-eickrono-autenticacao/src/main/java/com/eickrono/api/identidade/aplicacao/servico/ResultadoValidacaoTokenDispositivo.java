package com.eickrono.api.identidade.aplicacao.servico;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Resultado rico da validacao de um token de dispositivo.
 */
public record ResultadoValidacaoTokenDispositivo(
        StatusValidacaoTokenDispositivo status,
        OffsetDateTime expiraEm) {

    public boolean valido() {
        return status == StatusValidacaoTokenDispositivo.VALIDO;
    }

    public String codigo() {
        return status.getCodigo();
    }

    public String mensagem() {
        return status.getMensagemPadrao();
    }

    public Optional<OffsetDateTime> expiraEmOpt() {
        return Optional.ofNullable(expiraEm);
    }
}
