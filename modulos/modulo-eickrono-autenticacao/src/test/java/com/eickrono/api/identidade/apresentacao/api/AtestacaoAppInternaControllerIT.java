package com.eickrono.api.identidade.apresentacao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.dominio.modelo.DesafioAtestacaoApp;
import com.eickrono.api.identidade.dominio.repositorio.DesafioAtestacaoAppRepositorio;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(classes = AplicacaoApiIdentidade.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class AtestacaoAppInternaControllerIT {

    private static final String SEGREDO_INTERNO = "local-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DesafioAtestacaoAppRepositorio desafioRepositorio;

    @Test
    void deveGerarDesafioInternoComSegredoValido() throws Exception {
        desafioRepositorio.deleteAll();
        mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .with(jwtInternoThimisu())
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .header("X-Eickrono-Client-Ip", "10.10.10.1")
                        .header("X-Eickrono-Client-User-Agent", "JUnit/MockMvc")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "ANDROID",
                                  "usuarioSub": "sub-teste",
                                  "pessoaIdPerfil": 99,
                                  "cadastroId": "6cc70d55-3f39-4e2f-8f11-df45f0e3b911",
                                  "registroDispositivoId": "b4db9c0d-7068-44ff-b54e-bfd0a2db470e"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operacao").value("LOGIN"))
                .andExpect(jsonPath("$.plataforma").value("ANDROID"))
                .andExpect(jsonPath("$.provedorEsperado").value("GOOGLE_PLAY_INTEGRITY"))
                .andExpect(jsonPath("$.numeroProjetoNuvemAndroid").value("123456789"));

        assertThat(desafioRepositorio.findAll())
                .singleElement()
                .extracting(
                        DesafioAtestacaoApp::getIpSolicitante,
                        DesafioAtestacaoApp::getUserAgentSolicitante,
                        DesafioAtestacaoApp::getUsuarioSub,
                        DesafioAtestacaoApp::getPessoaIdPerfil,
                        DesafioAtestacaoApp::getCadastroId,
                        DesafioAtestacaoApp::getRegistroDispositivoId)
                .containsExactly(
                        "10.10.10.1",
                        "JUnit/MockMvc",
                        "sub-teste",
                        99L,
                        java.util.UUID.fromString("6cc70d55-3f39-4e2f-8f11-df45f0e3b911"),
                        java.util.UUID.fromString("b4db9c0d-7068-44ff-b54e-bfd0a2db470e"));
    }

    @Test
    void deveValidarComprovanteInternoEConsumirDesafio() throws Exception {
        desafioRepositorio.deleteAll();
        JsonNode desafio = criarDesafio("IOS");

        mockMvc.perform(post("/identidade/atestacoes/interna/validacoes")
                        .with(jwtInternoThimisu())
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "plataforma": "IOS",
                                  "provedor": "APPLE_APP_ATTEST",
                                  "tipoComprovante": "OBJETO_ATESTACAO",
                                  "identificadorDesafio": "%s",
                                  "desafioBase64": "%s",
                                  "conteudoComprovante": "objeto-atestacao-base64",
                                  "geradoEm": "2026-03-18T21:00:00Z",
                                  "chaveId": "key-id-ios"
                                }
                                """.formatted(
                                desafio.get("identificadorDesafio").asText(),
                                desafio.get("desafioBase64").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusValidacao").value("VALIDADA_LOCALMENTE"))
                .andExpect(jsonPath("$.identificadorDesafio").value(desafio.get("identificadorDesafio").asText()));

        assertThat(desafioRepositorio.findByIdentificadorDesafio(desafio.get("identificadorDesafio").asText()))
                .get()
                .extracting(DesafioAtestacaoApp::getConsumidoEm)
                .isNotNull();
    }

    @Test
    void deveRecusarSegredoInternoInvalido() throws Exception {
        desafioRepositorio.deleteAll();
        mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .with(jwtInternoThimisu())
                        .header("X-Eickrono-Internal-Secret", "segredo-invalido")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "ANDROID"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode criarDesafio(final String plataforma) throws Exception {
        String corpo = mockMvc.perform(post("/identidade/atestacoes/interna/desafios")
                        .with(jwtInternoThimisu())
                        .header("X-Eickrono-Internal-Secret", SEGREDO_INTERNO)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "operacao": "LOGIN",
                                  "plataforma": "%s"
                                }
                                """.formatted(plataforma)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(Objects.requireNonNull(corpo));
    }

    private RequestPostProcessor jwtInternoThimisu() {
        return jwt().jwt(jwt -> jwt
                .subject("service-account-identidade-servidor-interno")
                .claim("azp", "identidade-servidor-interno")
                .claim("preferred_username", "service-account-identidade-servidor-interno"));
    }
}
