package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "recuperacoes_senha")
public class RecuperacaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fluxo_id", nullable = false, unique = true)
    private UUID fluxoId;

    @Column(name = "subject_remoto", length = 255)
    private String subjectRemoto;

    @Column(name = "email_principal", nullable = false, length = 255)
    private String emailPrincipal;

    @Column(name = "codigo_email_hash", nullable = false, length = 64)
    private String codigoEmailHash;

    @Column(name = "codigo_email_gerado_em", nullable = false)
    private OffsetDateTime codigoEmailGeradoEm;

    @Column(name = "codigo_email_expira_em", nullable = false)
    private OffsetDateTime codigoEmailExpiraEm;

    @Column(name = "protocolo_suporte", nullable = false, unique = true, length = 40)
    private String protocoloSuporte;

    @Column(name = "tentativas_confirmacao_email", nullable = false)
    private int tentativasConfirmacaoEmail;

    @Column(name = "reenvios_email", nullable = false)
    private int reenviosEmail;

    @Column(name = "codigo_confirmado_em")
    private OffsetDateTime codigoConfirmadoEm;

    @Column(name = "senha_redefinida_em")
    private OffsetDateTime senhaRedefinidaEm;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected RecuperacaoSenha() {
        // construtor do JPA
    }

    public RecuperacaoSenha(final UUID fluxoId,
                            final String subjectRemoto,
                            final String emailPrincipal,
                            final String codigoEmailHash,
                            final OffsetDateTime codigoEmailGeradoEm,
                            final OffsetDateTime codigoEmailExpiraEm,
                            final OffsetDateTime criadoEm,
                            final OffsetDateTime atualizadoEm) {
        this.fluxoId = Objects.requireNonNull(fluxoId, "fluxoId é obrigatório");
        this.subjectRemoto = subjectRemoto;
        this.emailPrincipal = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório");
        this.codigoEmailHash = Objects.requireNonNull(codigoEmailHash, "codigoEmailHash é obrigatório");
        this.codigoEmailGeradoEm = Objects.requireNonNull(codigoEmailGeradoEm, "codigoEmailGeradoEm é obrigatório");
        this.codigoEmailExpiraEm = Objects.requireNonNull(codigoEmailExpiraEm, "codigoEmailExpiraEm é obrigatório");
        this.protocoloSuporte = gerarProtocoloSuporte();
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
        this.tentativasConfirmacaoEmail = 0;
        this.reenviosEmail = 0;
    }

    public Long getId() {
        return id;
    }

    public UUID getFluxoId() {
        return fluxoId;
    }

    public String getSubjectRemoto() {
        return subjectRemoto;
    }

    public String getEmailPrincipal() {
        return emailPrincipal;
    }

    public String getCodigoEmailHash() {
        return codigoEmailHash;
    }

    public OffsetDateTime getCodigoEmailGeradoEm() {
        return codigoEmailGeradoEm;
    }

    public OffsetDateTime getCodigoEmailExpiraEm() {
        return codigoEmailExpiraEm;
    }

    public String getProtocoloSuporte() {
        return protocoloSuporte;
    }

    public int getTentativasConfirmacaoEmail() {
        return tentativasConfirmacaoEmail;
    }

    public int getReenviosEmail() {
        return reenviosEmail;
    }

    public OffsetDateTime getCodigoConfirmadoEm() {
        return codigoConfirmadoEm;
    }

    public OffsetDateTime getSenhaRedefinidaEm() {
        return senhaRedefinidaEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public boolean codigoExpirado(final OffsetDateTime agora) {
        return codigoEmailExpiraEm != null && agora != null && agora.isAfter(codigoEmailExpiraEm);
    }

    public boolean codigoJaConfirmado() {
        return codigoConfirmadoEm != null;
    }

    public boolean senhaJaRedefinida() {
        return senhaRedefinidaEm != null;
    }

    public boolean ultrapassouReenviosEmail(final int reenviosMaximos) {
        return reenviosEmail >= reenviosMaximos;
    }

    public boolean possuiDestinoReal() {
        return subjectRemoto != null && !subjectRemoto.isBlank();
    }

    public void registrarTentativaConfirmacao(final OffsetDateTime atualizadoEm) {
        tentativasConfirmacaoEmail++;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public void atualizarCodigoEmail(final String codigoEmailHash,
                                     final OffsetDateTime codigoEmailGeradoEm,
                                     final OffsetDateTime codigoEmailExpiraEm,
                                     final OffsetDateTime atualizadoEm) {
        this.codigoEmailHash = Objects.requireNonNull(codigoEmailHash, "codigoEmailHash é obrigatório");
        this.codigoEmailGeradoEm = Objects.requireNonNull(codigoEmailGeradoEm, "codigoEmailGeradoEm é obrigatório");
        this.codigoEmailExpiraEm = Objects.requireNonNull(codigoEmailExpiraEm, "codigoEmailExpiraEm é obrigatório");
        this.codigoConfirmadoEm = null;
        this.reenviosEmail++;
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public void marcarCodigoConfirmado(final OffsetDateTime atualizadoEm) {
        this.codigoConfirmadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
        this.atualizadoEm = atualizadoEm;
    }

    public void marcarSenhaRedefinida(final OffsetDateTime atualizadoEm) {
        this.senhaRedefinidaEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
        this.atualizadoEm = atualizadoEm;
    }

    @PrePersist
    void assegurarProtocoloSuporte() {
        if (protocoloSuporte == null || protocoloSuporte.isBlank()) {
            protocoloSuporte = gerarProtocoloSuporte();
        }
    }

    private static String gerarProtocoloSuporte() {
        return "rec-" + UUID.randomUUID().toString().replace("-", "");
    }
}
