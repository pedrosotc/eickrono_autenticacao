package com.eickrono.api.identidade.apresentacao.dto;

import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import java.time.OffsetDateTime;

/**
 * Evento offline reportado pelo aplicativo.
 */
public record EventoOfflineDispositivoRequest(
        TipoEventoOfflineDispositivo tipoEvento,
        OffsetDateTime ocorridoEm,
        String detalhes
) {
}
