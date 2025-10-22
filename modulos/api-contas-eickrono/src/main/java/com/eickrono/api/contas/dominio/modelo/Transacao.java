package com.eickrono.api.contas.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Registra transações financeiras vinculadas a uma conta.
 */
@Entity
@Table(name = "transacoes")
public class Transacao {

    private static final int TAMANHO_MAXIMO_DESCRICAO = 500;

    public enum TipoTransacao {
        CREDITO,
        DEBITO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id", nullable = false)
    private Conta conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTransacao tipo;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "efetivada_em", nullable = false)
    private OffsetDateTime efetivadaEm;

    @Column(length = TAMANHO_MAXIMO_DESCRICAO)
    private String descricao;

    protected Transacao() {
    }

    public Transacao(Conta conta, TipoTransacao tipo, BigDecimal valor,
                     OffsetDateTime efetivadaEm, String descricao) {
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor;
        this.efetivadaEm = efetivadaEm;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public Conta getConta() {
        return conta;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public OffsetDateTime getEfetivadaEm() {
        return efetivadaEm;
    }

    public String getDescricao() {
        return descricao;
    }
}
