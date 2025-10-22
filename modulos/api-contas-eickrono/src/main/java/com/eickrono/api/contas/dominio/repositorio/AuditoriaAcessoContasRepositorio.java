package com.eickrono.api.contas.dominio.repositorio;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistência de acessos auditáveis.
 */
public interface AuditoriaAcessoContasRepositorio extends JpaRepository<AuditoriaAcessoContas, Long> {
}
