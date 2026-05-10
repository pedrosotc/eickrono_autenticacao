package com.eickrono.api.identidade.aplicacao.modelo;

public record ParametrosPersistidosSchedulerIntegracaoProduto(
        boolean habilitado,
        int tempoEntreTentativasSegundos,
        int quantidadeMaximaTentativas,
        int quantidadeMaximaItensPorCiclo,
        int timeoutSondagemMillis,
        int timeoutEntregaMillis
) {
}
