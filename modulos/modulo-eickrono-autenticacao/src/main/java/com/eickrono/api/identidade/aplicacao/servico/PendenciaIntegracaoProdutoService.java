package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.EntregaIntegracaoProdutoException;
import com.eickrono.api.identidade.aplicacao.modelo.ControleIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ParametrosOperacionaisSchedulerIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ParametrosPersistidosSchedulerIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ResultadoEntregaIntegracaoProduto;
import com.eickrono.api.identidade.dominio.repositorio.PendenciaIntegracaoProdutoRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.SchedulerIntegracaoProdutoProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PendenciaIntegracaoProdutoService {

    private final PendenciaIntegracaoProdutoRepositorio repositorio;
    private final SchedulerIntegracaoProdutoProperties properties;
    private final SondagemOperacionalProdutoService sondagemOperacionalProdutoService;
    private final ExecutorPendenciaIntegracaoProdutoService executorPendenciaIntegracaoProdutoService;
    private final Clock clock;

    public PendenciaIntegracaoProdutoService(final PendenciaIntegracaoProdutoRepositorio repositorio,
                                             final SchedulerIntegracaoProdutoProperties properties,
                                             final SondagemOperacionalProdutoService sondagemOperacionalProdutoService,
                                             final ExecutorPendenciaIntegracaoProdutoService executorPendenciaIntegracaoProdutoService,
                                             final Clock clock) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio e obrigatorio");
        this.properties = Objects.requireNonNull(properties, "properties e obrigatorio");
        this.sondagemOperacionalProdutoService = Objects.requireNonNull(
                sondagemOperacionalProdutoService,
                "sondagemOperacionalProdutoService e obrigatorio");
        this.executorPendenciaIntegracaoProdutoService = Objects.requireNonNull(
                executorPendenciaIntegracaoProdutoService,
                "executorPendenciaIntegracaoProdutoService e obrigatorio");
        this.clock = Objects.requireNonNull(clock, "clock e obrigatorio");
    }

    public ParametrosOperacionaisSchedulerIntegracaoProduto carregarParametrosOperacionais() {
        return repositorio.buscarParametrosGlobais()
                .map(this::mesclarParametrosPersistidos)
                .orElseGet(this::carregarFallbackProperties);
    }

    public ResultadoEntregaIntegracaoProduto executarCiclo() {
        ParametrosOperacionaisSchedulerIntegracaoProduto parametros = carregarParametrosOperacionais();
        if (!parametros.habilitado()) {
            return new ResultadoEntregaIntegracaoProduto(false, 0, 0, 0, 0, 0, 0);
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        int recuperadas = repositorio.recuperarPendenciasEmProcessamentoAbandonadas(
                agora.minusSeconds(parametros.timeoutRecuperacaoProcessamentoSegundos()),
                agora
        );
        List<PendenciaIntegracaoProduto> pendencias = repositorio.reservarPendenciasProcessaveis(
                "eickrono-autenticacao",
                parametros.quantidadeMaximaItensPorCiclo(),
                agora
        );
        int concluidas = 0;
        int reagendadas = 0;
        int escaladas = 0;
        int pausadas = 0;
        for (PendenciaIntegracaoProduto pendencia : pendencias) {
            ControleIntegracaoProduto controle = repositorio.buscarControleProduto(pendencia.clienteEcossistemaId())
                    .orElse(null);
            if (produtoEmManutencao(controle, agora)) {
                repositorio.marcarPausadoManutencao(
                        pendencia.id(),
                        motivoManutencao(controle)
                );
                pausadas++;
                continue;
            }
            int timeoutSondagem = overrideOuPadrao(
                    controle == null ? null : controle.timeoutSondagemMillisOverride(),
                    parametros.timeoutSondagemMillis()
            );
            if (!sondagemOperacionalProdutoService.produtoDisponivel(timeoutSondagem)) {
                if (deveEscalar(pendencia, controle, parametros)) {
                    repositorio.escalar(
                            pendencia.id(),
                            "SONDAGEM_FALHOU",
                            "Sondagem operacional falhou para o backend do produto."
                    );
                    escaladas++;
                } else {
                    repositorio.reagendar(
                            pendencia.id(),
                            agora.plusSeconds(overrideOuPadrao(
                                    controle == null ? null : controle.tempoEntreTentativasSegundosOverride(),
                                    parametros.tempoEntreTentativasSegundos()
                            )),
                            "SONDAGEM_FALHOU",
                            "Sondagem operacional falhou para o backend do produto."
                    );
                    reagendadas++;
                }
                continue;
            }
            try {
                executorPendenciaIntegracaoProdutoService.entregar(
                        pendencia,
                        overrideOuPadrao(
                                controle == null ? null : controle.timeoutEntregaMillisOverride(),
                                parametros.timeoutEntregaMillis()
                        )
                );
                repositorio.remover(pendencia.id());
                concluidas++;
            } catch (EntregaIntegracaoProdutoException ex) {
                if (!ex.isPassivelNovaTentativa() || deveEscalar(pendencia, controle, parametros)) {
                    repositorio.escalar(pendencia.id(), ex.getCodigoErro(), ex.getMessage());
                    escaladas++;
                    continue;
                }
                repositorio.reagendar(
                        pendencia.id(),
                        agora.plusSeconds(overrideOuPadrao(
                                controle == null ? null : controle.tempoEntreTentativasSegundosOverride(),
                                parametros.tempoEntreTentativasSegundos()
                        )),
                        ex.getCodigoErro(),
                        ex.getMessage()
                );
                reagendadas++;
            }
        }
        return new ResultadoEntregaIntegracaoProduto(
                true,
                recuperadas,
                pendencias.size(),
                concluidas,
                reagendadas,
                escaladas,
                pausadas
        );
    }

    private ParametrosOperacionaisSchedulerIntegracaoProduto mesclarParametrosPersistidos(
            final ParametrosPersistidosSchedulerIntegracaoProduto parametrosPersistidos) {
        return new ParametrosOperacionaisSchedulerIntegracaoProduto(
                parametrosPersistidos.habilitado(),
                parametrosPersistidos.tempoEntreTentativasSegundos(),
                parametrosPersistidos.quantidadeMaximaTentativas(),
                parametrosPersistidos.quantidadeMaximaItensPorCiclo(),
                parametrosPersistidos.timeoutSondagemMillis(),
                parametrosPersistidos.timeoutEntregaMillis(),
                properties.getTimeoutRecuperacaoProcessamentoSegundos()
        );
    }

    private ParametrosOperacionaisSchedulerIntegracaoProduto carregarFallbackProperties() {
        return new ParametrosOperacionaisSchedulerIntegracaoProduto(
                properties.isHabilitado(),
                properties.getTempoEntreTentativasSegundos(),
                properties.getQuantidadeMaximaTentativas(),
                properties.getQuantidadeMaximaItensPorCiclo(),
                properties.getTimeoutSondagemMillis(),
                properties.getTimeoutEntregaMillis(),
                properties.getTimeoutRecuperacaoProcessamentoSegundos()
        );
    }

    private boolean produtoEmManutencao(final ControleIntegracaoProduto controle, final OffsetDateTime agora) {
        if (controle == null) {
            return false;
        }
        if (!controle.escritasInternasHabilitadas()) {
            return true;
        }
        if (!controle.produtoEmManutencao()) {
            return false;
        }
        if (controle.inicioManutencao() != null && agora.isBefore(controle.inicioManutencao())) {
            return false;
        }
        return controle.fimManutencao() == null || !agora.isAfter(controle.fimManutencao());
    }

    private String motivoManutencao(final ControleIntegracaoProduto controle) {
        if (controle == null) {
            return "Produto temporariamente indisponivel para escritas internas.";
        }
        if (!controle.escritasInternasHabilitadas()) {
            return "Escritas internas desabilitadas para o produto.";
        }
        return controle.motivoManutencao() == null || controle.motivoManutencao().isBlank()
                ? "Produto em manutencao programada."
                : controle.motivoManutencao();
    }

    private boolean deveEscalar(final PendenciaIntegracaoProduto pendencia,
                                final ControleIntegracaoProduto controle,
                                final ParametrosOperacionaisSchedulerIntegracaoProduto parametros) {
        int maximo = overrideOuPadrao(
                controle == null ? null : controle.quantidadeMaximaTentativasOverride(),
                parametros.quantidadeMaximaTentativas()
        );
        return pendencia.tentativasRealizadas() + 1 >= maximo;
    }

    private int overrideOuPadrao(final Integer override, final int padrao) {
        return override == null ? padrao : override;
    }
}
