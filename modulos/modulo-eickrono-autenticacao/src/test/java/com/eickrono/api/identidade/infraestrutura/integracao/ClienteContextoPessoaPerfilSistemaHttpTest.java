package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteContextoPessoaPerfilSistemaHttpTest {

    @Test
    void deveConverterUsuarioDoContextoRemoto() {
        ContextoPessoaPerfilSistema contexto = ClienteContextoPessoaPerfilSistemaHttp.converterResposta(
                new ClienteContextoPessoaPerfilSistemaHttp.ContextoPerfilSistemaRemotoResposta(
                        77L,
                        321L,
                        "sub-central-001",
                        "ana@eickrono.com",
                        "Ana",
                        "ana.souza",
                        "perfil-uuid-001",
                        "LIBERADO"
                )
        );

        assertThat(contexto.pessoaId()).isEqualTo(321L);
        assertThat(contexto.sub()).isEqualTo("sub-central-001");
        assertThat(contexto.emailPrincipal()).isEqualTo("ana@eickrono.com");
        assertThat(contexto.nome()).isEqualTo("Ana");
        assertThat(contexto.usuario()).isEqualTo("ana.souza");
        assertThat(contexto.perfilSistemaId()).isEqualTo("perfil-uuid-001");
        assertThat(contexto.statusPerfilSistema()).isEqualTo("LIBERADO");
    }
}
