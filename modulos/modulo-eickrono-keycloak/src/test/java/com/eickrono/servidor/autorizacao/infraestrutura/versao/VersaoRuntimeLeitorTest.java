package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class VersaoRuntimeLeitorTest {

    @Test
    void deveCarregarVersaoRuntimeDoClasspath() {
        EstadoRuntimeResposta estado = VersaoRuntimeLeitor.carregar();

        assertEquals("eickrono-autenticacao-servidor", estado.servico());
        assertEquals("ok", estado.status());
        assertFalse(estado.versao().isBlank());
        assertFalse(estado.buildTime().isBlank());
    }
}
