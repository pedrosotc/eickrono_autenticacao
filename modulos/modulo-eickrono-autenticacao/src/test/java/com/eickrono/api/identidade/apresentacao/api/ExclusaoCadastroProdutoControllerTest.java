package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.servico.ExclusaoCadastroProdutoDryRunService;
import com.eickrono.api.identidade.apresentacao.dto.admin.AlvosExclusaoCadastroProdutoApiResposta;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.admin.ExclusaoCadastroProdutoApiResposta;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExclusaoCadastroProdutoControllerTest {

    @Mock
    private ExclusaoCadastroProdutoDryRunService dryRunService;

    private ExclusaoCadastroProdutoController controller;

    @BeforeEach
    void setUp() {
        controller = new ExclusaoCadastroProdutoController(dryRunService);
    }

    @Test
    void deveDelegarDryRunParaServico() {
        ExclusaoCadastroProdutoApiRequest request = new ExclusaoCadastroProdutoApiRequest(
                "THIMISU",
                "pedrosotc",
                null,
                true,
                "QA"
        );
        ExclusaoCadastroProdutoApiResposta respostaEsperada = new ExclusaoCadastroProdutoApiResposta(
                "correlacao",
                true,
                new AlvosExclusaoCadastroProdutoApiResposta(
                        "THIMISU",
                        "pedrosotc",
                        null,
                        List.of(),
                        List.of()
                ),
                List.of(),
                List.of(),
                List.of()
        );
        when(dryRunService.simular(request)).thenReturn(respostaEsperada);

        var resposta = controller.simular(request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo(respostaEsperada);
        verify(dryRunService).simular(request);
    }
}
