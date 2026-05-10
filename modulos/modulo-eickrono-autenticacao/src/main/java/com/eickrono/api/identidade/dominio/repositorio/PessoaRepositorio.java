package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório da raiz de identidade do usuário.
 */
public interface PessoaRepositorio extends JpaRepository<Pessoa, Long> {

    Optional<Pessoa> findBySub(String sub);
}
