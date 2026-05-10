package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fornecedor de SMS de desenvolvimento que apenas escreve o envio no log.
 */
@Component
public class FornecedorEnvioSmsLog implements FornecedorEnvioSms {

    private static final Logger LOGGER = LoggerFactory.getLogger(FornecedorEnvioSmsLog.class);

    @Override
    public String identificador() {
        return "log";
    }

    @Override
    public void enviarSms(RegistroDispositivo registro, String destino, String codigo) {
        LOGGER.info("Enviando código SMS para {} (registro={}) - código={}", destino, registro.getId(), codigo);
    }
}
