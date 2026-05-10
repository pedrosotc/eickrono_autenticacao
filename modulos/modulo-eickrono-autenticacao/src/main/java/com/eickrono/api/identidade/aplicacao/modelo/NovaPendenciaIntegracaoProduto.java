package com.eickrono.api.identidade.aplicacao.modelo;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NovaPendenciaIntegracaoProduto(
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
        OffsetDateTime proximaTentativaEm,
        String codigoUltimoErro,
        String mensagemUltimoErro,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
}
