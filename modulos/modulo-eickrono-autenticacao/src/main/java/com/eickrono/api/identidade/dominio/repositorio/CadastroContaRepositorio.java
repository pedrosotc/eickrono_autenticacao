package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.CadastroConta;
import com.eickrono.api.identidade.dominio.modelo.StatusCadastroConta;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CadastroContaRepositorio extends JpaRepository<CadastroConta, Long> {

    Optional<CadastroConta> findByCadastroId(UUID cadastroId);

    Optional<CadastroConta> findByEmailPrincipal(String emailPrincipal);

    Optional<CadastroConta> findByUsuarioIgnoreCase(String usuario);

    Optional<CadastroConta> findBySubjectRemoto(String subjectRemoto);

    List<CadastroConta> findByStatusAndCriadoEmBefore(StatusCadastroConta status, OffsetDateTime criadoEm);
}
