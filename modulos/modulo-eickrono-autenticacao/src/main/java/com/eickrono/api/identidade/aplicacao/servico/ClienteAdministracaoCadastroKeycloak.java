package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import java.time.LocalDate;
import java.util.Optional;

public interface ClienteAdministracaoCadastroKeycloak {

    CadastroKeycloakProvisionado criarUsuarioPendente(String nomeCompleto, String emailPrincipal, String senhaPura);

    void confirmarEmailEAtivarUsuario(String subjectRemoto, String nomeCompleto, LocalDate dataNascimento);

    void removerUsuarioPendente(String subjectRemoto);

    default void removerUsuario(final String subjectRemoto) {
        removerUsuarioPendente(subjectRemoto);
    }

    Optional<UsuarioCadastroKeycloakExistente> buscarUsuarioPorEmail(String emailPrincipal);

    void vincularIdentidadeFederada(String subjectRemoto, IdentidadeFederadaKeycloak identidadeFederada);

    void redefinirSenha(String subjectRemoto, String senhaPura);
}
