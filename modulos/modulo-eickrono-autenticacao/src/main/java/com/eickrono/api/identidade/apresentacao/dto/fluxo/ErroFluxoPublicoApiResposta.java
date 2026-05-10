package com.eickrono.api.identidade.apresentacao.dto.fluxo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ErroFluxoPublicoApiResposta(
        String codigo,
        String mensagem,
        Map<String, Object> detalhes
) {

    public ErroFluxoPublicoApiResposta {
        detalhes = detalhes == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
    }
}
