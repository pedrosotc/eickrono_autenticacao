package com.eickrono.api.identidade.apresentacao.dto;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta enviada após registrar um dispositivo.
 */
public record RegistroDispositivoResponse(UUID registroId,
                                          OffsetDateTime expiraEm,
                                          StatusRegistroDispositivo status,
                                          List<CanalVerificacao> canaisConfirmacao) {

    public RegistroDispositivoResponse {
        canaisConfirmacao = List.copyOf(canaisConfirmacao);
    }
}
