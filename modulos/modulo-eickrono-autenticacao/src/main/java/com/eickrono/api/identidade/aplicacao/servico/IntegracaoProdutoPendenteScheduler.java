package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ResultadoEntregaIntegracaoProduto;
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
public class IntegracaoProdutoPendenteScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegracaoProdutoPendenteScheduler.class);

    private final PendenciaIntegracaoProdutoService pendenciaIntegracaoProdutoService;

    public IntegracaoProdutoPendenteScheduler(
            final PendenciaIntegracaoProdutoService pendenciaIntegracaoProdutoService) {
        this.pendenciaIntegracaoProdutoService = pendenciaIntegracaoProdutoService;
    }

    @Scheduled(fixedDelayString = "${eickrono.integracao-produto.scheduler.intervalo-ciclo:PT1M}")
    public void executar() {
        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();
        if (!resultado.habilitado()) {
            LOGGER.debug("Scheduler de integracao com produto esta desabilitado.");
            return;
        }
        if (resultado.pendenciasRecuperadas() > 0
                || resultado.pendenciasReservadas() > 0
                || resultado.entregasConcluidas() > 0
                || resultado.pendenciasReagendadas() > 0
                || resultado.pendenciasEscaladas() > 0
                || resultado.pendenciasPausadas() > 0) {
            LOGGER.info(
                    "Scheduler de integracao com produto executado. recuperadas={}, reservadas={}, concluidas={}, reagendadas={}, escaladas={}, pausadas={}.",
                    resultado.pendenciasRecuperadas(),
                    resultado.pendenciasReservadas(),
                    resultado.entregasConcluidas(),
                    resultado.pendenciasReagendadas(),
                    resultado.pendenciasEscaladas(),
                    resultado.pendenciasPausadas()
            );
            return;
        }
        LOGGER.debug("Scheduler de integracao com produto executado sem pendencias prontas.");
    }
}
