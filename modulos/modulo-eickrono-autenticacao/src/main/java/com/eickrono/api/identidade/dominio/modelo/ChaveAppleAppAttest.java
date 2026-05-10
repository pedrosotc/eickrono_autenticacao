package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "apple_app_attest_chaves")
public class ChaveAppleAppAttest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_id", nullable = false, unique = true, length = 128)
    private String chaveId;

    @Column(name = "objeto_atestacao_base64", nullable = false, columnDefinition = "TEXT")
    private String objetoAtestacaoBase64;

    @Column(name = "contador_assinatura", nullable = false)
    private long contadorAssinatura;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected ChaveAppleAppAttest() {
        // Construtor do JPA.
    }

    public ChaveAppleAppAttest(final String chaveId,
                               final String objetoAtestacaoBase64,
                               final long contadorAssinatura,
                               final OffsetDateTime criadoEm,
                               final OffsetDateTime atualizadoEm) {
        this.chaveId = Objects.requireNonNull(chaveId, "chaveId é obrigatório");
        this.objetoAtestacaoBase64 = Objects.requireNonNull(
                objetoAtestacaoBase64, "objetoAtestacaoBase64 é obrigatório");
        this.contadorAssinatura = contadorAssinatura;
        this.criadoEm = Objects.requireNonNull(criadoEm, "criadoEm é obrigatório");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
    }

    public Long getId() {
        return id;
    }

    public String getChaveId() {
        return chaveId;
    }

    public String getObjetoAtestacaoBase64() {
        return objetoAtestacaoBase64;
    }

    public long getContadorAssinatura() {
        return contadorAssinatura;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void atualizarRegistroAtestacao(final String novoObjetoAtestacaoBase64,
                                           final long novoContadorAssinatura,
                                           final OffsetDateTime novoAtualizadoEm) {
        this.objetoAtestacaoBase64 = Objects.requireNonNull(
                novoObjetoAtestacaoBase64, "novoObjetoAtestacaoBase64 é obrigatório");
        this.contadorAssinatura = novoContadorAssinatura;
        this.atualizadoEm = Objects.requireNonNull(novoAtualizadoEm, "novoAtualizadoEm é obrigatório");
    }

    public void atualizarContadorAssinatura(final long novoContadorAssinatura,
                                            final OffsetDateTime novoAtualizadoEm) {
        this.contadorAssinatura = novoContadorAssinatura;
        this.atualizadoEm = Objects.requireNonNull(novoAtualizadoEm, "novoAtualizadoEm é obrigatório");
    }
}
