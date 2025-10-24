package com.eickrono.api.identidade.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job responsável por expirar registros de dispositivos pendentes.
 */
@Component
public class RegistroDispositivoScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistroDispositivoScheduler.class);

    private final RegistroDispositivoService registroDispositivoService;

    public RegistroDispositivoScheduler(RegistroDispositivoService registroDispositivoService) {
        this.registroDispositivoService = registroDispositivoService;
    }

    @Scheduled(fixedDelayString = "PT15M")
    public void executar() {
        LOGGER.debug("Iniciando verificação de registros de dispositivo pendentes");
        registroDispositivoService.expirarRegistrosPendentes();
    }
}
