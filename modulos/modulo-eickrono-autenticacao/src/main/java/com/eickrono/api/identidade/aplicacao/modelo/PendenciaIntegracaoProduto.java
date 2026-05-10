package com.eickrono.api.identidade.aplicacao.modelo;

import java.util.UUID;

public record PendenciaIntegracaoProduto(
        UUID id,
        long clienteEcossistemaId,
        String tipoOperacao,
        String uriEndpoint,
        String metodoHttp,
        String payloadJson,
        String idempotencyKey,
        String versaoContrato,
        UUID cadastroId,
        Long pessoaIdCentral,
        String perfilSistemaId,
        String identificadorPublicoSistema,
        String statusPendencia,
        int tentativasRealizadas
) {
}
