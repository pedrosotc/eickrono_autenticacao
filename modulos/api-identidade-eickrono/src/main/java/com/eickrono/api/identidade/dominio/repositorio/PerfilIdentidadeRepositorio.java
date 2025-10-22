package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório de perfis de identidade.
 */
public interface PerfilIdentidadeRepositorio extends JpaRepository<PerfilIdentidade, Long> {

    Optional<PerfilIdentidade> findBySub(String sub);
}
