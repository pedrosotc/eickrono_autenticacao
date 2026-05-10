package com.eickrono.api.identidade.apresentacao.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.servico.CadastroContaInternaServico;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class CadastroInternoControllerIT {

    private static final String SEGREDO_INTERNO = "local-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CadastroContaInternaServico cadastroContaInternaServico;

    @Test
    void deveConsultarDisponibilidadeInternaDoUsuarioPorSistema() throws Exception {
        when(cadastroContaInternaServico.identificadorPublicoSistemaDisponivel(" Ana.Souza ", "thimisu-backend"))
                .thenReturn(true);

        mockMvc.perform(get("/identidade/perfis-sistema/interna/disponibilidade")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("service-account-thimisu-backend-interno")
                                .claim("azp", "thimisu-backend-interno")
                                .claim("preferred_username", "service-account-thimisu-backend-interno")))
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .header("X-Eickrono-Calling-System", "thimisu-backend")
                        .param("identificadorPublicoSistema", " Ana.Souza "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorPublicoSistema").value("ana.souza"))
                .andExpect(jsonPath("$.disponivel").value(true));

        verify(cadastroContaInternaServico).identificadorPublicoSistemaDisponivel(" Ana.Souza ", "thimisu-backend");
    }
}
