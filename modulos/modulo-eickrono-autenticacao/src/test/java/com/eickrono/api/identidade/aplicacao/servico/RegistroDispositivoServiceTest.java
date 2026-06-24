package com.eickrono.api.identidade.aplicacao.servico;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.CodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusCodigoVerificacao;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.CodigoVerificacaoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.RegistroDispositivoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroRequest;
import com.eickrono.api.identidade.apresentacao.dto.ReenvioCodigoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoRequest;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class RegistroDispositivoServiceTest {

    private static final Clock CLOCK_FIXO = Clock.fixed(Instant.parse("2024-05-10T12:00:00Z"), ZoneOffset.UTC);

    private TokenDispositivoServiceFake tokenDispositivoService;
    private ProvisionamentoIdentidadeServiceFake provisionamentoIdentidadeService;
    private DispositivoIdentidadeServiceFake dispositivoIdentidadeService;

    private DispositivoProperties properties;
    private RegistroDispositivoService registroDispositivoService;
    private CapturadorCanal canalSms;
    private CapturadorCanal canalEmail;

    private RegistroDispositivo ultimoRegistro;
    private final List<AuditoriaEventoIdentidade> auditorias = new ArrayList<>();
    private final Map<UUID, RegistroDispositivo> registros = new ConcurrentHashMap<>();
    private final Map<UUID, CodigoVerificacao> codigos = new ConcurrentHashMap<>();

    private RegistroDispositivoRepositorio registroRepositorio() {
        return (RegistroDispositivoRepositorio) Proxy.newProxyInstance(
                RegistroDispositivoRepositorio.class.getClassLoader(),
                new Class<?>[] {RegistroDispositivoRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> salvarRegistroLocal((RegistroDispositivo) Objects.requireNonNull(args)[0]);
                    case "findById" -> Optional.ofNullable(registros.get((UUID) Objects.requireNonNull(args)[0]));
                    case "findByStatusInAndExpiraEmBefore" -> localizarRegistrosExpirados(args);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "RegistroDispositivoRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private CodigoVerificacaoRepositorio codigoRepositorio() {
        return (CodigoVerificacaoRepositorio) Proxy.newProxyInstance(
                CodigoVerificacaoRepositorio.class.getClassLoader(),
                new Class<?>[] {CodigoVerificacaoRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> salvarCodigoLocal((CodigoVerificacao) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "CodigoVerificacaoRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return (AuditoriaEventoIdentidadeRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AuditoriaEventoIdentidade auditoria = (AuditoriaEventoIdentidade) Objects.requireNonNull(args)[0];
                        auditorias.add(auditoria);
                        yield auditoria;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * Prepara o serviço real com dependências mockadas para acompanhar auditoria e registros gerados.
     * Utilizamos um TokenDispositivoServiceFake para evitar o uso de Mockito inline (incompatível com Java 25).
     */
    private void inicializarServico() {
        inicializarServico(true);
    }

    private void inicializarServico(boolean smsHabilitado) {
        properties = new DispositivoProperties();
        properties.getOnboarding().setSmsHabilitado(smsHabilitado);
        properties.getOnboarding().setSmsFornecedor("log");
        properties.getCodigo().setSegredoHmac("codigo-secreto-test");
        properties.getCodigo().setExpiracaoHoras(9);
        properties.getCodigo().setReenviosMaximos(3);
        properties.getCodigo().setTentativasMaximas(5);
        properties.getToken().setSegredoHmac("token-secreto-test");
        properties.getToken().setValidadeHoras(48);
        properties.getToken().setTamanhoBytes(16);
        auditorias.clear();
        registros.clear();
        codigos.clear();
        ultimoRegistro = null;

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio());
        canalSms = new CapturadorCanal(CanalVerificacao.SMS);
        canalEmail = new CapturadorCanal(CanalVerificacao.EMAIL);

        tokenDispositivoService = new TokenDispositivoServiceFake(properties, CLOCK_FIXO);
        provisionamentoIdentidadeService = new ProvisionamentoIdentidadeServiceFake();
        dispositivoIdentidadeService = new DispositivoIdentidadeServiceFake(CLOCK_FIXO);

        registroDispositivoService = new RegistroDispositivoService(
                registroRepositorio(),
                codigoRepositorio(),
                tokenDispositivoService,
                null,
                new ClienteContextoPessoaPerfilSistemaFake(provisionamentoIdentidadeService.pessoa()),
                dispositivoIdentidadeService,
                properties,
                auditoriaService,
                List.of(canalSms, canalEmail),
                CLOCK_FIXO,
                null);
    }

    private RegistroDispositivo salvarRegistroLocal(RegistroDispositivo registro) {
        ultimoRegistro = registro;
        registros.put(registro.getId(), registro);
        return registro;
    }

    private CodigoVerificacao salvarCodigoLocal(CodigoVerificacao codigo) {
        codigos.put(codigo.getId(), codigo);
        return codigo;
    }

    private List<RegistroDispositivo> localizarRegistrosExpirados(Object[] args) {
        @SuppressWarnings("unchecked")
        List<StatusRegistroDispositivo> status = (List<StatusRegistroDispositivo>) Objects.requireNonNull(args)[0];
        OffsetDateTime limite = (OffsetDateTime) args[1];
        return registros.values().stream()
                .filter(registro -> status.contains(registro.getStatus()))
                .filter(registro -> registro.getExpiraEm().isBefore(limite))
                .toList();
    }

    /**
     * Fluxo completo da solicitação inicial: verificamos geração de códigos e auditoria correspondente.
     */
    @Test
    @DisplayName("deve gerar códigos em ambos os canais e registrar auditoria")
    void deveGerarCodigosNosCanais() {
        inicializarServico();
        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();

        assertThat(resposta.canaisConfirmacao()).containsExactlyInAnyOrder(CanalVerificacao.EMAIL, CanalVerificacao.SMS);
        assertThat(canalSms.codigos(resposta.registroId())).hasSize(1);
        assertThat(canalEmail.codigos(resposta.registroId())).hasSize(1);

        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.getFirst().getTipoEvento())
                .isEqualTo("DISPOSITIVO_REGISTRO_SOLICITADO");

        assertThat(ultimoRegistro.getStatus()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(ultimoRegistro.getExpiraEm()).isEqualTo(OffsetDateTime.now(CLOCK_FIXO).plusHours(properties.getCodigo().getExpiracaoHoras()));
    }

    @Test
    @DisplayName("não deve emitir token antes da confirmação do registro")
    void naoDeveEmitirTokenAntesDaConfirmacaoDoRegistro() {
        inicializarServico();

        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();

        assertThat(resposta.status()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(tokenDispositivoService.getQuantidadeEmissoes()).isZero();
        assertThat(tokenDispositivoService.getUltimoRegistro()).isNull();
        assertThat(tokenDispositivoService.getUltimoUsuario()).isNull();
    }

    @Test
    @DisplayName("deve gerar apenas código de e-mail quando SMS estiver desabilitado por política")
    void deveGerarApenasCodigoEmailQuandoSmsDesabilitado() {
        inicializarServico(false);
        RegistroDispositivoRequest request = new RegistroDispositivoRequest();
        request.setEmail("teste@eickrono.com");
        request.setFingerprint("ios|iphone14,3|device");
        request.setPlataforma("IOS");
        request.setVersaoAplicativo("1.2.3");

        RegistroDispositivoResponse resposta = registroDispositivoService.solicitarRegistro(request, Optional.of(criarJwt("sub-123")));

        assertThat(resposta.canaisConfirmacao()).containsExactly(CanalVerificacao.EMAIL);
        assertThat(canalEmail.codigos(resposta.registroId())).hasSize(1);
        assertThat(canalSms.codigos(resposta.registroId())).isEmpty();
        assertThat(Objects.requireNonNull(ultimoRegistro).getTelefone()).isEmpty();
    }

    @Test
    @DisplayName("deve cair para e-mail apenas quando o telefone não estiver disponível")
    void deveCairParaEmailQuandoTelefoneNaoEstiverDisponivel() {
        inicializarServico(true);
        RegistroDispositivoRequest request = new RegistroDispositivoRequest();
        request.setEmail("teste@eickrono.com");
        request.setFingerprint("ios|iphone14,3|device");
        request.setPlataforma("IOS");
        request.setVersaoAplicativo("1.2.3");

        RegistroDispositivoResponse resposta = registroDispositivoService.solicitarRegistro(request, Optional.of(criarJwt("sub-123")));

        assertThat(resposta.canaisConfirmacao()).containsExactly(CanalVerificacao.EMAIL);
        assertThat(canalEmail.codigos(resposta.registroId())).hasSize(1);
        assertThat(canalSms.codigos(resposta.registroId())).isEmpty();
        assertThat(Objects.requireNonNull(ultimoRegistro).getTelefone()).isEmpty();
    }

    /**
     * Confirmação bem-sucedida precisa validar códigos SMS e e-mail, emitir token e atualizar status.
     */
    @Test
    @DisplayName("deve confirmar registro quando códigos estiverem corretos")
    void deveConfirmarRegistro() {
        inicializarServico();
        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
        RegistroDispositivo registro = Objects.requireNonNull(ultimoRegistro);

        String codigoSms = canalSms.codigos(resposta.registroId()).getFirst();
        String codigoEmail = canalEmail.codigos(resposta.registroId()).getFirst();

        TokenDispositivo tokenEntidade = new TokenDispositivo(
                UUID.randomUUID(),
                registro,
                dispositivoIdentidadeService.garantirDispositivo(provisionamentoIdentidadeService.pessoa(), registro),
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

        var respostaConfirmacao = registroDispositivoService.confirmarRegistro(
                resposta.registroId(), request, Optional.of(criarJwt("sub-123")));

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
        inicializarServico();
        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
        RegistroDispositivo registro = Objects.requireNonNull(ultimoRegistro);

        ConfirmacaoRegistroRequest request = new ConfirmacaoRegistroRequest();
        request.setCodigoSms("000000");
        request.setCodigoEmail(canalEmail.codigos(resposta.registroId()).getFirst());

        assertThatThrownBy(() -> registroDispositivoService.confirmarRegistro(
                resposta.registroId(), request, Optional.of(criarJwt("sub-123"))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        registro.codigoPorCanal(CanalVerificacao.SMS).ifPresent(codigo ->
                assertThat(codigo.getTentativas()).isEqualTo(1));
    }

    /**
     * Caso ainda existam reenvios disponíveis, os códigos são renovados e um novo contador registrado.
     */
    @Test
    @DisplayName("deve gerar novo código quando limite não foi atingido")
    void deveReenviarComSucesso() {
        inicializarServico();
        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
        RegistroDispositivo registro = Objects.requireNonNull(ultimoRegistro);

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
        inicializarServico();
        RegistroDispositivoResponse resposta = solicitarRegistroPadrao();
        RegistroDispositivo registro = Objects.requireNonNull(ultimoRegistro);

        registro.codigoPorCanal(CanalVerificacao.SMS).ifPresent(this::atingirLimiteReenvios);
        registro.codigoPorCanal(CanalVerificacao.EMAIL).ifPresent(this::atingirLimiteReenvios);

        assertThatThrownBy(() -> registroDispositivoService.reenviarCodigos(resposta.registroId(), new ReenvioCodigoRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Job de expiração: registros PENDENTE + expira_em passado devem virar EXPIRADO junto com os códigos.
     */
    @Test
    @DisplayName("deve marcar registros e códigos como expirados")
    void deveMarcarComoExpirado() {
        inicializarServico();
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

        registros.put(registro.getId(), registro);

        registroDispositivoService.expirarRegistrosPendentes();

        assertThat(registro.getStatus()).isEqualTo(StatusRegistroDispositivo.EXPIRADO);
        assertThat(codigo.getStatus()).isEqualTo(StatusCodigoVerificacao.EXPIRADO);
    }

    /**
     * Revogação solicitada pelo cliente: token deve ser marcado como REVOGADO e auditado.
     */
    @Test
    @DisplayName("deve revogar token quando encontrado")
    void deveRevogarToken() {
        inicializarServico();
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
                dispositivoIdentidadeService.garantirDispositivo(provisionamentoIdentidadeService.pessoa(), registro),
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
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.getFirst().getTipoEvento())
                .isEqualTo("DISPOSITIVO_TOKEN_REVOGADO");
    }

    /**
     * Se o token não for encontrado, nada deve ser registrado nem revogado.
     */
    @Test
    @DisplayName("não deve registrar auditoria quando token não existir")
    void naoDeveRegistrarQuandoTokenInexistente() {
        inicializarServico();
        tokenDispositivoService.configurarValidar(Optional.empty());

        registroDispositivoService.revogarToken("sub-123", "token-claro", MotivoRevogacaoToken.SOLICITACAO_CLIENTE);

        assertThat(auditorias).isEmpty();
    }

    private void atingirLimiteReenvios(CodigoVerificacao codigo) {
        for (int i = 0; i < properties.getCodigo().getReenviosMaximos(); i++) {
            codigo.atualizarCodigo(codigo.getCodigoHash(), codigo.getEnviadoEm().orElseThrow(), codigo.getExpiraEm());
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

        return registroDispositivoService.solicitarRegistro(request, Optional.of(criarJwt("sub-123")));
    }

    private Jwt criarJwt(String sub) {
        return Jwt.withTokenValue("jwt-teste-" + sub)
                .header("alg", "none")
                .subject(sub)
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste")
                .claim("perfis", List.of("CLIENTE"))
                .claim("papeis", List.of("ROLE_cliente"))
                .build();
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
        private int quantidadeEmissoes;

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

        int getQuantidadeEmissoes() {
            return quantidadeEmissoes;
        }

        @Override
        public TokenEmitido emitirToken(RegistroDispositivo registro,
                                        DispositivoIdentidade dispositivo,
                                        String usuarioSub) {
            this.ultimoRegistro = registro;
            this.ultimoUsuario = usuarioSub;
            this.quantidadeEmissoes++;
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

    private static class ProvisionamentoIdentidadeServiceFake extends ProvisionamentoIdentidadeService {

        private final Pessoa pessoa;

        ProvisionamentoIdentidadeServiceFake() {
            super(
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio.class),
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio.class),
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio.class));
            this.pessoa = new Pessoa(
                    "sub-123",
                    "teste@eickrono.com",
                    "Pessoa Teste",
                    Set.of("CLIENTE"),
                    Set.of("ROLE_cliente"),
                    OffsetDateTime.parse("2024-05-10T12:00:00Z"));
        }

        Pessoa pessoa() {
            return pessoa;
        }

        @Override
        public Pessoa provisionarOuAtualizar(Jwt jwt) {
            return pessoa;
        }

        @Override
        public Optional<Pessoa> localizarPessoaPorSub(String sub) {
            return Objects.equals(pessoa.getSub(), sub) ? Optional.of(pessoa) : Optional.empty();
        }
    }

    private static class ClienteContextoPessoaPerfilSistemaFake implements ClienteContextoPessoaPerfilSistema {

        private final Pessoa pessoa;

        ClienteContextoPessoaPerfilSistemaFake(final Pessoa pessoa) {
            this.pessoa = pessoa;
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorPessoaId(final Long pessoaId) {
            return Objects.equals(pessoa.getId(), pessoaId) ? Optional.of(paraContexto()) : Optional.empty();
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorSub(final String sub) {
            return Objects.equals(pessoa.getSub(), sub) ? Optional.of(paraContexto()) : Optional.empty();
        }

        @Override
        public Optional<ContextoPessoaPerfilSistema> buscarPorEmail(final String email) {
            return Objects.equals(pessoa.getEmail(), email) ? Optional.of(paraContexto()) : Optional.empty();
        }

        private ContextoPessoaPerfilSistema paraContexto() {
            return new ContextoPessoaPerfilSistema(
                    pessoa.getId(),
                    pessoa.getSub(),
                    pessoa.getEmail(),
                    pessoa.getNome(),
                    null,
                    null);
        }
    }

    private static class DispositivoIdentidadeServiceFake extends DispositivoIdentidadeService {

        private final Clock clock;

        DispositivoIdentidadeServiceFake(Clock clock) {
            super(
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio.class),
                    org.mockito.Mockito.mock(com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio.class),
                    clock);
            this.clock = clock;
        }

        @Override
        public DispositivoIdentidade garantirDispositivo(Pessoa pessoa, RegistroDispositivo registro) {
            OffsetDateTime agora = OffsetDateTime.now(clock);
            return new DispositivoIdentidade(
                    pessoa,
                    registro.getFingerprint(),
                    registro.getPlataforma(),
                    registro.getVersaoAplicativo().orElse(null),
                    registro.getChavePublica().orElse(null),
                    StatusDispositivoIdentidade.ATIVO,
                    agora,
                    agora);
        }
    }
}
