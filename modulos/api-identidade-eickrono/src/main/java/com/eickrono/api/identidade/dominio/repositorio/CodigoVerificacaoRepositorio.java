package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoVerificacaoRepositorio extends JpaRepository<CodigoVerificacao, UUID> {

    Optional<CodigoVerificacao> findByRegistroIdAndCanal(UUID registroId, CanalVerificacao canal);
}
