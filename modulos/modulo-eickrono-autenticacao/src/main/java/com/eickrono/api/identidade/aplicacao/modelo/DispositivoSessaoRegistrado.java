package com.eickrono.api.identidade.aplicacao.modelo;

import java.time.OffsetDateTime;

public record DispositivoSessaoRegistrado(
        String tokenDispositivo,
        OffsetDateTime tokenDispositivoExpiraEm
) {
}
