package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;

public interface CanalEnvioCodigoCadastroEmail {

    void enviar(CadastroConta cadastroConta, String codigo);
}
