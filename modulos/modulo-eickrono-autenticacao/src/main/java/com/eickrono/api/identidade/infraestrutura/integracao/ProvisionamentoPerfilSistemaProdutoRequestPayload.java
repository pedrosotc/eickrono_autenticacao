package com.eickrono.api.identidade.infraestrutura.integracao;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import java.time.LocalDate;

public record ProvisionamentoPerfilSistemaProdutoRequestPayload(
        Long pessoaIdCentral,
        String cadastroId,
        String subPessoa,
        String tipoPessoa,
        String nomePessoaAtual,
        String nomeFantasiaPessoaAtual,
        String identificadorPublicoSistema,
        String sexo,
        String paisNascimento,
        LocalDate dataNascimento,
        String emailPessoaAtual,
        String telefonePessoaAtual,
        String canalValidacaoTelefone
) {

    public static ProvisionamentoPerfilSistemaProdutoRequestPayload fromCadastro(final CadastroConta cadastroConta,
                                                                                 final Long pessoaIdCentral) {
        return new ProvisionamentoPerfilSistemaProdutoRequestPayload(
                pessoaIdCentral,
                cadastroConta.getCadastroId().toString(),
                cadastroConta.getSubjectRemoto(),
                cadastroConta.getTipoPessoa().name(),
                cadastroConta.getNomeCompleto(),
                cadastroConta.getNomeFantasia(),
                cadastroConta.getUsuario(),
                cadastroConta.getSexo() == null ? null : cadastroConta.getSexo().name(),
                cadastroConta.getPaisNascimento(),
                cadastroConta.getDataNascimento(),
                cadastroConta.getEmailPrincipal(),
                cadastroConta.getTelefonePrincipal(),
                cadastroConta.getCanalValidacaoTelefone() == null
                        ? null
                        : cadastroConta.getCanalValidacaoTelefone().name()
        );
    }
}
