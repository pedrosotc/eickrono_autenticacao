package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ProvisionamentoPerfilSistemaRealizado;
import com.eickrono.api.identidade.dominio.modelo.CadastroConta;

public interface ProvisionadorPerfilSistemaServico {

    ProvisionamentoPerfilSistemaRealizado provisionarCadastroConfirmado(CadastroConta cadastroConta, Long pessoaIdCentral);
}
