package com.eickrono.api.identidade.aplicacao.modelo;

public record ValidacaoAtestacaoAppConcluida(
        ValidacaoLocalAtestacaoAppResultado validacaoLocal,
        ValidacaoOficialAtestacaoAppResultado validacaoOficial,
        StatusValidacaoAtestacaoApp statusValidacao
) {
}
