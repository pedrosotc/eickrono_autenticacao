package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.idm.ClientPolicyExecutorConfigurationRepresentation;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProvider;
import org.keycloak.services.clientpolicy.executor.ClientPolicyExecutorProviderFactory;

/**
 * Factory do executor que exige device token confiavel no refresh token.
 */
public final class RefreshTokenDispositivoExecutorFactory implements ClientPolicyExecutorProviderFactory {

    private ConfiguracaoValidacaoRefreshDispositivo configuracao;

    @Override
    public ClientPolicyExecutorProvider<ClientPolicyExecutorConfigurationRepresentation> create(
            KeycloakSession session) {
        return new RefreshTokenDispositivoExecutor(
                session,
                new ValidadorRefreshDispositivoHttp(configuracao));
    }

    @Override
    public void init(Config.Scope config) {
        configuracao = ConfiguracaoValidacaoRefreshDispositivo.fromEnvironment();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return RefreshTokenDispositivoExecutor.PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Bloqueia refresh token quando o device token estiver ausente, invalido, revogado ou expirado.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return true;
    }
}
