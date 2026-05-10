package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Identidade explícita do aparelho vinculada à pessoa.
 */
@Entity
@Table(
        name = "dispositivos_identidade",
        uniqueConstraints = @UniqueConstraint(name = "uk_dispositivos_identidade_usuario_fingerprint",
                columnNames = {"usuario_sub", "fingerprint"})
)
public class DispositivoIdentidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_sub", nullable = false)
    private String usuarioSub;

    @Column(name = "pessoa_id_perfil")
    private Long pessoaIdPerfil;

    @Column(nullable = false)
    private String fingerprint;

    @Column(nullable = false)
    private String plataforma;

    @Column(name = "versao_app")
    private String versaoAplicativo;

    @Column(name = "chave_publica")
    private String chavePublica;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusDispositivoIdentidade status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Column(name = "ultimo_token_emitido_em")
    private OffsetDateTime ultimoTokenEmitidoEm;

    protected DispositivoIdentidade() {
        // Construtor do JPA.
    }

    public DispositivoIdentidade(String usuarioSub,
                                 Long pessoaIdPerfil,
                                 String fingerprint,
                                 String plataforma,
                                 String versaoAplicativo,
                                 String chavePublica,
                                 StatusDispositivoIdentidade status,
                                 OffsetDateTime criadoEm,
                                 OffsetDateTime atualizadoEm) {
        this.usuarioSub = Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");
        this.pessoaIdPerfil = pessoaIdPerfil;
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint é obrigatório");
        this.plataforma = Objects.requireNonNull(plataforma, "plataforma é obrigatória");
        this.versaoAplicativo = versaoAplicativo;
        this.chavePublica = chavePublica;
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public DispositivoIdentidade(final Pessoa pessoa,
                                 final String fingerprint,
                                 final String plataforma,
                                 final String versaoAplicativo,
                                 final String chavePublica,
                                 final StatusDispositivoIdentidade status,
                                 final OffsetDateTime criadoEm,
                                 final OffsetDateTime atualizadoEm) {
        this(
                Objects.requireNonNull(pessoa, "pessoa é obrigatória").getSub(),
                pessoa.getId(),
                fingerprint,
                plataforma,
                versaoAplicativo,
                chavePublica,
                status,
                criadoEm,
                atualizadoEm
        );
    }

    public Long getId() {
        return id;
    }

    public String getUsuarioSub() {
        return usuarioSub;
    }

    public Optional<Long> getPessoaIdPerfil() {
        return Optional.ofNullable(pessoaIdPerfil);
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public String getPlataforma() {
        return plataforma;
    }

    public Optional<String> getVersaoAplicativo() {
        return Optional.ofNullable(versaoAplicativo);
    }

    public Optional<String> getChavePublica() {
        return Optional.ofNullable(chavePublica);
    }

    public StatusDispositivoIdentidade getStatus() {
        return status;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public Optional<OffsetDateTime> getUltimoTokenEmitidoEm() {
        return Optional.ofNullable(ultimoTokenEmitidoEm);
    }

    public void atualizarMetadados(String novaPlataforma,
                                   String novaVersaoAplicativo,
                                   String novaChavePublica,
                                   OffsetDateTime momento) {
        this.plataforma = Objects.requireNonNull(novaPlataforma, "plataforma é obrigatória");
        this.versaoAplicativo = novaVersaoAplicativo;
        this.chavePublica = novaChavePublica;
        this.atualizadoEm = Objects.requireNonNull(momento, "momento é obrigatório");
    }

    public void atualizarPessoaIdPerfil(final Long novaPessoaIdPerfil, final OffsetDateTime momento) {
        if (novaPessoaIdPerfil == null || Objects.equals(this.pessoaIdPerfil, novaPessoaIdPerfil)) {
            return;
        }
        this.pessoaIdPerfil = novaPessoaIdPerfil;
        this.atualizadoEm = Objects.requireNonNull(momento, "momento é obrigatório");
    }

    public void registrarTokenEmitido(OffsetDateTime momento) {
        OffsetDateTime instante = Objects.requireNonNull(momento, "momento é obrigatório");
        this.ultimoTokenEmitidoEm = instante;
        this.atualizadoEm = instante;
    }

    public boolean estaConfiavel() {
        return status == StatusDispositivoIdentidade.ATIVO;
    }
}
