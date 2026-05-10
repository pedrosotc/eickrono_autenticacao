package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import java.util.Optional;

public interface ClienteContextoPessoaPerfilSistema {

    Optional<ContextoPessoaPerfilSistema> buscarPorPessoaId(Long pessoaId);

    Optional<ContextoPessoaPerfilSistema> buscarPorSub(String sub);

    Optional<ContextoPessoaPerfilSistema> buscarPorEmail(String email);
}
