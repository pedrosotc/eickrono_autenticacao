package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;

/**
 * Abstração para diferentes fornecedores de SMS.
 */
public interface FornecedorEnvioSms {

    String identificador();

    void enviarSms(RegistroDispositivo registro, String destino, String codigo);
}
