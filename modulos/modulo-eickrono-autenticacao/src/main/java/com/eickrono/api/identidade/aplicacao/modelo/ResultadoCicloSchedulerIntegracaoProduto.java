package com.eickrono.api.identidade.aplicacao.modelo;

public record ResultadoCicloSchedulerIntegracaoProduto(
        boolean habilitado,
        int pendenciasRecuperadas,
        long pendenciasProntas
) {
}
