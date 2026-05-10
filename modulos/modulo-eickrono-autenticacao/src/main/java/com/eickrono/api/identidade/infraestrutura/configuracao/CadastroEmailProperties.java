package com.eickrono.api.identidade.infraestrutura.configuracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identidade.cadastro.email")
public class CadastroEmailProperties {

    private String fornecedor = "log";
    private String remetente = "no-reply@eickrono.local";
    private String responderPara;
    private String assunto = "Codigo de confirmacao do cadastro";
    private String assuntoRecuperacaoSenha = "Codigo de recuperacao de senha";
    private String assuntoTentativaCadastroEmailExistente = "Tentativa de cadastro com este e-mail";
    private String nomeAplicacao = "Eickrono";

    public String getFornecedor() {
        return fornecedor;
    }

    public void setFornecedor(final String fornecedor) {
        this.fornecedor = fornecedor;
    }

    public String getRemetente() {
        return remetente;
    }

    public void setRemetente(final String remetente) {
        this.remetente = remetente;
    }

    public String getResponderPara() {
        return responderPara;
    }

    public void setResponderPara(final String responderPara) {
        this.responderPara = responderPara;
    }

    public String getAssunto() {
        return assunto;
    }

    public void setAssunto(final String assunto) {
        this.assunto = assunto;
    }

    public String getNomeAplicacao() {
        return nomeAplicacao;
    }

    public void setNomeAplicacao(final String nomeAplicacao) {
        this.nomeAplicacao = nomeAplicacao;
    }

    public String getAssuntoRecuperacaoSenha() {
        return assuntoRecuperacaoSenha;
    }

    public void setAssuntoRecuperacaoSenha(final String assuntoRecuperacaoSenha) {
        this.assuntoRecuperacaoSenha = assuntoRecuperacaoSenha;
    }

    public String getAssuntoTentativaCadastroEmailExistente() {
        return assuntoTentativaCadastroEmailExistente;
    }

    public void setAssuntoTentativaCadastroEmailExistente(final String assuntoTentativaCadastroEmailExistente) {
        this.assuntoTentativaCadastroEmailExistente = assuntoTentativaCadastroEmailExistente;
    }
}
