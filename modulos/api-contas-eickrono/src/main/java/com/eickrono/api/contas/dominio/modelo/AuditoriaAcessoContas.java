package com.eickrono.api.contas.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Auditoria de acessos à API de contas.
 */
@Entity
@Table(name = "auditoria_acessos_contas")
public class AuditoriaAcessoContas {

    private static final int TAMANHO_MAXIMO_DETALHES = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sujeito;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "registrado_em", nullable = false)
    private OffsetDateTime registradoEm;

    @Column(length = TAMANHO_MAXIMO_DETALHES)
    private String detalhes;

    protected AuditoriaAcessoContas() {
    }

    public AuditoriaAcessoContas(String sujeito, String endpoint, OffsetDateTime registradoEm, String detalhes) {
        this.sujeito = sujeito;
        this.endpoint = endpoint;
        this.registradoEm = registradoEm;
        this.detalhes = detalhes;
    }

    public Long getId() {
        return id;
    }

    public String getSujeito() {
        return sujeito;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public OffsetDateTime getRegistradoEm() {
        return registradoEm;
    }

    public String getDetalhes() {
        return detalhes;
    }
}
