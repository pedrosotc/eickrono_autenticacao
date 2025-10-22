package com.eickrono.api.contas.servico;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.AuditoriaEventoContas;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço centralizado de auditoria para a API de Contas.
 */
@Service
public class AuditoriaContasService {

    private final AuditoriaEventoContasRepositorio eventoRepositorio;
    private final AuditoriaAcessoContasRepositorio acessoRepositorio;

    public AuditoriaContasService(AuditoriaEventoContasRepositorio eventoRepositorio,
                                  AuditoriaAcessoContasRepositorio acessoRepositorio) {
        this.eventoRepositorio = eventoRepositorio;
        this.acessoRepositorio = acessoRepositorio;
    }

    @Transactional
    public void registrarEvento(String tipo, String sujeito, String detalhes) {
        AuditoriaEventoContas evento = new AuditoriaEventoContas(tipo, sujeito, OffsetDateTime.now(), detalhes);
        eventoRepositorio.save(evento);
    }

    @Transactional
    public void registrarAcesso(String sujeito, String endpoint, String detalhes) {
        AuditoriaAcessoContas acesso = new AuditoriaAcessoContas(sujeito, endpoint, OffsetDateTime.now(), detalhes);
        acessoRepositorio.save(acesso);
    }
}
