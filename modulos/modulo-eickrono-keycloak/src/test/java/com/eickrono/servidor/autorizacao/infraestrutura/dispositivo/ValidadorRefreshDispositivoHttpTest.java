package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class ValidadorRefreshDispositivoHttpTest {

    @Test
    void deveRetornarResultadoDaApiInterna() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/realms/eickrono/protocol/openid-connect/token", exchange -> {
            String body = "{\"access_token\":\"jwt-interno\",\"expires_in\":300}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body.getBytes());
            }
        });
        server.createContext("/api/conta/dispositivos/token/validacao/interna", exchange -> {
            assertEquals("Bearer jwt-interno", exchange.getRequestHeaders().getFirst("Authorization"));
            String body = "{\"valido\":false,\"codigo\":\"DEVICE_TOKEN_REVOKED\",\"mensagem\":\"revogado\"}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body.getBytes());
            }
        });
        server.start();
        try {
            ConfiguracaoValidacaoRefreshDispositivo configuracao = new ConfiguracaoValidacaoRefreshDispositivo(
                    "http://localhost:" + server.getAddress().getPort(),
                    "segredo",
                    "http://localhost:" + server.getAddress().getPort(),
                    "eickrono",
                    "eickrono-keycloak",
                    "segredo-client",
                    false,
                    "",
                    "",
                    "",
                    "",
                    java.time.Duration.ofSeconds(2));
            ValidadorRefreshDispositivoHttp validador = new ValidadorRefreshDispositivoHttp(
                    configuracao,
                    HttpClient.newHttpClient(),
                    new com.fasterxml.jackson.databind.ObjectMapper());

            ResultadoValidacaoRefreshDispositivo resultado = validador.validar("user-1", "device-1");

            assertEquals("DEVICE_TOKEN_REVOKED", resultado.codigo());
            assertTrue(!resultado.valido());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void deveFalharQuandoApiInternaResponderStatusNaoOk() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/realms/eickrono/protocol/openid-connect/token", exchange -> {
            String body = "{\"access_token\":\"jwt-interno\",\"expires_in\":300}";
            exchange.sendResponseHeaders(200, body.length());
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body.getBytes());
            }
        });
        server.createContext("/api/conta/dispositivos/token/validacao/interna", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            ConfiguracaoValidacaoRefreshDispositivo configuracao = new ConfiguracaoValidacaoRefreshDispositivo(
                    "http://localhost:" + server.getAddress().getPort(),
                    "segredo",
                    "http://localhost:" + server.getAddress().getPort(),
                    "eickrono",
                    "eickrono-keycloak",
                    "segredo-client",
                    false,
                    "",
                    "",
                    "",
                    "",
                    java.time.Duration.ofSeconds(2));
            ValidadorRefreshDispositivoHttp validador = new ValidadorRefreshDispositivoHttp(
                    configuracao,
                    HttpClient.newHttpClient(),
                    new com.fasterxml.jackson.databind.ObjectMapper());

            assertThrows(IllegalStateException.class, () -> validador.validar("user-1", "device-1"));
        } finally {
            server.stop(0);
        }
    }
}
