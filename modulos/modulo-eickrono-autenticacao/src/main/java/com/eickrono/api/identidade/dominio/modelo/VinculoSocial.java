package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Vinculo de uma conta social com o perfil.
 */
@Entity
@Table(name = "vinculos_sociais")
public class VinculoSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", nullable = false)
    private PerfilIdentidade perfil;

    @Column(nullable = false)
    private String provedor;

    @Column(nullable = false)
    private String identificador;

    @Column(name = "vinculado_em", nullable = false)
    private OffsetDateTime vinculadoEm;

    @Column(name = "nome_exibicao_externo")
    private String nomeExibicaoExterno;

    @Column(name = "url_avatar_externo", length = 2048)
    private String urlAvatarExterno;

    @Column(name = "avatar_externo_atualizado_em")
    private OffsetDateTime avatarExternoAtualizadoEm;

    protected VinculoSocial() {
    }

    public VinculoSocial(PerfilIdentidade perfil, String provedor, String identificador, OffsetDateTime vinculadoEm) {
        this(perfil, provedor, identificador, vinculadoEm, null, null, null);
    }

    public VinculoSocial(PerfilIdentidade perfil,
                         String provedor,
                         String identificador,
                         OffsetDateTime vinculadoEm,
                         String nomeExibicaoExterno,
                         String urlAvatarExterno,
                         OffsetDateTime avatarExternoAtualizadoEm) {
        this.perfil = perfil;
        this.provedor = provedor;
        this.identificador = identificador;
        this.vinculadoEm = vinculadoEm;
        this.nomeExibicaoExterno = nomeExibicaoExterno;
        this.urlAvatarExterno = urlAvatarExterno;
        this.avatarExternoAtualizadoEm = avatarExternoAtualizadoEm;
    }

    public Long getId() {
        return id;
    }

    public PerfilIdentidade getPerfil() {
        return perfil;
    }

    public String getProvedor() {
        return provedor;
    }

    public String getIdentificador() {
        return identificador;
    }

    public OffsetDateTime getVinculadoEm() {
        return vinculadoEm;
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

    public void atualizarIdentificador(final String novoIdentificador) {
        this.identificador = Objects.requireNonNull(novoIdentificador, "identificador é obrigatório");
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
