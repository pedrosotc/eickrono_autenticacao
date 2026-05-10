package com.eickrono.api.identidade.aplicacao.modelo;

import java.time.OffsetDateTime;

public record ControleIntegracaoProduto(
        long clienteEcossistemaId,
        boolean escritasInternasHabilitadas,
        boolean produtoEmManutencao,
        OffsetDateTime inicioManutencao,
        OffsetDateTime fimManutencao,
        String motivoManutencao,
        Integer tempoEntreTentativasSegundosOverride,
        Integer quantidadeMaximaTentativasOverride,
        Integer timeoutSondagemMillisOverride,
        Integer timeoutEntregaMillisOverride
) {
}
