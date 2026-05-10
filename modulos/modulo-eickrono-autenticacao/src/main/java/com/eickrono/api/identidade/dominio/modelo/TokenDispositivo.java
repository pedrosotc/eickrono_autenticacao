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
 * Token opaco emitido para um dispositivo autorizado.
 */
@Entity
@Table(name = "token_dispositivo")
public class TokenDispositivo {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_id", nullable = false)
    private RegistroDispositivo registro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispositivo_id")
    private DispositivoIdentidade dispositivo;

    @Column(name = "token_hash", nullable = false, length = 128, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusTokenDispositivo status;

    @Column(name = "emitido_em", nullable = false)
    private OffsetDateTime emitidoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "revogado_em")
    private OffsetDateTime revogadoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revogacao")
    private MotivoRevogacaoToken motivoRevogacao;

    protected TokenDispositivo() {
        // Construtor requerido pelo JPA.
    }

    public TokenDispositivo(UUID id,
                            RegistroDispositivo registro,
                            DispositivoIdentidade dispositivo,
                            String tokenHash,
                            StatusTokenDispositivo status,
                            OffsetDateTime emitidoEm,
                            OffsetDateTime expiraEm) {
        this.id = Objects.requireNonNull(id, "id é obrigatório");
        this.registro = Objects.requireNonNull(registro, "registro é obrigatório");
        this.dispositivo = dispositivo;
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash é obrigatório");
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.emitidoEm = Objects.requireNonNull(emitidoEm, "emitidoEm é obrigatório");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
    }

    public TokenDispositivo(final UUID id,
                            final RegistroDispositivo registro,
                            final DispositivoIdentidade dispositivo,
                            final String usuarioSub,
                            final String fingerprint,
                            final String plataforma,
                            final String versaoAplicativo,
                            final String tokenHash,
                            final StatusTokenDispositivo status,
                            final OffsetDateTime emitidoEm,
                            final OffsetDateTime expiraEm) {
        this(id, registro, dispositivo, tokenHash, status, emitidoEm, expiraEm);
    }

    public UUID getId() {
        return id;
    }

    public RegistroDispositivo getRegistro() {
        return registro;
    }

    public Optional<DispositivoIdentidade> getDispositivo() {
        return Optional.ofNullable(dispositivo);
    }

    public String getUsuarioSub() {
        return registro.getUsuarioSub().orElse(null);
    }

    public String getFingerprint() {
        return dispositivo != null ? dispositivo.getFingerprint() : registro.getFingerprint();
    }

    public String getPlataforma() {
        return dispositivo != null ? dispositivo.getPlataforma() : registro.getPlataforma();
    }

    public Optional<String> getVersaoAplicativo() {
        return dispositivo != null
                ? dispositivo.getVersaoAplicativo()
                : registro.getVersaoAplicativo();
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public StatusTokenDispositivo getStatus() {
        return status;
    }

    public OffsetDateTime getEmitidoEm() {
        return emitidoEm;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public Optional<OffsetDateTime> getRevogadoEm() {
        return Optional.ofNullable(revogadoEm);
    }

    public Optional<MotivoRevogacaoToken> getMotivoRevogacao() {
        return Optional.ofNullable(motivoRevogacao);
    }

    public boolean estaAtivo(OffsetDateTime agora) {
        return status == StatusTokenDispositivo.ATIVO && expiraEm.isAfter(agora);
    }

    public void revogar(MotivoRevogacaoToken motivo, OffsetDateTime momento) {
        this.status = StatusTokenDispositivo.REVOGADO;
        this.motivoRevogacao = motivo;
        this.revogadoEm = momento;
    }
}
