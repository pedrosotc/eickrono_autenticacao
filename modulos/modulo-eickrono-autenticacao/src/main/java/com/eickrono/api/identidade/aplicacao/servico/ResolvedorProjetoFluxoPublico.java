package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;

public interface ResolvedorProjetoFluxoPublico {

    ProjetoFluxoPublicoResolvido resolverAtivo(String aplicacaoId);
}
