package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoOfflineDispositivoRepositorio extends JpaRepository<EventoOfflineDispositivo, UUID> {
}
