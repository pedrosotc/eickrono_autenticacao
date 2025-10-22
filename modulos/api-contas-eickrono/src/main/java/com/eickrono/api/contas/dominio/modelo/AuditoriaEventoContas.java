package com.eickrono.api.contas.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Eventos relevantes para auditoria de operações de contas.
 */
@Entity
@Table(name = "auditoria_eventos_contas")
public class AuditoriaEventoContas {

    private static final int TAMANHO_MAXIMO_DETALHES = 2_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(nullable = false)
    private String sujeito;

    @Column(name = "registrado_em", nullable = false)
    private OffsetDateTime registradoEm;

    @Column(length = TAMANHO_MAXIMO_DETALHES)
    private String detalhes;

    protected AuditoriaEventoContas() {
    }

    public AuditoriaEventoContas(String tipoEvento, String sujeito, OffsetDateTime registradoEm, String detalhes) {
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
