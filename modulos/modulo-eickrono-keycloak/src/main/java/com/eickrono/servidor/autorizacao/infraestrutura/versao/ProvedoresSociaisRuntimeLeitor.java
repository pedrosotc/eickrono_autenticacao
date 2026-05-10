package com.eickrono.servidor.autorizacao.infraestrutura.versao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderQuery;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public final class ProvedoresSociaisRuntimeLeitor {

    private static final List<String> ALIASES_TEMPORARIAMENTE_OCULTOS = List.of(
            "instagram",
            "linkedin"
    );

    private static final List<String> ALIASES_CANONICOS = List.of(
            "apple",
            "facebook",
            "google",
            "instagram",
            "linkedin",
            "x"
    );

    private static final Map<String, List<String>> CHAVES_CONFIG_OBRIGATORIAS = Map.of(
            "apple", List.of("clientId", "clientSecret"),
            "facebook", List.of("clientId", "clientSecret"),
            "google", List.of("clientId", "clientSecret"),
            "instagram", List.of("clientId", "clientSecret"),
            "linkedin", List.of("clientId", "clientSecret"),
            "x", List.of("clientId", "clientSecret")
    );

    private ProvedoresSociaisRuntimeLeitor() {
    }

    public static ProvedoresSociaisRuntimeResposta listar(final KeycloakSession session, final RealmModel realm) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(realm, "realm");
        return listar(session.identityProviders().getAllStream(IdentityProviderQuery.any()));
    }

    static ProvedoresSociaisRuntimeResposta listar(final Stream<IdentityProviderModel> provedores) {
        Objects.requireNonNull(provedores, "provedores");
        Map<String, IdentityProviderModel> porAlias = new LinkedHashMap<>();
        provedores.forEach(provedor -> {
            String aliasCanonico = normalizarAliasCanonico(provedor.getAlias());
            if (aliasCanonico != null) {
                porAlias.put(aliasCanonico, provedor);
            }
        });

        List<ProvedorSocialRuntimeResposta> resposta = ALIASES_CANONICOS.stream()
                .map(alias -> new ProvedorSocialRuntimeResposta(
                        alias,
                        calcularHabilitado(porAlias.get(alias), alias)))
                .toList();

        return new ProvedoresSociaisRuntimeResposta(resposta);
    }

    private static boolean calcularHabilitado(final IdentityProviderModel provedor, final String aliasCanonico) {
        if (ALIASES_TEMPORARIAMENTE_OCULTOS.contains(aliasCanonico)) {
            return false;
        }
        if (provedor == null) {
            return false;
        }
        if (!provedor.isEnabled()) {
            return false;
        }
        if (Boolean.TRUE.equals(provedor.isHideOnLogin())) {
            return false;
        }
        List<String> chavesObrigatorias = CHAVES_CONFIG_OBRIGATORIAS.getOrDefault(aliasCanonico, List.of());
        for (String chave : chavesObrigatorias) {
            String valor = normalizarTexto(provedor.getConfig().get(chave));
            if (valor == null || valor.startsWith("${") || valor.startsWith("trocar-")) {
                return false;
            }
        }
        return true;
    }

    private static String normalizarAliasCanonico(final String alias) {
        String aliasNormalizado = normalizarTexto(alias);
        if (aliasNormalizado == null) {
            return null;
        }
        return switch (aliasNormalizado) {
            case "apple", "facebook", "google", "instagram", "linkedin", "x" -> aliasNormalizado;
            default -> null;
        };
    }

    private static String normalizarTexto(final String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.isEmpty()) {
            return null;
        }
        return texto.toLowerCase(Locale.ROOT);
    }
}
