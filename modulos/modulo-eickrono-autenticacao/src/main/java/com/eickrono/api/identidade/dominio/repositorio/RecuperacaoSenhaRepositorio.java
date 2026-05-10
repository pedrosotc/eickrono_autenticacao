package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.RecuperacaoSenha;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecuperacaoSenhaRepositorio extends JpaRepository<RecuperacaoSenha, Long> {

    Optional<RecuperacaoSenha> findByFluxoId(UUID fluxoId);
}
