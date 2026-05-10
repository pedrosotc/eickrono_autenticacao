package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vinculos_organizacionais")
public class VinculoOrganizacional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cadastro_id", nullable = false, unique = true)
    private UUID cadastroId;

    @Column(name = "pessoa_id_perfil", nullable = false)
    private Long pessoaIdPerfil;

    @Column(name = "usuario_id_perfil", nullable = false, length = 64)
    private String perfilSistemaId;

    @Column(name = "organizacao_id", nullable = false, length = 128)
    private String organizacaoId;

    @Column(name = "nome_organizacao", nullable = false, length = 255)
    private String nomeOrganizacao;

    @Column(name = "convite_codigo", nullable = false, length = 128)
    private String conviteCodigo;

    @Column(name = "email_convidado", length = 255)
    private String emailConvidado;

    @Column(name = "exige_conta_separada", nullable = false)
    private boolean exigeContaSeparada;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected VinculoOrganizacional() {
    }

    public VinculoOrganizacional(final UUID cadastroId,
                                 final Long pessoaIdPerfil,
                                 final String perfilSistemaId,
                                 final String organizacaoId,
                                 final String nomeOrganizacao,
                                 final String conviteCodigo,
                                 final String emailConvidado,
                                 final boolean exigeContaSeparada,
                                 final OffsetDateTime criadoEm) {
        this.cadastroId = Objects.requireNonNull(cadastroId, "cadastroId é obrigatório");
        this.pessoaIdPerfil = Objects.requireNonNull(pessoaIdPerfil, "pessoaIdPerfil é obrigatório");
        this.perfilSistemaId = Objects.requireNonNull(perfilSistemaId, "perfilSistemaId é obrigatório");
        this.organizacaoId = Objects.requireNonNull(organizacaoId, "organizacaoId é obrigatório");
        this.nomeOrganizacao = Objects.requireNonNull(nomeOrganizacao, "nomeOrganizacao é obrigatório");
        this.conviteCodigo = Objects.requireNonNull(conviteCodigo, "conviteCodigo é obrigatório");
        this.emailConvidado = emailConvidado;
        this.exigeContaSeparada = exigeContaSeparada;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.atualizadoEm = criadoEm;
    }

    public UUID getCadastroId() {
        return cadastroId;
    }

    public Long getPessoaIdPerfil() {
        return pessoaIdPerfil;
    }

    public String getPerfilSistemaId() {
        return perfilSistemaId;
    }

    public String getOrganizacaoId() {
        return organizacaoId;
    }

    public String getNomeOrganizacao() {
        return nomeOrganizacao;
    }

    public String getConviteCodigo() {
        return conviteCodigo;
    }

    public String getEmailConvidado() {
        return emailConvidado;
    }

    public boolean isExigeContaSeparada() {
        return exigeContaSeparada;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }
}
