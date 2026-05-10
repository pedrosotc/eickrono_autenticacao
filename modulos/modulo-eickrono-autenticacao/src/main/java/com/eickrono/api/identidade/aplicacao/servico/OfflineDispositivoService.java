package com.eickrono.api.identidade.aplicacao.servico;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.EventoOfflineDispositivoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.EventoOfflineDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistrarEventosOfflineRequest;

import jakarta.transaction.Transactional;

/**
 * Publica a politica offline do backend e registra a reconciliacao de eventos offline.
 */
@Service
public class OfflineDispositivoService {

    private final DispositivoProperties dispositivoProperties;
    private final TokenDispositivoService tokenDispositivoService;
    private final DispositivoIdentidadeService dispositivoIdentidadeService;
    private final EventoOfflineDispositivoRepositorio eventoOfflineRepositorio;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public OfflineDispositivoService(DispositivoProperties dispositivoProperties,
                                     TokenDispositivoService tokenDispositivoService,
                                     DispositivoIdentidadeService dispositivoIdentidadeService,
                                     EventoOfflineDispositivoRepositorio eventoOfflineRepositorio,
                                     AuditoriaService auditoriaService,
                                     Clock clock) {
        this.dispositivoProperties = dispositivoProperties;
        this.tokenDispositivoService = tokenDispositivoService;
        this.dispositivoIdentidadeService = dispositivoIdentidadeService;
        this.eventoOfflineRepositorio = eventoOfflineRepositorio;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    public PoliticaOfflineDispositivoResponse obterPolitica() {
        DispositivoProperties.Offline offline = dispositivoProperties.getOffline();
        List<String> condicoes = new ArrayList<>();
        if (offline.isBloquearQuandoTokenRevogado()) {
            condicoes.add("TOKEN_REVOGADO");
        }
        if (offline.isBloquearQuandoTokenExpirado()) {
            condicoes.add("TOKEN_EXPIRADO");
        }
        if (offline.isBloquearQuandoDispositivoSemConfianca()) {
            condicoes.add("DISPOSITIVO_SEM_CONFIANCA");
        }
        return new PoliticaOfflineDispositivoResponse(
                offline.isPermitido(),
                offline.getTempoMaximoMinutos(),
                offline.isExigeReconciliacao(),
                List.copyOf(condicoes),
                listarEventosPermitidos());
    }

    @Transactional
    public void registrarEventosOffline(String usuarioSub,
                                        String tokenDispositivo,
                                        RegistrarEventosOfflineRequest request) {
        Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");
        Objects.requireNonNull(tokenDispositivo, "tokenDispositivo é obrigatório");
        if (!dispositivoProperties.getOffline().isPermitido()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Uso offline desabilitado pela politica do servidor");
        }

        List<EventoOfflineDispositivoRequest> eventos = Objects.requireNonNull(request, "request é obrigatório").eventos();
        if (eventos == null || eventos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nenhum evento offline informado");
        }

        TokenDispositivo token = tokenDispositivoService.validarTokenAtivo(usuarioSub, tokenDispositivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.LOCKED,
                        "Token de dispositivo inválido para reconciliação offline"));
        DispositivoIdentidade dispositivo = dispositivoIdentidadeService.garantirDispositivoParaToken(token);

        if (!dispositivo.estaConfiavel() && dispositivoProperties.getOffline().isBloquearQuandoDispositivoSemConfianca()) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Dispositivo sem confiança para reconciliação offline");
        }

        OffsetDateTime registradoEm = OffsetDateTime.now(clock);
        Set<TipoEventoOfflineDispositivo> eventosPermitidos = tiposEventosPermitidos();
        for (EventoOfflineDispositivoRequest eventoRequest : eventos) {
            if (eventoRequest == null || eventoRequest.tipoEvento() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evento offline inválido");
            }
            if (!eventosPermitidos.contains(eventoRequest.tipoEvento())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tipo de evento offline nao permitido pela politica do servidor");
            }
            OffsetDateTime ocorridoEm = Objects.requireNonNullElse(eventoRequest.ocorridoEm(), registradoEm);
            EventoOfflineDispositivo evento = new EventoOfflineDispositivo(
                    UUID.randomUUID(),
                    dispositivo,
                    token,
                    Objects.requireNonNull(eventoRequest.tipoEvento()),
                    eventoRequest.detalhes(),
                    ocorridoEm,
                    registradoEm);
            eventoOfflineRepositorio.save(evento);
        }

        auditoriaService.registrarEvento(
                "DISPOSITIVO_EVENTOS_OFFLINE_REGISTRADOS",
                usuarioSub,
                "Eventos offline reconciliados: " + eventos.size());
    }

    private List<String> listarEventosPermitidos() {
        return tiposEventosPermitidos().stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    private Set<TipoEventoOfflineDispositivo> tiposEventosPermitidos() {
        return dispositivoProperties.getOffline().getEventosPermitidos().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(valor -> !valor.isEmpty())
                .map(valor -> {
                    try {
                        return TipoEventoOfflineDispositivo.valueOf(valor.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalStateException(
                                "Evento offline configurado invalido: " + valor,
                                ex
                        );
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }
}
