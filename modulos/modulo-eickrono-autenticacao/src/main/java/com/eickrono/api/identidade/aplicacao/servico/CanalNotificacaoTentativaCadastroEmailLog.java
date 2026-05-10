package com.eickrono.api.identidade.aplicacao.servico;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanalNotificacaoTentativaCadastroEmailLog implements CanalNotificacaoTentativaCadastroEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalNotificacaoTentativaCadastroEmailLog.class);

    @Override
    public void notificar(final String emailPrincipal) {
        String email = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório").trim();
        if (email.isBlank()) {
            throw new IllegalArgumentException("emailPrincipal é obrigatório");
        }
        LOGGER.info(
                "Tentativa de cadastro detectada para e-mail já vinculado: {}. "
                        + "Um aviso neutro deveria ser enviado ao titular do endereço.",
                email
        );
    }
}
