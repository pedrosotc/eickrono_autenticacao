package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.ChaveAppleAppAttest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChaveAppleAppAttestRepositorio extends JpaRepository<ChaveAppleAppAttest, Long> {

    Optional<ChaveAppleAppAttest> findByChaveId(String chaveId);
}
