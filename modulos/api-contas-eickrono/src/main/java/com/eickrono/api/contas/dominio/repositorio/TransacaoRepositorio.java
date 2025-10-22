package com.eickrono.api.contas.dominio.repositorio;

import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.modelo.Transacao;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de transações por conta.
 */
public interface TransacaoRepositorio extends JpaRepository<Transacao, Long> {

    List<Transacao> findByContaOrderByEfetivadaEmDesc(Conta conta);
}
