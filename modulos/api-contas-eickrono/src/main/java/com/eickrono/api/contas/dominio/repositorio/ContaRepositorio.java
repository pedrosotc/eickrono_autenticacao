package com.eickrono.api.contas.dominio.repositorio;

import com.eickrono.api.contas.dominio.modelo.Conta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de contas.
 */
public interface ContaRepositorio extends JpaRepository<Conta, Long> {

    Optional<Conta> findByNumero(String numero);

    List<Conta> findByClienteId(String clienteId);
}
