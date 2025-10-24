package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;

/**
 * Contrato para envio de código de verificação por canal específico.
 */
public interface CanalEnvioCodigo {

    CanalVerificacao canal();

    void enviar(RegistroDispositivo registro, String destino, String codigo);
}
