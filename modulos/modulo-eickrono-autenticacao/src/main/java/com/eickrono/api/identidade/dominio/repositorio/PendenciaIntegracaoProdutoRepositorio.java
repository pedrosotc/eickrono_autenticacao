package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.aplicacao.modelo.ParametrosPersistidosSchedulerIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.ControleIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.NovaPendenciaIntegracaoProduto;
import com.eickrono.api.identidade.aplicacao.modelo.PendenciaIntegracaoProduto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendenciaIntegracaoProdutoRepositorio {

    void registrarOuAtualizar(NovaPendenciaIntegracaoProduto novaPendencia);

    Optional<ParametrosPersistidosSchedulerIntegracaoProduto> buscarParametrosGlobais();

    int recuperarPendenciasEmProcessamentoAbandonadas(OffsetDateTime limiteProcessamento, OffsetDateTime novaTentativa);

    List<PendenciaIntegracaoProduto> reservarPendenciasProcessaveis(String nomeInstancia, int quantidadeMaximaItensPorCiclo,
                                                                    OffsetDateTime referencia);

    Optional<ControleIntegracaoProduto> buscarControleProduto(long clienteEcossistemaId);

    void marcarPausadoManutencao(UUID pendenciaId, String motivoManutencao);

    void reagendar(UUID pendenciaId, OffsetDateTime proximaTentativa, String codigoUltimoErro, String mensagemUltimoErro);

    void escalar(UUID pendenciaId, String codigoUltimoErro, String mensagemUltimoErro);

    void remover(UUID pendenciaId);
}
