package com.eickrono.api.identidade.aplicacao.servico;

import java.util.Objects;
import org.springframework.util.StringUtils;

public final class DerivacaoSenhaKeycloak {

    private DerivacaoSenhaKeycloak() {
    }

    public static String derivar(final String senhaPura, final long createdTimestamp, final String pepper) {
        if (!StringUtils.hasText(pepper)) {
            throw new IllegalStateException("EICKRONO_PASSWORD_PEPPER é obrigatório para derivar a senha.");
        }
        if (createdTimestamp <= 0L) {
            throw new IllegalStateException("createdTimestamp é obrigatório para derivar a senha.");
        }
        return Objects.requireNonNull(senhaPura, "senhaPura é obrigatória")
                + pepper
                + Long.toString(createdTimestamp);
    }
}
