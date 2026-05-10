package com.eickrono.api.identidade.aplicacao.servico;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.ResultadoEntregaIntegracaoProduto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegracaoProdutoPendenteSchedulerTest {

    @Mock
    private PendenciaIntegracaoProdutoService service;

    @Test
    @DisplayName("deve delegar a execucao do ciclo ao servico")
    void deveDelegarExecucaoDoCicloAoServico() {
        when(service.executarCiclo()).thenReturn(new ResultadoEntregaIntegracaoProduto(true, 1, 3, 1, 1, 0, 0));
        IntegracaoProdutoPendenteScheduler scheduler = new IntegracaoProdutoPendenteScheduler(service);

        scheduler.executar();

        verify(service).executarCiclo();
    }
}
