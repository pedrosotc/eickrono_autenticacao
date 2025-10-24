package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão que apenas registra em log o envio de SMS.
 * Em produção deve ser substituída por integração com o provedor oficial.
 */
@Component
public class CanalEnvioCodigoSmsLog implements CanalEnvioCodigo {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanalEnvioCodigoSmsLog.class);

    @Override
    public CanalVerificacao canal() {
        return CanalVerificacao.SMS;
    }

    @Override
    public void enviar(RegistroDispositivo registro, String destino, String codigo) {
        LOGGER.info("Enviando código SMS para {} (registro={}) - código={}", destino, registro.getId(), codigo);
    }
}
