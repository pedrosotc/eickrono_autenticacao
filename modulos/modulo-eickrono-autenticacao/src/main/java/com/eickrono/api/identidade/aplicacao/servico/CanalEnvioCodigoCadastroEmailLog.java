package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanalEnvioCodigoCadastroEmailLog implements CanalEnvioCodigoCadastroEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoCadastroEmailLog.class);

    @Override
    public void enviar(final CadastroConta cadastroConta, final String codigo) {
        CadastroConta cadastro = Objects.requireNonNull(cadastroConta, "cadastroConta é obrigatório");
        LOGGER.info(
                "Enviando código de confirmação de cadastro para {} (cadastroId={}, sistema={}) - código={}",
                cadastro.getEmailPrincipal(),
                cadastro.getCadastroId(),
                cadastro.getSistemaSolicitante(),
                codigo
        );
    }
}
