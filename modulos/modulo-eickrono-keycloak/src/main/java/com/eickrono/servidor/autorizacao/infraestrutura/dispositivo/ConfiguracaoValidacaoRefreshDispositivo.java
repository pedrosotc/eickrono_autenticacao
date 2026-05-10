package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import java.time.Duration;

/**
 * Configuracao de integracao com a API de identidade para validar device token no refresh.
 */
public record ConfiguracaoValidacaoRefreshDispositivo(
        String urlBaseIdentidade,
        String segredoInterno,
        String urlBaseKeycloak,
        String realmKeycloak,
        String clientIdInterno,
        String clientSecretInterno,
        boolean mtlsHabilitado,
        String mtlsKeystoreArquivo,
        String mtlsKeystoreSenha,
        String mtlsTruststoreArquivo,
        String mtlsTruststoreSenha,
        Duration timeout) {

    static ConfiguracaoValidacaoRefreshDispositivo fromEnvironment() {
        String urlBase = lerPrimeiroAmbienteDisponivel(
                new String[]{"EICKRONO_AUTENTICACAO_API_BASE_URL", "EICKRONO_IDENTIDADE_API_BASE_URL"},
                "http://eickrono-autenticacao:8081");
        String segredo = lerAmbiente("EICKRONO_INTERNAL_SECRET", "local-internal-secret");
        String urlBaseKeycloak = lerAmbiente("EICKRONO_KEYCLOAK_URL_BASE", "http://localhost:8080");
        String realmKeycloak = lerAmbiente("EICKRONO_KEYCLOAK_REALM", "eickrono");
        String clientIdInterno = lerPrimeiroAmbienteDisponivel(
                new String[]{"EICKRONO_AUTENTICACAO_CLIENT_ID_INTERNO", "EICKRONO_IDENTIDADE_CLIENT_ID_INTERNO"},
                "eickrono-keycloak");
        String clientSecretInterno = lerPrimeiroAmbienteDisponivel(
                new String[]{"EICKRONO_AUTENTICACAO_CLIENT_SECRET_INTERNO", "EICKRONO_IDENTIDADE_CLIENT_SECRET_INTERNO"},
                "CHANGE_ME");
        boolean mtlsHabilitado = Boolean.parseBoolean(lerAmbiente("EICKRONO_INTERNO_MTLS_HABILITADO", "false"));
        String mtlsKeystoreArquivo = lerAmbiente("EICKRONO_INTERNO_MTLS_KEYSTORE_ARQUIVO", "");
        String mtlsKeystoreSenha = lerAmbiente("EICKRONO_INTERNO_MTLS_KEYSTORE_SENHA", "");
        String mtlsTruststoreArquivo = lerAmbiente("EICKRONO_INTERNO_MTLS_TRUSTSTORE_ARQUIVO", "");
        String mtlsTruststoreSenha = lerAmbiente("EICKRONO_INTERNO_MTLS_TRUSTSTORE_SENHA", "");
        String timeoutMs = lerPrimeiroAmbienteDisponivel(
                new String[]{"EICKRONO_AUTENTICACAO_TIMEOUT_MS", "EICKRONO_IDENTIDADE_TIMEOUT_MS"},
                "3000");
        return new ConfiguracaoValidacaoRefreshDispositivo(
                urlBase,
                segredo,
                urlBaseKeycloak,
                realmKeycloak,
                clientIdInterno,
                clientSecretInterno,
                mtlsHabilitado,
                mtlsKeystoreArquivo,
                mtlsKeystoreSenha,
                mtlsTruststoreArquivo,
                mtlsTruststoreSenha,
                Duration.ofMillis(Long.parseLong(timeoutMs)));
    }

    private static String lerAmbiente(String chave, String padrao) {
        String valor = System.getenv(chave);
        if (valor == null || valor.isBlank()) {
            valor = System.getProperty(chave, padrao);
        }
        return valor;
    }

    private static String lerPrimeiroAmbienteDisponivel(String[] chaves, String padrao) {
        for (String chave : chaves) {
            String valor = System.getenv(chave);
            if (valor == null || valor.isBlank()) {
                valor = System.getProperty(chave);
            }
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return padrao;
    }
}
