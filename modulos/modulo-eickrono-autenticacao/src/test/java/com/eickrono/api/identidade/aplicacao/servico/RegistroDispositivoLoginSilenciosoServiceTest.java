package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.DispositivoSessaoRegistrado;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.DispositivoSessaoApiRequest;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegistroDispositivoLoginSilenciosoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2026-05-05T12:30:00Z"), ZoneOffset.UTC);

    @Mock
    private RegistroDispositivoRepositorio registroDispositivoRepositorio;

    @Mock
    private DispositivoIdentidadeRepositorio dispositivoIdentidadeRepositorio;

    @Mock
    private DispositivoIdentidadeService dispositivoIdentidadeService;

    @Mock
    private TokenDispositivoService tokenDispositivoService;

    @Mock
    private AuditoriaService auditoriaService;

    private RegistroDispositivoLoginSilenciosoService service;
    private DispositivoProperties properties;

    @BeforeEach
    void setUp() {
        properties = new DispositivoProperties();
        properties.getToken().setSegredoHmac("token-secreto-test");
        properties.getToken().setValidadeHoras(48);
        service = new RegistroDispositivoLoginSilenciosoService(
                registroDispositivoRepositorio,
                dispositivoIdentidadeRepositorio,
                dispositivoIdentidadeService,
                tokenDispositivoService,
                properties,
                auditoriaService,
                CLOCK_FIXO,
                null
        );
    }

    @Test
    void deveConcluirSessaoLocalSilenciosaQuandoDispositivoEstiverApto() {
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                55L,
                "usuario-123",
                "usuario@eickrono.com",
                "Usuario Teste",
                null,
                "LIBERADO"
        );
        DispositivoSessaoApiRequest request = new DispositivoSessaoApiRequest(
                "IOS",
                "eickrono-thimisu-app",
                "instalacao-abc",
                "iphone17,1",
                "apple",
                "ios",
                "18.0",
                "1.0.0"
        );
        DispositivoIdentidade dispositivoAtivo = criarDispositivo(StatusDispositivoIdentidade.ATIVO);
        TokenDispositivo token = new TokenDispositivo(
                UUID.randomUUID(),
                criarRegistroBase(),
                dispositivoAtivo,
                "usuario-123",
                "fingerprint-antigo",
                "IOS",
                "1.0.0",
                "hash-token",
                com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(48)
        );
        when(dispositivoIdentidadeRepositorio.findByUsuarioSubAndFingerprint(eq("usuario-123"), any()))
                .thenReturn(Optional.empty());
        when(dispositivoIdentidadeService.garantirDispositivo(eq("usuario-123"), eq(55L), any(RegistroDispositivo.class)))
                .thenReturn(dispositivoAtivo);
        when(tokenDispositivoService.emitirToken(any(RegistroDispositivo.class), eq(dispositivoAtivo), eq("usuario-123")))
                .thenReturn(new TokenDispositivoService.TokenEmitido("device-token", token));

        DispositivoSessaoRegistrado resposta = service.registrar(contexto, request);

        ArgumentCaptor<RegistroDispositivo> captorRegistro = ArgumentCaptor.forClass(RegistroDispositivo.class);
        verify(registroDispositivoRepositorio).save(captorRegistro.capture());
        RegistroDispositivo registroPersistido = captorRegistro.getValue();
        assertThat(registroPersistido.getStatus()).isEqualTo(StatusRegistroDispositivo.CONFIRMADO);
        assertThat(registroPersistido.getUsuarioSub()).contains("usuario-123");
        assertThat(registroPersistido.getPessoaIdPerfil()).contains(55L);
        assertThat(registroPersistido.getEmail()).isEqualTo("usuario@eickrono.com");
        assertThat(registroPersistido.getFingerprint()).isNotBlank();

        assertThat(resposta.tokenDispositivo()).isEqualTo("device-token");
        assertThat(resposta.tokenDispositivoExpiraEm()).isEqualTo(token.getExpiraEm());
        verify(tokenDispositivoService).emitirToken(eq(registroPersistido), eq(dispositivoAtivo), eq("usuario-123"));
    }

    @Test
    void deveBloquearSessaoSilenciosaQuandoDispositivoExistenteNaoEstiverAtivo() {
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                55L,
                "usuario-123",
                "usuario@eickrono.com",
                "Usuario Teste",
                null,
                "LIBERADO"
        );
        DispositivoSessaoApiRequest request = new DispositivoSessaoApiRequest(
                "IOS",
                "eickrono-thimisu-app",
                "instalacao-abc",
                "iphone17,1",
                "apple",
                "ios",
                "18.0",
                "1.0.0"
        );
        DispositivoIdentidade dispositivoBloqueado = criarDispositivo(StatusDispositivoIdentidade.REVOGADO);
        when(dispositivoIdentidadeRepositorio.findByUsuarioSubAndFingerprint(eq("usuario-123"), any()))
                .thenReturn(Optional.of(dispositivoBloqueado));

        assertThatThrownBy(() -> service.registrar(contexto, request))
                .isInstanceOf(FluxoPublicoException.class)
                .satisfies(throwable -> {
                    FluxoPublicoException exception = (FluxoPublicoException) throwable;
                    assertThat(exception.getCodigo()).isEqualTo("dispositivo_nao_liberado");
                });

        verify(registroDispositivoRepositorio, never()).save(any());
        verify(tokenDispositivoService, never()).emitirToken(any(), any(), any());
    }

    private DispositivoIdentidade criarDispositivo(final StatusDispositivoIdentidade status) {
        Pessoa pessoa = new Pessoa(
                "usuario-123",
                "usuario@eickrono.com",
                "Usuario Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.now(CLOCK_FIXO)
        );
        OffsetDateTime agora = OffsetDateTime.now(CLOCK_FIXO);
        return new DispositivoIdentidade(
                pessoa,
                "fingerprint-base",
                "IOS",
                "1.0.0",
                null,
                status,
                agora,
                agora
        );
    }

    private RegistroDispositivo criarRegistroBase() {
        return new RegistroDispositivo(
                UUID.randomUUID(),
                "usuario-123",
                "usuario@eickrono.com",
                null,
                "fingerprint-base",
                "IOS",
                "1.0.0",
                null,
                StatusRegistroDispositivo.CONFIRMADO,
                OffsetDateTime.now(CLOCK_FIXO),
                OffsetDateTime.now(CLOCK_FIXO).plusHours(48)
        );
    }
}
