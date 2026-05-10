package com.eickrono.api.identidade.aplicacao.modelo;

public record ParametrosOperacionaisSchedulerIntegracaoProduto(
        boolean habilitado,
        int tempoEntreTentativasSegundos,
        int quantidadeMaximaTentativas,
        int quantidadeMaximaItensPorCiclo,
        int timeoutSondagemMillis,
        int timeoutEntregaMillis,
        int timeoutRecuperacaoProcessamentoSegundos
) {
}
