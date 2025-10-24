package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão que apenas loga o envio de código por e-mail.
 */
@Component
public class CanalEnvioCodigoEmailLog implements CanalEnvioCodigo {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoEmailLog.class);

    @Override
    public CanalVerificacao canal() {
        return CanalVerificacao.EMAIL;
    }

    @Override
    public void enviar(RegistroDispositivo registro, String destino, String codigo) {
        LOGGER.info("Enviando código E-mail para {} (registro={}) - código={}", destino, registro.getId(), codigo);
    }
}
