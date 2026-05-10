package com.eickrono.api.identidade.dominio.modelo;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Provedores sociais suportados pela API de identidade.
 */
public enum ProvedorVinculoSocial {
    GOOGLE(true, true, "google"),
    APPLE(true, false, "apple"),
    FACEBOOK(false, true, "facebook"),
    LINKEDIN(true, true, "linkedin"),
    INSTAGRAM(false, true, "instagram");

    private final String aliasApi;
    private final boolean trustEmail;
    private final boolean suportaAvatarPerfil;

    ProvedorVinculoSocial(final boolean trustEmail,
                          final boolean suportaAvatarPerfil,
                          final String aliasApi) {
        this.trustEmail = trustEmail;
        this.suportaAvatarPerfil = suportaAvatarPerfil;
        this.aliasApi = aliasApi;
    }

    public String getAliasApi() {
        return aliasApi;
    }

    public String getAliasFormaAcesso() {
        return aliasApi.toUpperCase(Locale.ROOT);
    }

    public boolean confiaEmailDoProvedor() {
        return trustEmail;
    }

    public boolean suportaAvatarPerfil() {
        return suportaAvatarPerfil;
    }

    public static Optional<ProvedorVinculoSocial> fromAlias(final String alias) {
        if (alias == null || alias.isBlank()) {
            return Optional.empty();
        }
        String aliasNormalizado = alias.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(provedor -> provedor.aliasApi.equals(aliasNormalizado))
                .findFirst();
    }
}
