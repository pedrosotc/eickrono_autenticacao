package com.eickrono.api.identidade.aplicacao.modelo;

public record ResultadoEntregaIntegracaoProduto(
        boolean habilitado,
        int pendenciasRecuperadas,
        int pendenciasReservadas,
        int entregasConcluidas,
        int pendenciasReagendadas,
        int pendenciasEscaladas,
        int pendenciasPausadas
) {
}
