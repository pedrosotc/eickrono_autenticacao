package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Forma de acesso vinculada à pessoa, como e-mail/senha ou login social.
 */
@Entity
@Table(name = "pessoas_formas_acesso")
public class FormaAcesso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoFormaAcesso tipo;

    @Column(nullable = false)
    private String provedor;

    @Column(nullable = false)
    private String identificador;

    @Column(nullable = false)
    private boolean principal;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "verificado_em")
    private OffsetDateTime verificadoEm;

    @Column(name = "nome_exibicao_externo")
    private String nomeExibicaoExterno;

    @Column(name = "url_avatar_externo", length = 2048)
    private String urlAvatarExterno;

    @Column(name = "avatar_externo_atualizado_em")
    private OffsetDateTime avatarExternoAtualizadoEm;

    protected FormaAcesso() {
        // Construtor protegido para JPA.
    }

    public FormaAcesso(Pessoa pessoa, TipoFormaAcesso tipo, String provedor, String identificador,
                       boolean principal, OffsetDateTime criadoEm, OffsetDateTime verificadoEm) {
        this(pessoa, tipo, provedor, identificador, principal, criadoEm, verificadoEm, null, null, null);
    }

    public FormaAcesso(Pessoa pessoa, TipoFormaAcesso tipo, String provedor, String identificador,
                       boolean principal, OffsetDateTime criadoEm, OffsetDateTime verificadoEm,
                       String nomeExibicaoExterno, String urlAvatarExterno, OffsetDateTime avatarExternoAtualizadoEm) {
        this.pessoa = Objects.requireNonNull(pessoa, "pessoa é obrigatória");
        this.tipo = Objects.requireNonNull(tipo, "tipo é obrigatório");
        this.provedor = Objects.requireNonNull(provedor, "provedor é obrigatório");
        this.identificador = Objects.requireNonNull(identificador, "identificador é obrigatório");
        this.principal = principal;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.verificadoEm = verificadoEm;
        this.nomeExibicaoExterno = nomeExibicaoExterno;
        this.urlAvatarExterno = urlAvatarExterno;
        this.avatarExternoAtualizadoEm = avatarExternoAtualizadoEm;
    }

    public Long getId() {
        return id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public TipoFormaAcesso getTipo() {
        return tipo;
    }

    public String getProvedor() {
        return provedor;
    }

    public String getIdentificador() {
        return identificador;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getVerificadoEm() {
        return verificadoEm;
    }

    public String getNomeExibicaoExterno() {
        return nomeExibicaoExterno;
    }

    public String getUrlAvatarExterno() {
        return urlAvatarExterno;
    }

    public OffsetDateTime getAvatarExternoAtualizadoEm() {
        return avatarExternoAtualizadoEm;
    }

    public void atualizarIdentificador(String novoIdentificador, boolean novoPrincipal, OffsetDateTime novoVerificadoEm) {
        this.identificador = Objects.requireNonNull(novoIdentificador, "identificador é obrigatório");
        this.principal = novoPrincipal;
        this.verificadoEm = novoVerificadoEm;
    }

    public void atualizarDadosExternos(final String nomeExibicaoExterno,
                                       final String urlAvatarExterno,
                                       final OffsetDateTime atualizadoEm) {
        if (nomeExibicaoExterno != null && !nomeExibicaoExterno.isBlank()) {
            this.nomeExibicaoExterno = nomeExibicaoExterno.trim();
        }
        String urlNormalizada = normalizarOpcional(urlAvatarExterno);
        this.urlAvatarExterno = urlNormalizada;
        if (urlNormalizada != null) {
            this.avatarExternoAtualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
        }
    }

    private String normalizarOpcional(final String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
