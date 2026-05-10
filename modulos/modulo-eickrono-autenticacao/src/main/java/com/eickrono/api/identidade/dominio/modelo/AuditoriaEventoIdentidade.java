package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Registro de auditoria para eventos sensíveis da API de Identidade.
 */
@Entity
@Table(name = "auditoria_eventos_identidade")
public class AuditoriaEventoIdentidade {

    private static final int TAMANHO_MAXIMO_DETALHES = 2_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipoEvento;

    @Column(nullable = false)
    private String sujeito;

    @Column(nullable = false)
    private OffsetDateTime registradoEm;

    @Column(length = TAMANHO_MAXIMO_DETALHES)
    private String detalhes;

    protected AuditoriaEventoIdentidade() {
    }

    public AuditoriaEventoIdentidade(String tipoEvento, String sujeito, OffsetDateTime registradoEm, String detalhes) {
        this.tipoEvento = tipoEvento;
        this.sujeito = sujeito;
        this.registradoEm = registradoEm;
        this.detalhes = detalhes;
    }

    public Long getId() {
        return id;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getSujeito() {
        return sujeito;
    }

    public OffsetDateTime getRegistradoEm() {
        return registradoEm;
    }

    public String getDetalhes() {
        return detalhes;
    }
}
