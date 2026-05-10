package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evento offline reportado pelo aplicativo ao backend.
 */
@Entity
@Table(name = "eventos_offline_dispositivo")
public class EventoOfflineDispositivo {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id", nullable = false)
    private DispositivoIdentidade dispositivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "token_id")
    private TokenDispositivo token;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false)
    private TipoEventoOfflineDispositivo tipoEvento;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "ocorrido_em", nullable = false)
    private OffsetDateTime ocorridoEm;

    @Column(name = "registrado_em", nullable = false)
    private OffsetDateTime registradoEm;

    protected EventoOfflineDispositivo() {
        // Construtor do JPA.
    }

    public EventoOfflineDispositivo(UUID id,
                                    DispositivoIdentidade dispositivo,
                                    TokenDispositivo token,
                                    TipoEventoOfflineDispositivo tipoEvento,
                                    String detalhes,
                                    OffsetDateTime ocorridoEm,
                                    OffsetDateTime registradoEm) {
        this.id = Objects.requireNonNull(id, "id é obrigatório");
        this.dispositivo = Objects.requireNonNull(dispositivo, "dispositivo é obrigatório");
        this.token = token;
        this.tipoEvento = Objects.requireNonNull(tipoEvento, "tipoEvento é obrigatório");
        this.detalhes = detalhes;
        this.ocorridoEm = Objects.requireNonNull(ocorridoEm, "ocorridoEm é obrigatório");
        this.registradoEm = Objects.requireNonNull(registradoEm, "registradoEm é obrigatório");
    }

    public UUID getId() {
        return id;
    }

    public DispositivoIdentidade getDispositivo() {
        return dispositivo;
    }

    public Optional<TokenDispositivo> getToken() {
        return Optional.ofNullable(token);
    }

    public TipoEventoOfflineDispositivo getTipoEvento() {
        return tipoEvento;
    }

    public Optional<String> getDetalhes() {
        return Optional.ofNullable(detalhes);
    }

    public OffsetDateTime getOcorridoEm() {
        return ocorridoEm;
    }

    public OffsetDateTime getRegistradoEm() {
        return registradoEm;
    }
}
