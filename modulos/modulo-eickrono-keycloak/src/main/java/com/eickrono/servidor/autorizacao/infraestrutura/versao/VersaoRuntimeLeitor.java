package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public final class VersaoRuntimeLeitor {

    private static final String STATUS_OK = "ok";
    private static final String DESCONHECIDO = "desconhecida";
    private static final String RECURSO = "/eickrono-autenticacao-servidor-versao.properties";
    private static final String CHAVE_SERVICO = "servico";
    private static final String CHAVE_VERSAO = "versao";
    private static final String CHAVE_BUILD_TIME = "buildTime";

    private VersaoRuntimeLeitor() {
    }

    public static EstadoRuntimeResposta carregar() {
        Properties properties = new Properties();
        try (InputStream inputStream = VersaoRuntimeLeitor.class.getResourceAsStream(RECURSO)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
            // cai no fallback abaixo
        }
        return new EstadoRuntimeResposta(
                valorOuDesconhecido(properties.getProperty(CHAVE_SERVICO)),
                STATUS_OK,
                valorOuDesconhecido(properties.getProperty(CHAVE_VERSAO)),
                valorOuDesconhecido(properties.getProperty(CHAVE_BUILD_TIME))
        );
    }

    private static String valorOuDesconhecido(final String valor) {
        String valorNormalizado = Objects.requireNonNullElse(valor, "").trim();
        return valorNormalizado.isEmpty() ? DESCONHECIDO : valorNormalizado;
    }
}
