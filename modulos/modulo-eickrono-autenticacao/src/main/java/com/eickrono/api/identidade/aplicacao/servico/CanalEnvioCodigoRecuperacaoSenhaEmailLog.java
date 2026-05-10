package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CanalEnvioCodigoRecuperacaoSenhaEmailLog implements CanalEnvioCodigoRecuperacaoSenhaEmail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoRecuperacaoSenhaEmailLog.class);

    @Override
    public void enviar(final RecuperacaoSenha recuperacaoSenha, final String codigo) {
        RecuperacaoSenha recuperacao = Objects.requireNonNull(recuperacaoSenha, "recuperacaoSenha é obrigatória");
        LOGGER.info(
                "Enviando código de recuperação de senha para {} (fluxoId={}) - código={}",
                recuperacao.getEmailPrincipal(),
                recuperacao.getFluxoId(),
                codigo
        );
    }
}
