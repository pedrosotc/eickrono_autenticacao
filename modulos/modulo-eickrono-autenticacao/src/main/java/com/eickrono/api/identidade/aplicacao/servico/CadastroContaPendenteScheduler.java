package com.eickrono.api.identidade.aplicacao.servico;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring gerencia o ciclo de vida do servico injetado; o scheduler apenas delega a chamada."
)
public class CadastroContaPendenteScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CadastroContaPendenteScheduler.class);

    private final CadastroContaInternaServico cadastroContaInternaServico;

    public CadastroContaPendenteScheduler(final CadastroContaInternaServico cadastroContaInternaServico) {
        this.cadastroContaInternaServico = cadastroContaInternaServico;
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void executar() {
        int removidos = cadastroContaInternaServico.expurgarCadastrosPendentesExpirados();
        if (removidos > 0) {
            LOGGER.info("Expurgo de cadastros pendentes removeu {} registro(s) PENDENTE_EMAIL.", removidos);
            return;
        }
        LOGGER.debug("Expurgo de cadastros pendentes concluído sem remoções.");
    }
}
