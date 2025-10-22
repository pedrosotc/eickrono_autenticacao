package com.eickrono.api.identidade.servico;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável por registrar eventos de auditoria.
 */
@Service
public class AuditoriaService {

    private final AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio;

    public AuditoriaService(AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio) {
        this.auditoriaRepositorio = auditoriaRepositorio;
    }

    @Transactional
    public void registrarEvento(String tipo, String sujeito, String detalhes) {
        AuditoriaEventoIdentidade evento = new AuditoriaEventoIdentidade(tipo, sujeito, OffsetDateTime.now(), detalhes);
        auditoriaRepositorio.save(evento);
    }
}
