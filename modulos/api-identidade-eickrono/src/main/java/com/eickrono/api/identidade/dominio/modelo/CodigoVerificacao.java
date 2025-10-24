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
 * Representa um código de verificação enviado por SMS ou e-mail.
 */
@Entity
@Table(name = "codigo_verificacao")
public class CodigoVerificacao {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_id", nullable = false)
    private RegistroDispositivo registro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalVerificacao canal;

    @Column(nullable = false)
    private String destino;

    @Column(name = "codigo_hash", nullable = false, length = 128)
    private String codigoHash;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "tentativas_maximas", nullable = false)
    private int tentativasMaximas;

    @Column(nullable = false)
    private int reenvios;

    @Column(name = "reenvios_maximos", nullable = false)
    private int reenviosMaximos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCodigoVerificacao status;

    @Column(name = "enviado_em")
    private OffsetDateTime enviadoEm;

    @Column(name = "confirmado_em")
    private OffsetDateTime confirmadoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    protected CodigoVerificacao() {
        // Construtor requerido pelo JPA.
    }

    public CodigoVerificacao(UUID id,
                             CanalVerificacao canal,
                             String destino,
                             String codigoHash,
                             int tentativasMaximas,
                             int reenviosMaximos,
                             StatusCodigoVerificacao status,
                             OffsetDateTime enviadoEm,
                             OffsetDateTime expiraEm) {
        this.id = Objects.requireNonNull(id, "id é obrigatório");
        this.canal = Objects.requireNonNull(canal, "canal é obrigatório");
        this.destino = Objects.requireNonNull(destino, "destino é obrigatório");
        this.codigoHash = Objects.requireNonNull(codigoHash, "codigoHash é obrigatório");
        this.tentativasMaximas = tentativasMaximas;
        this.reenviosMaximos = reenviosMaximos;
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.enviadoEm = enviadoEm;
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
        this.tentativas = 0;
        this.reenvios = 0;
    }

    public UUID getId() {
        return id;
    }

    public CanalVerificacao getCanal() {
        return canal;
    }

    public String getDestino() {
        return destino;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public int getTentativas() {
        return tentativas;
    }

    public int getTentativasMaximas() {
        return tentativasMaximas;
    }

    public int getReenvios() {
        return reenvios;
    }

    public int getReenviosMaximos() {
        return reenviosMaximos;
    }

    public StatusCodigoVerificacao getStatus() {
        return status;
    }

    public Optional<OffsetDateTime> getEnviadoEm() {
        return Optional.ofNullable(enviadoEm);
    }

    public Optional<OffsetDateTime> getConfirmadoEm() {
        return Optional.ofNullable(confirmadoEm);
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public RegistroDispositivo getRegistro() {
        return registro;
    }

    void definirRegistro(RegistroDispositivo registroDispositivo) {
        this.registro = registroDispositivo;
    }

    public void atualizarCodigo(String novoHash, OffsetDateTime novoEnvio, OffsetDateTime novaExpiracao) {
        this.codigoHash = Objects.requireNonNull(novoHash, "novoHash é obrigatório");
        this.enviadoEm = Objects.requireNonNull(novoEnvio, "novoEnvio é obrigatório");
        this.expiraEm = Objects.requireNonNull(novaExpiracao, "novaExpiracao é obrigatória");
        this.tentativas = 0;
        this.reenvios += 1;
        this.status = StatusCodigoVerificacao.PENDENTE;
    }

    public void registrarTentativaInvalida() {
        this.tentativas += 1;
        if (tentativas >= tentativasMaximas) {
            this.status = StatusCodigoVerificacao.BLOQUEADO;
        }
    }

    public void marcarComoConfirmado(OffsetDateTime momento) {
        this.status = StatusCodigoVerificacao.CONFIRMADO;
        this.confirmadoEm = momento;
    }

    public void marcarComoExpirado() {
        this.status = StatusCodigoVerificacao.EXPIRADO;
    }

    public boolean ultrapassouReenvios() {
        return reenvios >= reenviosMaximos;
    }

    public boolean expirado(OffsetDateTime agora) {
        return expiraEm.isBefore(agora);
    }
}
