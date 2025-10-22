package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistência de auditoria para a API de Identidade.
 */
public interface AuditoriaEventoIdentidadeRepositorio
        extends JpaRepository<AuditoriaEventoIdentidade, Long> {
}
