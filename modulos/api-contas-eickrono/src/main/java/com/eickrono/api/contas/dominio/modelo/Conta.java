package com.eickrono.api.contas.dominio.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representa uma conta de cliente da Eickrono.
 */
@Entity
@Table(name = "contas")
public class Conta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @Column(name = "cliente_id", nullable = false)
    private String clienteId;

    @Column(nullable = false)
    private BigDecimal saldo;

    @Column(name = "criada_em", nullable = false)
    private OffsetDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private OffsetDateTime atualizadaEm;

    protected Conta() {
    }

    public Conta(String numero, String clienteId, BigDecimal saldo,
                 OffsetDateTime criadaEm, OffsetDateTime atualizadaEm) {
        this.numero = numero;
        this.clienteId = clienteId;
        this.saldo = saldo;
        this.criadaEm = criadaEm;
        this.atualizadaEm = atualizadaEm;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public String getClienteId() {
        return clienteId;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public OffsetDateTime getCriadaEm() {
        return criadaEm;
    }

    public OffsetDateTime getAtualizadaEm() {
        return atualizadaEm;
    }

    public void atualizarSaldo(BigDecimal novoSaldo, OffsetDateTime instante) {
        this.saldo = novoSaldo;
        this.atualizadaEm = instante;
    }
}
