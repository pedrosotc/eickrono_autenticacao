package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.EntregaIntegracaoProdutoException;
import com.eickrono.api.identidade.aplicacao.modelo.ControleIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ParametrosPersistidosSchedulerIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ResultadoEntregaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.SchedulerIntegracaoProdutoProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendenciaIntegracaoProdutoServiceTest {

    @Mock
    private PendenciaIntegracaoProdutoRepositorio repositorio;

    @Mock
    private SondagemOperacionalProdutoService sondagemOperacionalProdutoService;

    @Mock
    private ExecutorPendenciaIntegracaoProdutoService executorPendenciaIntegracaoProdutoService;

    private PendenciaIntegracaoProdutoService service;
    private SchedulerIntegracaoProdutoProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SchedulerIntegracaoProdutoProperties();
        properties.setHabilitado(true);
        properties.setTempoEntreTentativasSegundos(300);
        properties.setQuantidadeMaximaTentativas(10);
        properties.setQuantidadeMaximaItensPorCiclo(50);
        properties.setTimeoutSondagemMillis(3000);
        properties.setTimeoutEntregaMillis(10000);
        properties.setTimeoutRecuperacaoProcessamentoSegundos(900);
        Clock clock = Clock.fixed(Instant.parse("2026-05-03T12:00:00Z"), ZoneOffset.UTC);
        service = new PendenciaIntegracaoProdutoService(
                repositorio,
                properties,
                sondagemOperacionalProdutoService,
                executorPendenciaIntegracaoProdutoService,
                clock
        );
    }

    @Test
    @DisplayName("deve usar parametros do banco quando existirem")
    void deveUsarParametrosDoBancoQuandoExistirem() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.of(
                new ParametrosPersistidosSchedulerIntegracaoProduto(false, 120, 7, 25, 1500, 5000)
        ));

        var parametros = service.carregarParametrosOperacionais();

        assertThat(parametros.habilitado()).isFalse();
        assertThat(parametros.tempoEntreTentativasSegundos()).isEqualTo(120);
        assertThat(parametros.quantidadeMaximaTentativas()).isEqualTo(7);
        assertThat(parametros.quantidadeMaximaItensPorCiclo()).isEqualTo(25);
        assertThat(parametros.timeoutSondagemMillis()).isEqualTo(1500);
        assertThat(parametros.timeoutEntregaMillis()).isEqualTo(5000);
        assertThat(parametros.timeoutRecuperacaoProcessamentoSegundos()).isEqualTo(900);
    }

    @Test
    @DisplayName("deve usar fallback das properties quando nao houver linha no banco")
    void deveUsarFallbackDasPropertiesQuandoNaoHouverLinhaNoBanco() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.empty());

        var parametros = service.carregarParametrosOperacionais();

        assertThat(parametros.habilitado()).isTrue();
        assertThat(parametros.tempoEntreTentativasSegundos()).isEqualTo(300);
        assertThat(parametros.quantidadeMaximaTentativas()).isEqualTo(10);
        assertThat(parametros.quantidadeMaximaItensPorCiclo()).isEqualTo(50);
        assertThat(parametros.timeoutSondagemMillis()).isEqualTo(3000);
        assertThat(parametros.timeoutEntregaMillis()).isEqualTo(10000);
        assertThat(parametros.timeoutRecuperacaoProcessamentoSegundos()).isEqualTo(900);
    }

    @Test
    @DisplayName("nao deve processar fila quando estiver desabilitado")
    void naoDeveProcessarFilaQuandoEstiverDesabilitado() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.of(
                new ParametrosPersistidosSchedulerIntegracaoProduto(false, 120, 7, 25, 1500, 5000)
        ));

        ResultadoEntregaIntegracaoProduto resultado = service.executarCiclo();

        assertThat(resultado.habilitado()).isFalse();
        assertThat(resultado.pendenciasRecuperadas()).isZero();
        assertThat(resultado.pendenciasReservadas()).isZero();
        verify(repositorio, never()).recuperarPendenciasEmProcessamentoAbandonadas(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(repositorio, never()).reservarPendenciasProcessaveis(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("deve recuperar pendencias abandonadas e concluir entrega bem-sucedida")
    void deveRecuperarPendenciasAbandonadasEConcluirEntregaBemSucedida() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.empty());
        when(repositorio.recuperarPendenciasEmProcessamentoAbandonadas(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(2);
        PendenciaIntegracaoProduto pendencia = pendencia(0);
        when(repositorio.reservarPendenciasProcessaveis(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(pendencia));
        when(sondagemOperacionalProdutoService.produtoDisponivel(3000)).thenReturn(true);
        when(repositorio.buscarControleProduto(1L)).thenReturn(Optional.empty());

        ResultadoEntregaIntegracaoProduto resultado = service.executarCiclo();

        assertThat(resultado.habilitado()).isTrue();
        assertThat(resultado.pendenciasRecuperadas()).isEqualTo(2);
        assertThat(resultado.pendenciasReservadas()).isEqualTo(1);
        assertThat(resultado.entregasConcluidas()).isEqualTo(1);
        verify(repositorio).remover(pendencia.id());
    }

    @Test
    @DisplayName("deve pausar a pendencia quando o produto estiver em manutencao")
    void devePausarPendenciaQuandoProdutoEstiverEmManutencao() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.empty());
        when(repositorio.recuperarPendenciasEmProcessamentoAbandonadas(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        PendenciaIntegracaoProduto pendencia = pendencia(0);
        when(repositorio.reservarPendenciasProcessaveis(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(pendencia));
        when(repositorio.buscarControleProduto(1L)).thenReturn(Optional.of(
                new ControleIntegracaoProduto(1L, true, true, null, null, "Manutencao", null, null, null, null)
        ));

        ResultadoEntregaIntegracaoProduto resultado = service.executarCiclo();

        assertThat(resultado.pendenciasPausadas()).isEqualTo(1);
        verify(repositorio).marcarPausadoManutencao(pendencia.id(), "Manutencao");
        verify(executorPendenciaIntegracaoProdutoService, never()).entregar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("deve reagendar a pendencia quando a sondagem falhar antes do limite")
    void deveReagendarPendenciaQuandoSondagemFalharAntesDoLimite() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.empty());
        when(repositorio.recuperarPendenciasEmProcessamentoAbandonadas(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        PendenciaIntegracaoProduto pendencia = pendencia(0);
        when(repositorio.reservarPendenciasProcessaveis(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(pendencia));
        when(repositorio.buscarControleProduto(1L)).thenReturn(Optional.empty());
        when(sondagemOperacionalProdutoService.produtoDisponivel(3000)).thenReturn(false);

        ResultadoEntregaIntegracaoProduto resultado = service.executarCiclo();

        assertThat(resultado.pendenciasReagendadas()).isEqualTo(1);
        verify(repositorio).reagendar(
                org.mockito.ArgumentMatchers.eq(pendencia.id()),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("SONDAGEM_FALHOU"),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("deve escalar a pendencia quando a entrega falhar e o limite for atingido")
    void deveEscalarPendenciaQuandoEntregaFalharELimiteForAtingido() {
        when(repositorio.buscarParametrosGlobais()).thenReturn(Optional.empty());
        when(repositorio.recuperarPendenciasEmProcessamentoAbandonadas(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        PendenciaIntegracaoProduto pendencia = pendencia(9);
        when(repositorio.reservarPendenciasProcessaveis(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(pendencia));
        when(repositorio.buscarControleProduto(1L)).thenReturn(Optional.empty());
        when(sondagemOperacionalProdutoService.produtoDisponivel(3000)).thenReturn(true);
        org.mockito.Mockito.doThrow(new EntregaIntegracaoProdutoException(
                "HTTP_5XX",
                "falha",
                true
        )).when(executorPendenciaIntegracaoProdutoService).entregar(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt()
        );

        ResultadoEntregaIntegracaoProduto resultado = service.executarCiclo();

        assertThat(resultado.pendenciasEscaladas()).isEqualTo(1);
        verify(repositorio).escalar(
                org.mockito.ArgumentMatchers.eq(pendencia.id()),
                org.mockito.ArgumentMatchers.eq("HTTP_5XX"),
                org.mockito.ArgumentMatchers.eq("falha")
        );
    }

    private PendenciaIntegracaoProduto pendencia(final int tentativasRealizadas) {
        return new PendenciaIntegracaoProduto(
                UUID.randomUUID(),
                1L,
                "CRIAR_PERFIL_SISTEMA",
                "/api/interna/perfis-sistema/provisionamentos",
                "POST",
                "{\"exemplo\":true}",
                "idempotency-key",
                "v1",
                UUID.randomUUID(),
                10L,
                null,
                "joao123",
                "PENDENTE_ENVIO",
                tentativasRealizadas
        );
    }
}
