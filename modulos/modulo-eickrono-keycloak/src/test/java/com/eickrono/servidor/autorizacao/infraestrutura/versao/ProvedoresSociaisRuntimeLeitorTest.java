package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.keycloak.models.IdentityProviderModel;

class ProvedoresSociaisRuntimeLeitorTest {

    @Test
    void deveMarcarXComoHabilitadoQuandoAliasCanonicoEstiverConfigurado() {
        IdentityProviderModel x = novoProvedor("x", true, false, "clientId", "abc", "clientSecret", "segredo");

        ProvedoresSociaisRuntimeResposta resposta = ProvedoresSociaisRuntimeLeitor.listar(List.of(x).stream());

        assertTrue(resposta.provedores().stream()
                .filter(provedor -> provedor.alias().equals("x"))
                .findFirst()
                .orElseThrow()
                .habilitado());
    }

    @Test
    void deveMarcarXComoDesabilitadoQuandoCredencialAindaForPlaceholder() {
        IdentityProviderModel x = novoProvedor(
                "x",
                true,
                false,
                "clientId",
                "trocar-x-client-id",
                "clientSecret",
                "trocar-x-client-secret"
        );

        ProvedoresSociaisRuntimeResposta resposta = ProvedoresSociaisRuntimeLeitor.listar(List.of(x).stream());

        assertFalse(resposta.provedores().stream()
                .filter(provedor -> provedor.alias().equals("x"))
                .findFirst()
                .orElseThrow()
                .habilitado());
    }

    @Test
    void deveMarcarFacebookComoDesabilitadoQuandoOcultoNoLogin() {
        IdentityProviderModel facebook = novoProvedor(
                "facebook",
                true,
                true,
                "clientId",
                "facebook-client-id",
                "clientSecret",
                "facebook-client-secret"
        );

        ProvedoresSociaisRuntimeResposta resposta = ProvedoresSociaisRuntimeLeitor.listar(List.of(facebook).stream());

        assertFalse(resposta.provedores().stream()
                .filter(provedor -> provedor.alias().equals("facebook"))
                .findFirst()
                .orElseThrow()
                .habilitado());
    }

    @Test
    void deveManterInstagramComoDesabilitadoMesmoQuandoConfigurado() {
        IdentityProviderModel instagram = novoProvedor(
                "instagram",
                true,
                false,
                "clientId",
                "instagram-client-id",
                "clientSecret",
                "instagram-client-secret"
        );

        ProvedoresSociaisRuntimeResposta resposta = ProvedoresSociaisRuntimeLeitor.listar(List.of(instagram).stream());

        assertFalse(resposta.provedores().stream()
                .filter(provedor -> provedor.alias().equals("instagram"))
                .findFirst()
                .orElseThrow()
                .habilitado());
    }

    @Test
    void deveManterLinkedinComoDesabilitadoMesmoQuandoConfigurado() {
        IdentityProviderModel linkedin = novoProvedor(
                "linkedin",
                true,
                false,
                "clientId",
                "linkedin-client-id",
                "clientSecret",
                "linkedin-client-secret"
        );

        ProvedoresSociaisRuntimeResposta resposta = ProvedoresSociaisRuntimeLeitor.listar(List.of(linkedin).stream());

        assertFalse(resposta.provedores().stream()
                .filter(provedor -> provedor.alias().equals("linkedin"))
                .findFirst()
                .orElseThrow()
                .habilitado());
    }

    private static IdentityProviderModel novoProvedor(final String alias,
                                                      final boolean enabled,
                                                      final boolean hideOnLogin,
                                                      final String chave1,
                                                      final String valor1,
                                                      final String chave2,
                                                      final String valor2) {
        IdentityProviderModel provedor = new IdentityProviderModel();
        provedor.setAlias(alias);
        provedor.setEnabled(enabled);
        provedor.setHideOnLogin(hideOnLogin);
        provedor.setConfig(new java.util.LinkedHashMap<>(java.util.Map.of(
                chave1, valor1,
                chave2, valor2
        )));
        return provedor;
    }
}
