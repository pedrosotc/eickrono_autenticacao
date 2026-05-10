package com.eickrono.servidor.autorizacao.infraestrutura.versao;

public record EstadoRuntimeResposta(String servico, String status, String versao, String buildTime) {
}
