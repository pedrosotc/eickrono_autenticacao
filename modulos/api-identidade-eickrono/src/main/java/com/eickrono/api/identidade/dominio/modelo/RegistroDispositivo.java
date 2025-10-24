package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Entidade responsável por acompanhar o ciclo de vida do registro de um dispositivo móvel.
 */
@Entity
@Table(name = "registro_dispositivo")
public class RegistroDispositivo {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "usuario_sub")
    private String usuarioSub;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String telefone;

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
    private StatusRegistroDispositivo status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "confirmado_em")
    private OffsetDateTime confirmadoEm;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;

    @Column(name = "reenvios", nullable = false)
    private int reenvios;

    @OneToMany(mappedBy = "registro", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<CodigoVerificacao> codigos = new HashSet<>();

    protected RegistroDispositivo() {
        // Construtor usado pelo JPA.
    }

    public RegistroDispositivo(UUID id,
                               String usuarioSub,
                               String email,
                               String telefone,
                               String fingerprint,
                               String plataforma,
                               String versaoAplicativo,
                               String chavePublica,
                               StatusRegistroDispositivo status,
                               OffsetDateTime criadoEm,
                               OffsetDateTime expiraEm) {
        this.id = Objects.requireNonNull(id, "id é obrigatório");
        this.usuarioSub = usuarioSub;
        this.email = Objects.requireNonNull(email, "email é obrigatório");
        this.telefone = Objects.requireNonNull(telefone, "telefone é obrigatório");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint é obrigatório");
        this.plataforma = Objects.requireNonNull(plataforma, "plataforma é obrigatória");
        this.versaoAplicativo = versaoAplicativo;
        this.chavePublica = chavePublica;
        this.status = Objects.requireNonNull(status, "status é obrigatório");
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm é obrigatório");
        this.reenvios = 0;
    }

    public UUID getId() {
        return id;
    }

    public Optional<String> getUsuarioSub() {
        return Optional.ofNullable(usuarioSub);
    }

    public void definirUsuarioSub(String novoUsuarioSub) {
        this.usuarioSub = novoUsuarioSub;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
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

    public StatusRegistroDispositivo getStatus() {
        return status;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
    }

    public Optional<OffsetDateTime> getConfirmadoEm() {
        return Optional.ofNullable(confirmadoEm);
    }

    public Optional<OffsetDateTime> getCanceladoEm() {
        return Optional.ofNullable(canceladoEm);
    }

    public int getReenvios() {
        return reenvios;
    }

    public Set<CodigoVerificacao> getCodigos() {
        return Collections.unmodifiableSet(codigos);
    }

    public void adicionarCodigo(CodigoVerificacao codigo) {
        codigos.add(codigo);
        codigo.definirRegistro(this);
    }

    public Optional<CodigoVerificacao> codigoPorCanal(CanalVerificacao canal) {
        return codigos.stream()
                .filter(codigo -> codigo.getCanal() == canal)
                .findFirst();
    }

    public Map<CanalVerificacao, CodigoVerificacao> codigosPorCanal() {
        Map<CanalVerificacao, CodigoVerificacao> mapa = new EnumMap<>(CanalVerificacao.class);
        for (CodigoVerificacao codigo : codigos) {
            mapa.put(codigo.getCanal(), codigo);
        }
        return Collections.unmodifiableMap(mapa);
    }

    public void definirStatus(StatusRegistroDispositivo novoStatus, OffsetDateTime momento) {
        this.status = Objects.requireNonNull(novoStatus, "novoStatus é obrigatório");
        if (novoStatus == StatusRegistroDispositivo.CONFIRMADO) {
            this.confirmadoEm = momento;
        } else if (novoStatus == StatusRegistroDispositivo.EXPIRADO
                || novoStatus == StatusRegistroDispositivo.BLOQUEADO
                || novoStatus == StatusRegistroDispositivo.CANCELADO) {
            this.canceladoEm = momento;
        }
    }

    public void atualizarExpiracao(OffsetDateTime novaData) {
        this.expiraEm = Objects.requireNonNull(novaData, "novaData é obrigatória");
    }

    public void incrementarReenvios() {
        this.reenvios += 1;
    }
}
