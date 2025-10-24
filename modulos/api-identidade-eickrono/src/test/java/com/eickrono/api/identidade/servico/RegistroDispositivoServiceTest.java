package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusCodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.CodigoVerificacaoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.dto.RegistroDispositivoResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RegistroDispositivoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2024-05-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RegistroDispositivoRepositorio registroRepositorio;
    @Mock
    private CodigoVerificacaoRepositorio codigoRepositorio;
    private TokenDispositivoServiceFake tokenDispositivoService;
    @Mock
    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio;

    private DispositivoProperties properties;
    private RegistroDispositivoService registroDispositivoService;
    private CapturadorCanal canalSms;
    private CapturadorCanal canalEmail;

    private RegistroDispositivo ultimoRegistro;

    /**
     * Prepara o serviço real com dependências mockadas para acompanhar auditoria e registros gerados.
     * Utilizamos um TokenDispositivoServiceFake para evitar o uso de Mockito inline (incompatível com Java 25).
     */
    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        properties = new DispositivoProperties();
        properties.getCodigo().setSegredoHmac("codigo-secreto-test");
        properties.getCodigo().setExpiracaoHoras(9);
        properties.getCodigo().setReenviosMaximos(3);
        properties.getCodigo().setTentativasMaximas(5);
        properties.getToken().setSegredoHmac("token-secreto-test");
        properties.getToken().setValidadeHoras(48);
        properties.getToken().setTamanhoBytes(16);

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio);
        canalSms = new CapturadorCanal(CanalVerificacao.SMS);
        canalEmail = new CapturadorCanal(CanalVerificacao.EMAIL);

        lenient().when(registroRepositorio.save(any())).thenAnswer(invocation -> {
            ultimoRegistro = invocation.getArgument(0);
            return ultimoRegistro;
        });
        lenient().when(codigoRepositorio.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        tokenDispositivoService = new TokenDispositivoServiceFake(properties, CLOCK_FIXO);

        registroDispositivoService = new RegistroDispositivoService(
                registroRepositorio,
                codigoRepositorio,
                tokenDispositivoService,
                properties,
                auditoriaService,
                List.of(canalSms, canalEmail),
                CLOCK_FIXO);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Caso de uso: solicitar registro")
    class SolicitarRegistro {

        /**
         * Fluxo completo da solicitação inicial: verificamos geração de códigos e auditoria correspondente.
         */
        @Test
        @DisplayName("deve gerar códigos em ambos os canais e registrar auditoria")
        void deveGerarCodigosNosCanais() {
            RegistroDispositivoResponse resposta = solicitarRegistroPadrao();

            assertThat(canalSms.codigos(resposta.registroId())).hasSize(1);
            assertThat(canalEmail.codigos(resposta.registroId())).hasSize(1);

            ArgumentCaptor<AuditoriaEventoIdentidade> auditoriaCaptor = ArgumentCaptor.forClass(AuditoriaEventoIdentidade.class);
            verify(auditoriaRepositorio).save(auditoriaCaptor.capture());
            assertThat(auditoriaCaptor.getValue().getTipoEvento()).isEqualTo("DISPOSITIVO_REGISTRO_SOLICITADO");

            assertThat(ultimoRegistro.getStatus()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
            assertThat(ultimoRegistro.getExpiraEm()).isEqualTo(OffsetDateTime.now(CLOCK_FIXO).plusHours(properties.getCodigo().getExpiracaoHoras()));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Caso de uso: confirmar registro")
    class ConfirmarRegistro {

        /**
         * Confirmação bem-sucedida precisa validar códigos SMS e e-mail, emitir token e atualizar status.
         */
        @Test
        @DisplayName("deve confirmar registro quando códigos estiverem corretos")
        void deveConfirmarRegistro() {
            RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
            RegistroDispositivo registro = ultimoRegistro;
            when(registroRepositorio.findById(resposta.registroId())).thenReturn(Optional.of(registro));

            String codigoSms = canalSms.codigos(resposta.registroId()).getFirst();
            String codigoEmail = canalEmail.codigos(resposta.registroId()).getFirst();

            TokenDispositivo tokenEntidade = new TokenDispositivo(
                    UUID.randomUUID(),
                    registro,
                    "sub-123",
                    registro.getFingerprint(),
                    registro.getPlataforma(),
                    registro.getVersaoAplicativo().orElse(null),
                    "hash-token",
                    StatusTokenDispositivo.ATIVO,
                    OffsetDateTime.now(CLOCK_FIXO),
                    OffsetDateTime.now(CLOCK_FIXO).plusHours(48));
            tokenDispositivoService.configurarEmissao(new TokenDispositivoService.TokenEmitido("token-dispositivo", tokenEntidade));

            ConfirmacaoRegistroRequest request = new ConfirmacaoRegistroRequest();
            request.setCodigoSms(codigoSms);
            request.setCodigoEmail(codigoEmail);

            var respostaConfirmacao = registroDispositivoService.confirmarRegistro(resposta.registroId(), request, Optional.of("sub-123"));

            assertThat(respostaConfirmacao.tokenDispositivo()).isEqualTo("token-dispositivo");
            assertThat(tokenDispositivoService.getUltimoUsuario()).isEqualTo("sub-123");
            assertThat(tokenDispositivoService.getUltimoRegistro()).isSameAs(registro);
            assertThat(registro.getStatus()).isEqualTo(StatusRegistroDispositivo.CONFIRMADO);
            registro.codigoPorCanal(CanalVerificacao.SMS).ifPresent(codigo ->
                    assertThat(codigo.getStatus()).isEqualTo(StatusCodigoVerificacao.CONFIRMADO));
            registro.codigoPorCanal(CanalVerificacao.EMAIL).ifPresent(codigo ->
                    assertThat(codigo.getStatus()).isEqualTo(StatusCodigoVerificacao.CONFIRMADO));
        }

        /**
         * Quando um dos códigos falha, o serviço deve lançar 401 e registrar tentativa nos metadados.
         */
        @Test
        @DisplayName("deve lançar exceção quando código informado for inválido")
        void deveRejeitarCodigoInvalido() {
            RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
            RegistroDispositivo registro = ultimoRegistro;
            when(registroRepositorio.findById(resposta.registroId())).thenReturn(Optional.of(registro));

            ConfirmacaoRegistroRequest request = new ConfirmacaoRegistroRequest();
            request.setCodigoSms("000000");
            request.setCodigoEmail(canalEmail.codigos(resposta.registroId()).getFirst());

            assertThatThrownBy(() -> registroDispositivoService.confirmarRegistro(resposta.registroId(), request, Optional.of("sub-123")))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            registro.codigoPorCanal(CanalVerificacao.SMS).ifPresent(codigo ->
                    assertThat(codigo.getTentativas()).isEqualTo(1));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Caso de uso: reenviar códigos")
    class ReenviarCodigos {

        /**
         * Caso ainda existam reenvios disponíveis, os códigos são renovados e um novo contador registrado.
         */
        @Test
        @DisplayName("deve gerar novo código quando limite não foi atingido")
        void deveReenviarComSucesso() {
            RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
            RegistroDispositivo registro = ultimoRegistro;
            when(registroRepositorio.findById(resposta.registroId())).thenReturn(Optional.of(registro));

            ReenvioCodigoRequest requisicao = new ReenvioCodigoRequest();
            requisicao.setReenviarEmail(true);
            requisicao.setReenviarSms(true);

            registroDispositivoService.reenviarCodigos(resposta.registroId(), requisicao);

            assertThat(registro.getReenvios()).isEqualTo(1);
            assertThat(canalSms.codigos(resposta.registroId())).hasSize(2);
            assertThat(canalEmail.codigos(resposta.registroId())).hasSize(2);
        }

        /**
         * Quando os reenvios alcançam o limite configurado, esperamos a resposta 429 (Too Many Requests).
         */
        @Test
        @DisplayName("deve lançar quando limite for atingido")
        void deveFalharQuandoLimiteAtingido() {
            RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
            RegistroDispositivo registro = ultimoRegistro;
            when(registroRepositorio.findById(resposta.registroId())).thenReturn(Optional.of(registro));

            registro.codigoPorCanal(CanalVerificacao.SMS).ifPresent(this::atingirLimiteReenvios);
            registro.codigoPorCanal(CanalVerificacao.EMAIL).ifPresent(this::atingirLimiteReenvios);

            assertThatThrownBy(() -> registroDispositivoService.reenviarCodigos(resposta.registroId(), new ReenvioCodigoRequest()))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }

        private void atingirLimiteReenvios(CodigoVerificacao codigo) {
            for (int i = 0; i < properties.getCodigo().getReenviosMaximos(); i++) {
                codigo.atualizarCodigo(codigo.getCodigoHash(), codigo.getEnviadoEm().orElseThrow(), codigo.getExpiraEm());
            }
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Caso de uso: expirar registros pendentes")
    class ExpirarRegistros {

        /**
         * Job de expiração: registros PENDENTE + expira_em passado devem virar EXPIRADO junto com os códigos.
         */
        @Test
        @DisplayName("deve marcar registros e códigos como expirados")
        void deveMarcarComoExpirado() {
            RegistroDispositivo registro = new RegistroDispositivo(
                    UUID.randomUUID(),
                    "sub-789",
                    "teste@eickrono.com",
                    "+5511999991111",
                    "fingerprint",
                    "ANDROID",
                    "1.0.0",
                    null,
                    StatusRegistroDispositivo.PENDENTE,
                    OffsetDateTime.now(CLOCK_FIXO).minusHours(10),
                    OffsetDateTime.now(CLOCK_FIXO).minusMinutes(1));
            CodigoVerificacao codigo = new CodigoVerificacao(
                    UUID.randomUUID(),
                    CanalVerificacao.EMAIL,
                    "teste@eickrono.com",
                    "hash",
                    properties.getCodigo().getTentativasMaximas(),
                    properties.getCodigo().getReenviosMaximos(),
                    StatusCodigoVerificacao.PENDENTE,
                    OffsetDateTime.now(CLOCK_FIXO).minusHours(9),
                    OffsetDateTime.now(CLOCK_FIXO).minusMinutes(1));
            registro.adicionarCodigo(codigo);

            when(registroRepositorio.findByStatusInAndExpiraEmBefore(any(), any())).thenReturn(List.of(registro));

            registroDispositivoService.expirarRegistrosPendentes();

            assertThat(registro.getStatus()).isEqualTo(StatusRegistroDispositivo.EXPIRADO);
            assertThat(codigo.getStatus()).isEqualTo(StatusCodigoVerificacao.EXPIRADO);
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Caso de uso: revogar token")
    class RevogarToken {

        /**
         * Revogação solicitada pelo cliente: token deve ser marcado como REVOGADO e auditado.
         */
        @Test
        @DisplayName("deve revogar token quando encontrado")
        void deveRevogarToken() {
            RegistroDispositivo registro = new RegistroDispositivo(
                    UUID.randomUUID(),
                    "sub-123",
                    "teste@eickrono.com",
                    "+551199999999",
                    "fingerprint",
                    "IOS",
                    "1.0.0",
                    null,
                    StatusRegistroDispositivo.CONFIRMADO,
                    OffsetDateTime.now(CLOCK_FIXO).minusHours(3),
                    OffsetDateTime.now(CLOCK_FIXO).plusHours(1));
            TokenDispositivo token = new TokenDispositivo(
                    UUID.randomUUID(),
                    registro,
                    "sub-123",
                    "fingerprint",
                    "IOS",
                    "1.0.0",
                    "hash",
                    StatusTokenDispositivo.ATIVO,
                    OffsetDateTime.now(CLOCK_FIXO).minusHours(1),
                    OffsetDateTime.now(CLOCK_FIXO).plusHours(2));
            tokenDispositivoService.configurarValidar(Optional.of(token));

            registroDispositivoService.revogarToken("sub-123", "token-claro", MotivoRevogacaoToken.SOLICITACAO_CLIENTE);

            assertThat(token.getStatus()).isEqualTo(StatusTokenDispositivo.REVOGADO);
            ArgumentCaptor<AuditoriaEventoIdentidade> auditoriaCaptor = ArgumentCaptor.forClass(AuditoriaEventoIdentidade.class);
            verify(auditoriaRepositorio).save(auditoriaCaptor.capture());
            assertThat(auditoriaCaptor.getValue().getTipoEvento()).isEqualTo("DISPOSITIVO_TOKEN_REVOGADO");
        }

        /**
         * Se o token não for encontrado, nada deve ser registrado nem revogado.
         */
        @Test
        @DisplayName("não deve registrar auditoria quando token não existir")
        void naoDeveRegistrarQuandoTokenInexistente() {
            tokenDispositivoService.configurarValidar(Optional.empty());

            registroDispositivoService.revogarToken("sub-123", "token-claro", MotivoRevogacaoToken.SOLICITACAO_CLIENTE);

            verify(auditoriaRepositorio, never()).save(any());
        }
    }

    private RegistroDispositivoResponse solicitarRegistroPadrao() {
        RegistroDispositivoRequest request = new RegistroDispositivoRequest();
        request.setEmail("teste@eickrono.com");
        request.setTelefone("+55-11-99999-0000");
        request.setFingerprint("ios|iphone14,3|device");
        request.setPlataforma("IOS");
        request.setVersaoAplicativo("1.2.3");
        request.setChavePublica("chave-publica");

        return registroDispositivoService.solicitarRegistro(request, Optional.of("sub-123"));
    }

    private static class CapturadorCanal implements CanalEnvioCodigo {

        private final CanalVerificacao canal;
        private final Map<UUID, List<String>> codigos = new ConcurrentHashMap<>();

        CapturadorCanal(CanalVerificacao canal) {
            this.canal = canal;
        }

        @Override
        public CanalVerificacao canal() {
            return canal;
        }

        @Override
        public void enviar(RegistroDispositivo registro, String destino, String codigo) {
            codigos.computeIfAbsent(registro.getId(), chave -> new ArrayList<>()).add(codigo);
        }

        List<String> codigos(UUID registroId) {
            return codigos.getOrDefault(registroId, List.of());
        }
    }

    private static class TokenDispositivoServiceFake extends TokenDispositivoService {

        private TokenEmitido emissao = new TokenEmitido("token-padrao", null);
        private Optional<TokenDispositivo> validar = Optional.empty();
        private RegistroDispositivo ultimoRegistro;
        private String ultimoUsuario;

        TokenDispositivoServiceFake(DispositivoProperties properties, Clock clock) {
            super(org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio.class), properties, clock);
        }

        void configurarEmissao(TokenEmitido emissao) {
            this.emissao = emissao;
        }

        void configurarValidar(Optional<TokenDispositivo> validar) {
            this.validar = validar;
        }

        RegistroDispositivo getUltimoRegistro() {
            return ultimoRegistro;
        }

        String getUltimoUsuario() {
            return ultimoUsuario;
        }

        @Override
        public TokenEmitido emitirToken(RegistroDispositivo registro, String usuarioSub) {
            this.ultimoRegistro = registro;
            this.ultimoUsuario = usuarioSub;
            return emissao;
        }

        @Override
        public Optional<TokenDispositivo> validarTokenAtivo(String usuarioSub, String tokenClaro) {
            return validar;
        }

        @Override
        public void revogarTokensAtivos(String usuarioSub, MotivoRevogacaoToken motivo) {
            // comportamento controlado pelos testes
        }
    }
}
