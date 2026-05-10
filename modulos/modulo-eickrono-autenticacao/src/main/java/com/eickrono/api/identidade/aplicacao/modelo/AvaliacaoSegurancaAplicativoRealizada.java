package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.List;

public record AvaliacaoSegurancaAplicativoRealizada(
        boolean bloqueada,
        boolean modoObservacao,
        int scoreRisco,
        List<String> sinaisCalculados
) {
}
