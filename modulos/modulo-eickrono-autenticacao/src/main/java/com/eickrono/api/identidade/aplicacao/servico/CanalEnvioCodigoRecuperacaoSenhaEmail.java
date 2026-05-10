package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;

public interface CanalEnvioCodigoRecuperacaoSenhaEmail {

    void enviar(RecuperacaoSenha recuperacaoSenha, String codigo);
}
