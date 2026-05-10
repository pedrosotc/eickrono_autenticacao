package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ResultadoEntregaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import com.eickrono.api.identidade.infraestrutura.integracao.ClienteTokenBackchannelPerfilKeycloak;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(
        classes = AplicacaoApiIdentidade.class,
        properties = {
                "eickrono.integracao-produto.scheduler.intervalo-ciclo=PT24H",
                "eickrono.integracao-produto.scheduler.timeout-recuperacao-processamento-segundos=1"
        }
)
@ActiveProfiles("test")
@ContextConfiguration(initializers = {
        IntegracaoProdutoPendenteSchedulerIT.LocalDatabaseOidcInitializer.class,
        IntegracaoProdutoPendenteSchedulerIT.BackendProdutoMockInitializer.class
})
class IntegracaoProdutoPendenteSchedulerIT {

    private static final String CODIGO_CLIENTE_THIMISU = "eickrono-thimisu-app";
    private static final String URI_PROVISIONAMENTO = "/api/interna/perfis-sistema/provisionamentos";
    private static final String URI_ATUALIZACAO = "/api/interna/perfis-sistema/atualizacoes";
    private static final String URI_REVOGACAO = "/api/interna/perfis-sistema/revogacoes/123";

    private static MockWebServer backendProdutoMockServer;

    @Autowired
    private PendenciaIntegracaoProdutoService pendenciaIntegracaoProdutoService;

    @Autowired
    private PendenciaIntegracaoProdutoRepositorio pendenciaIntegracaoProdutoRepositorio;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean
    private ClienteTokenBackchannelPerfilKeycloak clienteTokenBackchannelPerfilKeycloak;

    @MockBean
    private CadastroContaPendenteScheduler cadastroContaPendenteScheduler;

    @MockBean
    private RegistroDispositivoScheduler registroDispositivoScheduler;

    @MockBean
    private IntegracaoProdutoPendenteScheduler integracaoProdutoPendenteScheduler;

    private long clienteEcossistemaId;

