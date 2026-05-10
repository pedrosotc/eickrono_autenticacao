package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class EstadoRuntimeRealmResourceProviderFactory implements RealmResourceProviderFactory {

    public static final String PROVIDER_ID = "eickrono-runtime";

    @Override
    public RealmResourceProvider create(final KeycloakSession session) {
        return new EstadoRuntimeRealmResourceProvider(session);
    }

    @Override
    public void init(final Scope config) {
        // sem inicializacao
    }

    @Override
    public void postInit(final KeycloakSessionFactory factory) {
        // sem pos-inicializacao
    }

    @Override
    public void close() {
        // sem recursos
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
