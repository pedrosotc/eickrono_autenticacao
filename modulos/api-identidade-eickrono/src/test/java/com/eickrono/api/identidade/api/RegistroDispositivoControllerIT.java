package com.eickrono.api.identidade.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import com.eickrono.api.identidade.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.servico.CanalEnvioCodigo;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistroDispositivoControllerIT {

    private static final String REGISTRO_ENDPOINT = "/identidade/dispositivos/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CodigoCapturador codigoCapturador;

    @Autowired
    private TokenDispositivoRepositorio tokenDispositivoRepositorio;

    @Test
    void fluxoCompletoDeRegistroConfirmacaoERevogacao() throws Exception {
        RegistroDispositivoResponse registro = solicitarRegistro();

        String codigoSms = codigoCapturador.obterCodigo(registro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Código SMS não capturado"));
        String codigoEmail = codigoCapturador.obterCodigo(registro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Código e-mail não capturado"));

        ConfirmacaoRegistroResponse confirmacao = confirmarRegistro(registro.registroId(), codigoSms, codigoEmail);

        assertThat(confirmacao.tokenDispositivo()).isNotBlank();

        // GET com token válido deve passar pelo filtro e chegar à controller (mesmo que retorne 404)
        mockMvc.perform(get("/identidade/perfil")
                        .with(clienteJwt())
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isNotFound());

        // Sem o cabeçalho obrigatório deve retornar 428
        mockMvc.perform(get("/identidade/perfil")
                        .with(clienteJwt()))
                .andExpect(status().isPreconditionRequired());

        // Token inválido retorna 423
        mockMvc.perform(get("/identidade/perfil")
                        .with(clienteJwt())
                        .header("X-Device-Token", "token-invalido"))
                .andExpect(status().isLocked());

        // Revogação
        mockMvc.perform(post("/identidade/dispositivos/revogar")
                        .with(clienteJwt())
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"SOLICITACAO_CLIENTE\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/identidade/perfil")
                        .with(clienteJwt())
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isLocked());

        Optional<TokenDispositivo> tokenPersistido = tokenDispositivoRepositorio.findAll().stream().findFirst();
        assertThat(tokenPersistido).isPresent();
        assertThat(tokenPersistido.get().getMotivoRevogacao()).contains(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
    }

    @Test
    void reenviarCodigoRespeitaLimites() throws Exception {
        RegistroDispositivoResponse registro = solicitarRegistro();

        mockMvc.perform(post(REGISTRO_ENDPOINT + "/" + registro.registroId() + "/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted());
    }

    private RegistroDispositivoResponse solicitarRegistro() throws Exception {
        String payload = """
                {
                  "email": "teste@eickrono.com",
                  "telefone": "+55-11-99999-0000",
                  "fingerprint": "ios|iphone14,3|device",
                  "plataforma": "iOS",
                  "versaoAplicativo": "1.0.0"
                }
                """;

        MvcResult resultado = mockMvc.perform(post(REGISTRO_ENDPOINT)
                        .with(clienteJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        return objectMapper.readValue(resultado.getResponse().getContentAsByteArray(), RegistroDispositivoResponse.class);
    }

    private ConfirmacaoRegistroResponse confirmarRegistro(UUID registroId, String codigoSms, String codigoEmail) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "codigoSms", codigoSms,
                "codigoEmail", codigoEmail
        ));

        MvcResult resultado = mockMvc.perform(post(REGISTRO_ENDPOINT + "/" + registroId + "/confirmacao")
                        .with(clienteJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(resultado.getResponse().getContentAsByteArray(), ConfirmacaoRegistroResponse.class);
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor clienteJwt() {
        return jwt().jwt(builder -> builder
                        .subject("usuario-xyz")
                        .claim("scope", "identidade:ler"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_cliente"),
                        new SimpleGrantedAuthority("SCOPE_identidade:ler"));
    }

    @TestConfiguration
    static class CanalEnvioCodigoTestConfiguration {

        @Bean
        CodigoCapturador codigoCapturador() {
            return new CodigoCapturador();
        }

        @Bean(name = "canalEnvioCodigoSmsLog")
        CanalEnvioCodigo canalEnvioSms(CodigoCapturador capturador) {
            return new CanalEnvioCodigo() {
                @Override
                public CanalVerificacao canal() {
                    return CanalVerificacao.SMS;
                }

                @Override
                public void enviar(RegistroDispositivo registro, String destino, String codigo) {
                    capturador.registrar(registro.getId(), CanalVerificacao.SMS, codigo);
                }
            };
        }

        @Bean(name = "canalEnvioCodigoEmailLog")
        CanalEnvioCodigo canalEnvioEmail(CodigoCapturador capturador) {
            return new CanalEnvioCodigo() {
                @Override
                public CanalVerificacao canal() {
                    return CanalVerificacao.EMAIL;
                }

                @Override
                public void enviar(RegistroDispositivo registro, String destino, String codigo) {
                    capturador.registrar(registro.getId(), CanalVerificacao.EMAIL, codigo);
                }
            };
        }
    }

    static class CodigoCapturador {
        private final Map<CanalVerificacao, Map<UUID, String>> mapa = new ConcurrentHashMap<>();

        void registrar(UUID registroId, CanalVerificacao canal, String codigo) {
            mapa.computeIfAbsent(canal, c -> new ConcurrentHashMap<>())
                    .put(registroId, codigo);
        }

        Optional<String> obterCodigo(UUID registroId, CanalVerificacao canal) {
            return Optional.ofNullable(mapa.getOrDefault(canal, Map.of()).get(registroId));
        }
    }
}
