package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

public class EstadoRuntimeRealmResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public EstadoRuntimeRealmResourceProvider(final KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new EstadoRuntimeResource(session);
    }

    @Override
    public void close() {
        // nada a fechar
    }

    @Path("")
    public static class EstadoRuntimeResource {

        private final KeycloakSession session;

        public EstadoRuntimeResource(final KeycloakSession session) {
            this.session = session;
        }

        @GET
        @Path("estado")
        @Produces(MediaType.APPLICATION_JSON)
        public Response consultarEstado() {
            return Response.ok(VersaoRuntimeLeitor.carregar()).build();
        }

        @GET
        @Path("provedores-sociais")
        @Produces(MediaType.APPLICATION_JSON)
        public Response consultarProvedoresSociais() {
            return Response.ok(ProvedoresSociaisRuntimeLeitor.listar(
                    session,
                    session.getContext().getRealm()
            )).build();
        }
    }
}