    static final class BackendProdutoMockInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            iniciarBackendProdutoMock();
            TestPropertyValues.of(
                    "integracao.perfil.url-base=" + obterUrlBaseBackendProdutoMock(),
                    "integracao.perfil.jwt-interno.url-base=http://localhost:65535",
                    "integracao.perfil.jwt-interno.client-secret=test-client-secret"
            ).applyTo(context.getEnvironment());
        }
    }

    static final class LocalDatabaseOidcInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static final String DEFAULT_DB_HOST = "localhost";
        private static final String DEFAULT_DB_PORT = "5432";
        private static final String DEFAULT_DB_NAME = "eickrono_identidade";
        private static final String DEFAULT_DB_USER = "eickrono";
        private static final String DEFAULT_DB_PASSWORD = "senhaLocalDev";

        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            String issuer = InfraestruturaTesteIdentidade.obterIssuer();
            TestPropertyValues.of(
                    "spring.datasource.url=" + jdbcUrl(),
                    "spring.datasource.username=" + env("EICKRONO_TEST_DB_USER", DEFAULT_DB_USER),
                    "spring.datasource.password=" + env("EICKRONO_TEST_DB_PASSWORD", DEFAULT_DB_PASSWORD),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.flyway.enabled=false",
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
                            + issuer + "/protocol/openid-connect/certs",
                    "fapi.seguranca.audiencia-esperada=eickrono-autenticacao"
            ).applyTo(context.getEnvironment());
            context.addApplicationListener((ApplicationListener<ContextClosedEvent>) event ->
                    InfraestruturaTesteIdentidade.encerrarInfraestrutura());
        }

        private static String jdbcUrl() {
            String explicit = System.getenv("EICKRONO_TEST_JDBC_URL");
            if (explicit != null && !explicit.isBlank()) {
                return explicit.trim();
            }
            return "jdbc:postgresql://"
                    + env("EICKRONO_TEST_DB_HOST", DEFAULT_DB_HOST)
                    + ":"
                    + env("EICKRONO_TEST_DB_PORT", DEFAULT_DB_PORT)
                    + "/"
                    + env("EICKRONO_TEST_DB_NAME", DEFAULT_DB_NAME);
        }

        private static String env(final String name, final String fallback) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim();
        }
    }

    @BeforeEach
    void setUp() {
        when(clienteTokenBackchannelPerfilKeycloak.obterTokenBearer()).thenReturn("token-interno-teste");
        backendProdutoMockServer.setDispatcher(dispatcherPadrao404());
        assegurarSchemaMinimo();
        clienteEcossistemaId = buscarClienteEcossistemaId(CODIGO_CLIENTE_THIMISU);
        limparPendencias();
        resetarParametrosGlobais(true, 0, 10, 50, 200, 200);
        resetarControleProduto();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (backendProdutoMockServer != null) {
            backendProdutoMockServer.shutdown();
            backendProdutoMockServer = null;
        }
    }

    @Test
    @DisplayName("cenario 1: deve remover a pendencia quando o produto responder com sucesso")
    void deveRemoverPendenciaQuandoProdutoResponderComSucesso() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(201, "{\"status\":\"LIBERADO\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        assertThat(resultado.entregasConcluidas()).isEqualTo(1);
        assertThat(buscarPendencia(pendenciaId)).isEmpty();
        assertThat(cenario.quantidadeSondagens()).isEqualTo(1);
        assertThat(cenario.quantidadeEntregas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 2: deve reagendar quando o produto estiver fora do ar antes da chamada real")
    void deveReagendarQuandoProdutoEstiverForaDoArAntesDaChamadaReal() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(503, "{\"status\":\"DOWN\"}"),
                (indice, request) -> jsonStatus(201, "{\"status\":\"IGNORAR\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasReagendadas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("AGUARDANDO_NOVA_TENTATIVA");
        assertThat(((Number) linha.get("tentativas_realizadas")).intValue()).isEqualTo(1);
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("SONDAGEM_FALHOU");
        assertThat(cenario.quantidadeSondagens()).isEqualTo(1);
        assertThat(cenario.quantidadeEntregas()).isZero();
    }

    @Test
    @DisplayName("cenario 3: deve reagendar quando a chamada real falhar com erro 5xx")
    void deveReagendarQuandoEntregaRetornarErro5xx() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(502, "{\"erro\":\"temporario\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasReagendadas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("AGUARDANDO_NOVA_TENTATIVA");
        assertThat(((Number) linha.get("tentativas_realizadas")).intValue()).isEqualTo(1);
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("HTTP_5XX");
        assertThat(cenario.quantidadeEntregas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 4: deve repetir a entrega quando a resposta anterior se perder")
    void deveRepetirEntregaQuandoRespostaAnteriorSePerder() {
        resetarParametrosGlobais(true, 0, 10, 50, 200, 50);
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> indice == 1
                        ? new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                        : jsonStatus(200, "{\"status\":\"OK\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        pendenciaIntegracaoProdutoService.executarCiclo();
        pendenciaIntegracaoProdutoService.executarCiclo();

        assertThat(buscarPendencia(pendenciaId)).isEmpty();
        assertThat(cenario.quantidadeEntregas()).isEqualTo(2);
        assertThat(cenario.entregasRecebidas().get(0).getBody().readUtf8())
                .isEqualTo(cenario.entregasRecebidas().get(1).getBody().readUtf8());
    }

    @Test
    @DisplayName("cenario 5: deve reagendar quando a entrega sofrer timeout de rede")
    void deveReagendarQuandoEntregaSofrerTimeoutDeRede() {
        resetarParametrosGlobais(true, 0, 10, 50, 200, 50);
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasReagendadas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("AGUARDANDO_NOVA_TENTATIVA");
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("TIMEOUT_ENTREGA");
        assertThat(((Number) linha.get("tentativas_realizadas")).intValue()).isEqualTo(1);
        assertThat(cenario.quantidadeEntregas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 6: deve escalar quando o produto retornar erro 4xx irrecuperavel")
    void deveEscalarQuandoProdutoRetornarErro4xxIrrecuperavel() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(409, "{\"erro\":\"conflito\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasEscaladas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("FALHA_ESCALADA");
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("HTTP_4XX");
        assertThat(((Number) linha.get("tentativas_realizadas")).intValue()).isEqualTo(1);
        assertThat(cenario.quantidadeEntregas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 7: deve entregar operacao futura de alteracao no produto")
    void deveEntregarOperacaoFuturaDeAlteracaoNoProduto() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"ATUALIZADO\"}")
        );
        UUID pendenciaId = inserirPendencia("ATUALIZAR_PERFIL_SISTEMA", "PATCH", URI_ATUALIZACAO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, "{\"nome\":\"Ana\"}");

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        assertThat(resultado.entregasConcluidas()).isEqualTo(1);
        assertThat(buscarPendencia(pendenciaId)).isEmpty();
        assertThat(cenario.entregasRecebidas()).hasSize(1);
        assertThat(cenario.entregasRecebidas().get(0).getMethod()).isEqualTo("PATCH");
        assertThat(cenario.entregasRecebidas().get(0).getPath()).isEqualTo(URI_ATUALIZACAO);
    }

    @Test
    @DisplayName("cenario 8: deve entregar operacao de apagar ou revogar no produto")
    void deveEntregarOperacaoDeApagarOuRevogarNoProduto() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(204, "")
        );
        UUID pendenciaId = inserirPendencia("REVOGAR_PERFIL_SISTEMA", "DELETE", URI_REVOGACAO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, "{}");

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        assertThat(resultado.entregasConcluidas()).isEqualTo(1);
        assertThat(buscarPendencia(pendenciaId)).isEmpty();
        assertThat(cenario.entregasRecebidas()).hasSize(1);
        assertThat(cenario.entregasRecebidas().get(0).getMethod()).isEqualTo("DELETE");
        assertThat(cenario.entregasRecebidas().get(0).getPath()).isEqualTo(URI_REVOGACAO);
    }

    @Test
    @DisplayName("cenario 9: deve pausar a pendencia quando o produto estiver em manutencao programada")
    void devePausarPendenciaQuandoProdutoEstiverEmManutencaoProgramada() {
        atualizarControleProduto(
                true,
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
                "Atualizacao assistida",
                null,
                null,
                null,
                null
        );
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"IGNORAR\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasPausadas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("PAUSADO_MANUTENCAO");
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("PRODUTO_EM_MANUTENCAO");
        assertThat(cenario.quantidadeSondagens()).isZero();
        assertThat(cenario.quantidadeEntregas()).isZero();
    }

    @Test
    @DisplayName("cenario 10: deve incrementar tentativas no mesmo registro em falhas seguidas")
    void deveIncrementarTentativasNoMesmoRegistroEmFalhasSeguidas() {
        resetarParametrosGlobais(true, 0, 10, 50, 200, 200);
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(503, "{\"status\":\"DOWN\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"IGNORAR\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        pendenciaIntegracaoProdutoService.executarCiclo();
        Map<String, Object> aposPrimeira = buscarPendencia(pendenciaId).orElseThrow();
        pendenciaIntegracaoProdutoService.executarCiclo();
        Map<String, Object> aposSegunda = buscarPendencia(pendenciaId).orElseThrow();

        assertThat(((Number) aposPrimeira.get("tentativas_realizadas")).intValue()).isEqualTo(1);
        assertThat(((Number) aposSegunda.get("tentativas_realizadas")).intValue()).isEqualTo(2);
        assertThat(aposPrimeira.get("id")).isEqualTo(aposSegunda.get("id"));
        assertThat(cenario.quantidadeSondagens()).isEqualTo(2);
    }

    @Test
    @DisplayName("cenario 11: deve escalar quando atingir o limite maximo de tentativas")
    void deveEscalarQuandoAtingirLimiteMaximoDeTentativas() {
        resetarParametrosGlobais(true, 0, 2, 50, 200, 200);
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(503, "{\"status\":\"DOWN\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"IGNORAR\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 1,
                "AGUARDANDO_NOVA_TENTATIVA", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.pendenciasEscaladas()).isEqualTo(1);
        assertThat(linha.get("status_pendencia")).isEqualTo("FALHA_ESCALADA");
        assertThat(((Number) linha.get("tentativas_realizadas")).intValue()).isEqualTo(2);
        assertThat(linha.get("codigo_ultimo_erro")).isEqualTo("SONDAGEM_FALHOU");
        assertThat(cenario.quantidadeSondagens()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 12: deve recuperar item preso em processamento e conclui-lo")
    void deveRecuperarItemPresoEmProcessamentoEConcluiLo() {
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"OK\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "EM_PROCESSAMENTO",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2),
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        assertThat(resultado.pendenciasRecuperadas()).isEqualTo(1);
        assertThat(resultado.entregasConcluidas()).isEqualTo(1);
        assertThat(buscarPendencia(pendenciaId)).isEmpty();
        assertThat(cenario.quantidadeEntregas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cenario 13: deve reservar a mesma pendencia apenas uma vez com concorrencia")
    void deveReservarMesmaPendenciaApenasUmaVezComConcorrencia() throws Exception {
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);
        CyclicBarrier barreira = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<PendenciaIntegracaoProduto>> tarefaA = () -> {
                barreira.await(5, TimeUnit.SECONDS);
                return pendenciaIntegracaoProdutoRepositorio.reservarPendenciasProcessaveis(
                        "instancia-a",
                        1,
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
            };
            Callable<List<PendenciaIntegracaoProduto>> tarefaB = () -> {
                barreira.await(5, TimeUnit.SECONDS);
                return pendenciaIntegracaoProdutoRepositorio.reservarPendenciasProcessaveis(
                        "instancia-b",
                        1,
                        OffsetDateTime.now(ZoneOffset.UTC)
                );
            };

            Future<List<PendenciaIntegracaoProduto>> futuroA = executor.submit(tarefaA);
            Future<List<PendenciaIntegracaoProduto>> futuroB = executor.submit(tarefaB);

            List<PendenciaIntegracaoProduto> loteA = futuroA.get(5, TimeUnit.SECONDS);
            List<PendenciaIntegracaoProduto> loteB = futuroB.get(5, TimeUnit.SECONDS);

            assertThat(loteA.size() + loteB.size()).isEqualTo(1);
            Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
            assertThat(linha.get("status_pendencia")).isEqualTo("EM_PROCESSAMENTO");
            assertThat(linha.get("processando_por_instancia")).isIn("instancia-a", "instancia-b");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("cenario 14: nao deve processar a fila quando ela estiver desabilitada")
    void naoDeveProcessarFilaQuandoElaEstiverDesabilitada() {
        resetarParametrosGlobais(false, 0, 10, 50, 200, 200);
        ProdutoMockCenario cenario = configurarCenarioProduto(
                indice -> jsonStatus(200, "{\"status\":\"UP\"}"),
                (indice, request) -> jsonStatus(200, "{\"status\":\"IGNORAR\"}")
        );
        UUID pendenciaId = inserirPendencia("PROVISIONAR_PERFIL_SISTEMA", "POST", URI_PROVISIONAMENTO, 0,
                "PENDENTE_ENVIO", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null);

        ResultadoEntregaIntegracaoProduto resultado = pendenciaIntegracaoProdutoService.executarCiclo();

        Map<String, Object> linha = buscarPendencia(pendenciaId).orElseThrow();
        assertThat(resultado.habilitado()).isFalse();
        assertThat(linha.get("status_pendencia")).isEqualTo("PENDENTE_ENVIO");
        assertThat(cenario.quantidadeSondagens()).isZero();
        assertThat(cenario.quantidadeEntregas()).isZero();
    }

    private static synchronized void iniciarBackendProdutoMock() {
        if (backendProdutoMockServer != null) {
            return;
        }
        backendProdutoMockServer = new MockWebServer();
        try {
            backendProdutoMockServer.start();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao iniciar MockWebServer do backend do produto.", ex);
        }
        backendProdutoMockServer.setDispatcher(dispatcherPadrao404());
    }

    private static String obterUrlBaseBackendProdutoMock() {
        iniciarBackendProdutoMock();
        HttpUrl url = backendProdutoMockServer.url("/");
        return url.toString().endsWith("/") ? url.toString().substring(0, url.toString().length() - 1) : url.toString();
    }

    private static Dispatcher dispatcherPadrao404() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(final RecordedRequest request) {
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private ProdutoMockCenario configurarCenarioProduto(final IntFunction<MockResponse> responderSondagem,
                                                        final BiFunction<Integer, RecordedRequest, MockResponse> responderEntrega) {
        ProdutoMockCenario cenario = new ProdutoMockCenario(responderSondagem, responderEntrega);
        backendProdutoMockServer.setDispatcher(cenario.dispatcher());
        return cenario;
    }

    private long buscarClienteEcossistemaId(final String codigoCliente) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM catalogo.clientes_ecossistema
                        WHERE codigo = :codigo
                        """,
                new MapSqlParameterSource("codigo", codigoCliente),
                Long.class
        );
    }

    private void assegurarSchemaMinimo() {
        jdbcTemplate.getJdbcTemplate().execute("CREATE SCHEMA IF NOT EXISTS catalogo");
        jdbcTemplate.getJdbcTemplate().execute("CREATE SCHEMA IF NOT EXISTS autenticacao");
        jdbcTemplate.getJdbcTemplate().execute(
                """
                        CREATE TABLE IF NOT EXISTS catalogo.clientes_ecossistema (
                            id BIGSERIAL PRIMARY KEY,
                            codigo VARCHAR(64) NOT NULL UNIQUE,
                            nome VARCHAR(255) NOT NULL,
                            tipo VARCHAR(32) NOT NULL,
                            client_id_oidc VARCHAR(255),
                            ativo BOOLEAN NOT NULL DEFAULT TRUE,
                            criado_em TIMESTAMPTZ NOT NULL,
                            atualizado_em TIMESTAMPTZ NOT NULL
                        )
                        """
        );
        jdbcTemplate.getJdbcTemplate().execute(
                """
                        CREATE TABLE IF NOT EXISTS autenticacao.pendencias_integracao_produto (
                            id uuid PRIMARY KEY,
                            cliente_ecossistema_id bigint NOT NULL
                                REFERENCES catalogo.clientes_ecossistema (id),
                            tipo_operacao varchar(64) NOT NULL,
                            uri_endpoint varchar(512) NOT NULL,
                            metodo_http varchar(16) NOT NULL,
                            payload_json jsonb NOT NULL,
                            idempotency_key varchar(255) NOT NULL,
                            versao_contrato varchar(32) NOT NULL,
                            cadastro_id uuid,
                            pessoa_id_central bigint,
                            perfil_sistema_id uuid,
                            identificador_publico_sistema varchar(255),
                            status_pendencia varchar(32) NOT NULL,
                            tentativas_realizadas integer NOT NULL DEFAULT 0,
                            ultima_tentativa_em timestamptz,
                            proxima_tentativa_em timestamptz NOT NULL,
                            codigo_ultimo_erro varchar(128),
                            mensagem_ultimo_erro varchar(2000),
                            processando_por_instancia varchar(255),
                            processando_desde timestamptz,
                            criado_em timestamptz NOT NULL,
                            atualizado_em timestamptz NOT NULL,
                            CONSTRAINT uq_pendencia_integracao_produto_idempotency
                                UNIQUE (cliente_ecossistema_id, idempotency_key)
                        )
                        """
        );
        jdbcTemplate.getJdbcTemplate().execute(
                """
                        CREATE TABLE IF NOT EXISTS autenticacao.parametros_scheduler_integracao_produto (
                            id smallint PRIMARY KEY,
                            habilitado boolean NOT NULL,
                            tempo_entre_tentativas_segundos integer NOT NULL,
                            quantidade_maxima_tentativas integer NOT NULL,
                            quantidade_maxima_itens_por_ciclo integer NOT NULL,
                            timeout_sondagem_millis integer NOT NULL,
                            timeout_entrega_millis integer NOT NULL,
                            criado_em timestamptz NOT NULL,
                            atualizado_em timestamptz NOT NULL
                        )
                        """
        );
        jdbcTemplate.getJdbcTemplate().execute(
                """
                        CREATE TABLE IF NOT EXISTS autenticacao.controles_integracao_produto (
                            id bigserial PRIMARY KEY,
                            cliente_ecossistema_id bigint NOT NULL UNIQUE
                                REFERENCES catalogo.clientes_ecossistema (id),
                            escritas_internas_habilitadas boolean NOT NULL DEFAULT true,
                            produto_em_manutencao boolean NOT NULL DEFAULT false,
                            inicio_manutencao timestamptz,
                            fim_manutencao timestamptz,
                            motivo_manutencao varchar(512),
                            tempo_entre_tentativas_segundos_override integer,
                            quantidade_maxima_tentativas_override integer,
                            timeout_sondagem_millis_override integer,
                            timeout_entrega_millis_override integer,
                            criado_em timestamptz NOT NULL,
                            atualizado_em timestamptz NOT NULL
                        )
                        """
        );
        jdbcTemplate.update(
                """
                        INSERT INTO catalogo.clientes_ecossistema (
                            codigo, nome, tipo, client_id_oidc, ativo, criado_em, atualizado_em
                        ) VALUES (
                            :codigo, :nome, :tipo, :clientIdOidc, true, NOW(), NOW()
                        )
                        ON CONFLICT (codigo) DO UPDATE
                        SET nome = EXCLUDED.nome,
                            tipo = EXCLUDED.tipo,
                            client_id_oidc = EXCLUDED.client_id_oidc,
                            ativo = true,
                            atualizado_em = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("codigo", CODIGO_CLIENTE_THIMISU)
                        .addValue("nome", "Thimisu App")
                        .addValue("tipo", "APP")
                        .addValue("clientIdOidc", "app-flutter-local")
        );
    }

    private void limparPendencias() {
        jdbcTemplate.update("DELETE FROM autenticacao.pendencias_integracao_produto", new MapSqlParameterSource());
    }

    private void resetarParametrosGlobais(final boolean habilitado,
                                          final int tempoEntreTentativasSegundos,
                                          final int quantidadeMaximaTentativas,
                                          final int quantidadeMaximaItensPorCiclo,
                                          final int timeoutSondagemMillis,
                                          final int timeoutEntregaMillis) {
        jdbcTemplate.update(
                """
                        INSERT INTO autenticacao.parametros_scheduler_integracao_produto (
                            id,
                            habilitado,
                            tempo_entre_tentativas_segundos,
                            quantidade_maxima_tentativas,
                            quantidade_maxima_itens_por_ciclo,
                            timeout_sondagem_millis,
                            timeout_entrega_millis,
                            criado_em,
                            atualizado_em
                        ) VALUES (
                            1,
                            :habilitado,
                            :tempoEntreTentativasSegundos,
                            :quantidadeMaximaTentativas,
                            :quantidadeMaximaItensPorCiclo,
                            :timeoutSondagemMillis,
                            :timeoutEntregaMillis,
                            NOW(),
                            NOW()
                        )
                        ON CONFLICT (id) DO UPDATE
                        SET habilitado = EXCLUDED.habilitado,
                            tempo_entre_tentativas_segundos = EXCLUDED.tempo_entre_tentativas_segundos,
                            quantidade_maxima_tentativas = EXCLUDED.quantidade_maxima_tentativas,
                            quantidade_maxima_itens_por_ciclo = EXCLUDED.quantidade_maxima_itens_por_ciclo,
                            timeout_sondagem_millis = EXCLUDED.timeout_sondagem_millis,
                            timeout_entrega_millis = EXCLUDED.timeout_entrega_millis,
                            atualizado_em = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("habilitado", habilitado)
                        .addValue("tempoEntreTentativasSegundos", tempoEntreTentativasSegundos)
                        .addValue("quantidadeMaximaTentativas", quantidadeMaximaTentativas)
                        .addValue("quantidadeMaximaItensPorCiclo", quantidadeMaximaItensPorCiclo)
                        .addValue("timeoutSondagemMillis", timeoutSondagemMillis)
                        .addValue("timeoutEntregaMillis", timeoutEntregaMillis)
        );
    }

    private void resetarControleProduto() {
        atualizarControleProduto(true, false, null, null, null, null, null, null, null);
    }

    private void atualizarControleProduto(final boolean escritasInternasHabilitadas,
                                          final boolean produtoEmManutencao,
                                          final OffsetDateTime inicioManutencao,
                                          final OffsetDateTime fimManutencao,
                                          final String motivoManutencao,
                                          final Integer tempoEntreTentativasSegundosOverride,
                                          final Integer quantidadeMaximaTentativasOverride,
                                          final Integer timeoutSondagemMillisOverride,
                                          final Integer timeoutEntregaMillisOverride) {
        jdbcTemplate.update(
                """
                        INSERT INTO autenticacao.controles_integracao_produto (
                            cliente_ecossistema_id,
                            escritas_internas_habilitadas,
                            produto_em_manutencao,
                            inicio_manutencao,
                            fim_manutencao,
                            motivo_manutencao,
                            tempo_entre_tentativas_segundos_override,
                            quantidade_maxima_tentativas_override,
                            timeout_sondagem_millis_override,
                            timeout_entrega_millis_override,
                            criado_em,
                            atualizado_em
                        ) VALUES (
                            :clienteEcossistemaId,
                            :escritasInternasHabilitadas,
                            :produtoEmManutencao,
                            :inicioManutencao,
                            :fimManutencao,
                            :motivoManutencao,
                            :tempoEntreTentativasSegundosOverride,
                            :quantidadeMaximaTentativasOverride,
                            :timeoutSondagemMillisOverride,
                            :timeoutEntregaMillisOverride,
                            NOW(),
                            NOW()
                        )
                        ON CONFLICT (cliente_ecossistema_id) DO UPDATE
                        SET escritas_internas_habilitadas = EXCLUDED.escritas_internas_habilitadas,
                            produto_em_manutencao = EXCLUDED.produto_em_manutencao,
                            inicio_manutencao = EXCLUDED.inicio_manutencao,
                            fim_manutencao = EXCLUDED.fim_manutencao,
                            motivo_manutencao = EXCLUDED.motivo_manutencao,
                            tempo_entre_tentativas_segundos_override =
                                EXCLUDED.tempo_entre_tentativas_segundos_override,
                            quantidade_maxima_tentativas_override =
                                EXCLUDED.quantidade_maxima_tentativas_override,
                            timeout_sondagem_millis_override = EXCLUDED.timeout_sondagem_millis_override,
                            timeout_entrega_millis_override = EXCLUDED.timeout_entrega_millis_override,
                            atualizado_em = NOW()
                        """,
                new MapSqlParameterSource()
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("escritasInternasHabilitadas", escritasInternasHabilitadas)
                        .addValue("produtoEmManutencao", produtoEmManutencao)
                        .addValue("inicioManutencao", inicioManutencao)
                        .addValue("fimManutencao", fimManutencao)
                        .addValue("motivoManutencao", motivoManutencao)
                        .addValue("tempoEntreTentativasSegundosOverride", tempoEntreTentativasSegundosOverride)
                        .addValue("quantidadeMaximaTentativasOverride", quantidadeMaximaTentativasOverride)
                        .addValue("timeoutSondagemMillisOverride", timeoutSondagemMillisOverride)
                        .addValue("timeoutEntregaMillisOverride", timeoutEntregaMillisOverride)
        );
    }

    private UUID inserirPendencia(final String tipoOperacao,
                                  final String metodoHttp,
                                  final String uriEndpoint,
                                  final int tentativasRealizadas,
                                  final String statusPendencia,
                                  final OffsetDateTime proximaTentativaEm,
                                  final OffsetDateTime processandoDesde) {
        return inserirPendencia(tipoOperacao, metodoHttp, uriEndpoint, tentativasRealizadas, statusPendencia,
                proximaTentativaEm, processandoDesde, "{\"cadastroId\":\"123\"}");
    }

    private UUID inserirPendencia(final String tipoOperacao,
                                  final String metodoHttp,
                                  final String uriEndpoint,
                                  final int tentativasRealizadas,
                                  final String statusPendencia,
                                  final OffsetDateTime proximaTentativaEm,
                                  final OffsetDateTime processandoDesde,
                                  final String payloadJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO autenticacao.pendencias_integracao_produto (
                            id,
                            cliente_ecossistema_id,
                            tipo_operacao,
                            uri_endpoint,
                            metodo_http,
                            payload_json,
                            idempotency_key,
                            versao_contrato,
                            cadastro_id,
                            pessoa_id_central,
                            perfil_sistema_id,
                            identificador_publico_sistema,
                            status_pendencia,
                            tentativas_realizadas,
                            ultima_tentativa_em,
                            proxima_tentativa_em,
                            codigo_ultimo_erro,
                            mensagem_ultimo_erro,
                            processando_por_instancia,
                            processando_desde,
                            criado_em,
                            atualizado_em
                        ) VALUES (
                            :id,
                            :clienteEcossistemaId,
                            :tipoOperacao,
                            :uriEndpoint,
                            :metodoHttp,
                            CAST(:payloadJson AS jsonb),
                            :idempotencyKey,
                            'v1',
                            :cadastroId,
                            :pessoaIdCentral,
                            :perfilSistemaId,
                            :identificadorPublicoSistema,
                            :statusPendencia,
                            :tentativasRealizadas,
                            NULL,
                            :proximaTentativaEm,
                            NULL,
                            NULL,
                            :processandoPorInstancia,
                            :processandoDesde,
                            NOW(),
                            NOW()
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("clienteEcossistemaId", clienteEcossistemaId)
                        .addValue("tipoOperacao", tipoOperacao)
                        .addValue("uriEndpoint", uriEndpoint)
                        .addValue("metodoHttp", metodoHttp)
                        .addValue("payloadJson", payloadJson)
                        .addValue("idempotencyKey", "it:" + id)
                        .addValue("cadastroId", UUID.randomUUID())
                        .addValue("pessoaIdCentral", 77L)
                        .addValue("perfilSistemaId", UUID.randomUUID())
                        .addValue("identificadorPublicoSistema", "ana.souza")
                        .addValue("statusPendencia", statusPendencia)
                        .addValue("tentativasRealizadas", tentativasRealizadas)
                        .addValue("proximaTentativaEm", proximaTentativaEm)
                        .addValue("processandoPorInstancia", processandoDesde == null ? null : "instancia-antiga")
                        .addValue("processandoDesde", processandoDesde)
        );
        return id;
    }

    private Optional<Map<String, Object>> buscarPendencia(final UUID pendenciaId) {
        List<Map<String, Object>> linhas = jdbcTemplate.queryForList(
                """
                        SELECT *
                        FROM autenticacao.pendencias_integracao_produto
                        WHERE id = :id
                        """,
                new MapSqlParameterSource("id", pendenciaId)
        );
        if (linhas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(linhas.get(0));
    }

    private static MockResponse jsonStatus(final int statusCode, final String body) {
        MockResponse response = new MockResponse().setResponseCode(statusCode);
        if (body != null) {
            response.setHeader("Content-Type", "application/json");
            response.setBody(body);
        }
        return response;
    }

    private static final class ProdutoMockCenario {

        private final AtomicInteger sondagens = new AtomicInteger();
        private final AtomicInteger entregas = new AtomicInteger();
        private final List<RecordedRequest> entregasRecebidas = new CopyOnWriteArrayList<>();
        private final IntFunction<MockResponse> responderSondagem;
        private final BiFunction<Integer, RecordedRequest, MockResponse> responderEntrega;

        private ProdutoMockCenario(final IntFunction<MockResponse> responderSondagem,
                                   final BiFunction<Integer, RecordedRequest, MockResponse> responderEntrega) {
            this.responderSondagem = responderSondagem;
            this.responderEntrega = responderEntrega;
        }

        private Dispatcher dispatcher() {
            return new Dispatcher() {
                @Override
                public MockResponse dispatch(final RecordedRequest request) {
                    String path = request.getPath() == null ? "" : request.getPath();
                    if (path.equals("/api/v1/estado")) {
                        return responderSondagem.apply(sondagens.incrementAndGet());
                    }
                    entregasRecebidas.add(request);
                    return responderEntrega.apply(entregas.incrementAndGet(), request);
                }
            };
        }

        private int quantidadeSondagens() {
            return sondagens.get();
        }

        private int quantidadeEntregas() {
            return entregas.get();
        }

        private List<RecordedRequest> entregasRecebidas() {
            return new ArrayList<>(entregasRecebidas);
        }
    }
}
