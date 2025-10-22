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

    protected VinculoSocial() {
    }

    public VinculoSocial(PerfilIdentidade perfil, String provedor, String identificador, OffsetDateTime vinculadoEm) {
        this.perfil = perfil;
        this.provedor = provedor;
        this.identificador = identificador;
        this.vinculadoEm = vinculadoEm;
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
}
