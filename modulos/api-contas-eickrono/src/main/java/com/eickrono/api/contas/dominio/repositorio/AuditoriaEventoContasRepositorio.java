package com.eickrono.api.contas.dominio.repositorio;

import com.eickrono.api.contas.dominio.modelo.AuditoriaEventoContas;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistência de eventos de auditoria da API de contas.
 */
public interface AuditoriaEventoContasRepositorio extends JpaRepository<AuditoriaEventoContas, Long> {
}
