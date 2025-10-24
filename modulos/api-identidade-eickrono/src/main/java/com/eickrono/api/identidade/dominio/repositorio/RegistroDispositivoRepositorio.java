package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroDispositivoRepositorio extends JpaRepository<RegistroDispositivo, UUID> {

    Optional<RegistroDispositivo> findById(UUID id);

    List<RegistroDispositivo> findByStatusInAndExpiraEmBefore(Collection<StatusRegistroDispositivo> status,
                                                              OffsetDateTime limite);
}
