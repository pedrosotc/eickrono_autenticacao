package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cadastros_conta")
public class CadastroConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cadastro_id", nullable = false, unique = true)
    private UUID cadastroId;

    @Column(name = "subject_remoto", nullable = false, unique = true)
    private String subjectRemoto;

    @Column(name = "pessoa_id_perfil")
    private Long pessoaIdPerfil;

    @Column(name = "usuario_id_perfil", length = 36)
    private String perfilSistemaId;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 16)
    private TipoPessoaCadastro tipoPessoa;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "usuario", nullable = false, length = 255)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", length = 16)
    private SexoPessoaCadastro sexo;

    @Column(name = "pais_nascimento", length = 8)
    private String paisNascimento;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "email_principal", nullable = false, unique = true)
    private String emailPrincipal;

    @Column(name = "telefone_principal", length = 32)
    private String telefonePrincipal;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_validacao_telefone", length = 16)
    private CanalValidacaoTelefoneCadastro canalValidacaoTelefone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StatusCadastroConta status;

    @Column(name = "codigo_email_hash", nullable = false, length = 64)
    private String codigoEmailHash;

    @Column(name = "codigo_email_gerado_em", nullable = false)
    private OffsetDateTime codigoEmailGeradoEm;

    @Column(name = "codigo_email_expira_em", nullable = false)
    private OffsetDateTime codigoEmailExpiraEm;

    @Column(name = "protocolo_suporte", nullable = false, unique = true, length = 40)
    private String protocoloSuporte;

    @Column(name = "tentativas_confirmacao_email", nullable = false)
    private int tentativasConfirmacaoEmail;

    @Column(name = "reenvios_email", nullable = false)
    private int reenviosEmail;

    @Column(name = "email_confirmado_em")
    private OffsetDateTime emailConfirmadoEm;

    @Column(name = "sistema_solicitante", nullable = false, length = 64)
    private String sistemaSolicitante;

    @Column(name = "ip_solicitante", length = 64)
    private String ipSolicitante;

    @Column(name = "user_agent_solicitante", length = 512)
    private String userAgentSolicitante;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected CadastroConta() {
        // construtor do JPA
    }

    public CadastroConta(final UUID cadastroId,
                         final String subjectRemoto,
                         final TipoPessoaCadastro tipoPessoa,
                         final String nomeCompleto,
                         final String nomeFantasia,
                         final String usuario,
                         final SexoPessoaCadastro sexo,
                         final String paisNascimento,
                         final LocalDate dataNascimento,
                         final String emailPrincipal,
                         final String telefonePrincipal,
                         final CanalValidacaoTelefoneCadastro canalValidacaoTelefone,
                         final String codigoEmailHash,
                         final OffsetDateTime codigoEmailGeradoEm,
                         final OffsetDateTime codigoEmailExpiraEm,
                         final String sistemaSolicitante,
                         final String ipSolicitante,
                         final String userAgentSolicitante,
                         final OffsetDateTime criadoEm,
                         final OffsetDateTime atualizadoEm) {
        this.cadastroId = Objects.requireNonNull(cadastroId, "cadastroId é obrigatório");
        this.subjectRemoto = Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório");
        this.tipoPessoa = Objects.requireNonNull(tipoPessoa, "tipoPessoa é obrigatório");
        this.nomeCompleto = Objects.requireNonNull(nomeCompleto, "nomeCompleto é obrigatório");
        this.nomeFantasia = nomeFantasia;
        this.usuario = Objects.requireNonNull(usuario, "usuario é obrigatório");
        this.sexo = sexo;
        this.paisNascimento = paisNascimento;
        this.dataNascimento = dataNascimento;
        this.emailPrincipal = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório");
        this.telefonePrincipal = telefonePrincipal;
        this.canalValidacaoTelefone = canalValidacaoTelefone;
        this.status = StatusCadastroConta.PENDENTE_EMAIL;
        this.codigoEmailHash = Objects.requireNonNull(codigoEmailHash, "codigoEmailHash é obrigatório");
        this.codigoEmailGeradoEm = Objects.requireNonNull(codigoEmailGeradoEm, "codigoEmailGeradoEm é obrigatório");
        this.codigoEmailExpiraEm = Objects.requireNonNull(codigoEmailExpiraEm, "codigoEmailExpiraEm é obrigatório");
        this.protocoloSuporte = gerarProtocoloSuporte();
        this.tentativasConfirmacaoEmail = 0;
        this.reenviosEmail = 0;
        this.sistemaSolicitante = Objects.requireNonNull(sistemaSolicitante, "sistemaSolicitante é obrigatório");
        this.ipSolicitante = ipSolicitante;
        this.userAgentSolicitante = userAgentSolicitante;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public Long getId() {
        return id;
    }

    public UUID getCadastroId() {
        return cadastroId;
    }

    public String getSubjectRemoto() {
        return subjectRemoto;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public Long getPessoaIdPerfil() {
        return pessoaIdPerfil;
    }

    public String getPerfilSistemaId() {
        return perfilSistemaId;
    }

    public String getEmailPrincipal() {
        return emailPrincipal;
    }

    public TipoPessoaCadastro getTipoPessoa() {
        return tipoPessoa;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public String getUsuario() {
        return usuario;
    }

    public SexoPessoaCadastro getSexo() {
        return sexo;
    }

    public String getPaisNascimento() {
        return paisNascimento;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public String getTelefonePrincipal() {
        return telefonePrincipal;
    }

    public CanalValidacaoTelefoneCadastro getCanalValidacaoTelefone() {
        return canalValidacaoTelefone;
    }

    public StatusCadastroConta getStatus() {
        return status;
    }

    public String getCodigoEmailHash() {
        return codigoEmailHash;
    }

    public OffsetDateTime getCodigoEmailGeradoEm() {
        return codigoEmailGeradoEm;
    }

    public OffsetDateTime getCodigoEmailExpiraEm() {
        return codigoEmailExpiraEm;
    }

    public String getProtocoloSuporte() {
        return protocoloSuporte;
    }

    public int getTentativasConfirmacaoEmail() {
        return tentativasConfirmacaoEmail;
    }

    public int getReenviosEmail() {
        return reenviosEmail;
    }

    public OffsetDateTime getEmailConfirmadoEm() {
        return emailConfirmadoEm;
    }

    public String getSistemaSolicitante() {
        return sistemaSolicitante;
    }

    public String getIpSolicitante() {
        return ipSolicitante;
    }

    public String getUserAgentSolicitante() {
        return userAgentSolicitante;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    @PrePersist
    void assegurarProtocoloSuporte() {
        if (protocoloSuporte == null || protocoloSuporte.isBlank()) {
            protocoloSuporte = gerarProtocoloSuporte();
        }
    }

    private static String gerarProtocoloSuporte() {
        return "cad-" + UUID.randomUUID().toString().replace("-", "");
    }

    public boolean emailJaConfirmado() {
        return status == StatusCadastroConta.EMAIL_CONFIRMADO;
    }

    public boolean codigoEmailExpirado(final OffsetDateTime instante) {
        return codigoEmailExpiraEm.isBefore(Objects.requireNonNull(instante, "instante é obrigatório"));
    }

    public void registrarTentativaConfirmacao(final OffsetDateTime atualizadoEm) {
        this.tentativasConfirmacaoEmail += 1;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public void marcarEmailConfirmado(final OffsetDateTime confirmadoEm) {
        OffsetDateTime instante = Objects.requireNonNull(confirmadoEm, "confirmadoEm é obrigatório");
        this.status = StatusCadastroConta.EMAIL_CONFIRMADO;
        this.emailConfirmadoEm = instante;
        this.atualizadoEm = instante;
    }

    public boolean ultrapassouReenviosEmail(final int reenviosMaximos) {
        return reenviosEmail >= reenviosMaximos;
    }

    public void atualizarCodigoEmail(final String codigoEmailHash,
                                     final OffsetDateTime codigoEmailGeradoEm,
                                     final OffsetDateTime codigoEmailExpiraEm,
                                     final OffsetDateTime atualizadoEm) {
        this.codigoEmailHash = Objects.requireNonNull(codigoEmailHash, "codigoEmailHash é obrigatório");
        this.codigoEmailGeradoEm = Objects.requireNonNull(
                codigoEmailGeradoEm, "codigoEmailGeradoEm é obrigatório");
        this.codigoEmailExpiraEm = Objects.requireNonNull(
                codigoEmailExpiraEm, "codigoEmailExpiraEm é obrigatório");
        this.reenviosEmail += 1;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public void definirPessoaIdPerfil(final Long pessoaIdPerfil, final OffsetDateTime atualizadoEm) {
        this.pessoaIdPerfil = pessoaIdPerfil;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public void definirProvisionamentoPerfil(final Long pessoaIdPerfil,
                                             final String perfilSistemaId,
                                             final OffsetDateTime atualizadoEm) {
        this.pessoaIdPerfil = pessoaIdPerfil;
        this.perfilSistemaId = perfilSistemaId;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }
}
