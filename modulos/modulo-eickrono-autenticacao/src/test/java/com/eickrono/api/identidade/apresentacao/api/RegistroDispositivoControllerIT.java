package com.eickrono.api.identidade.apresentacao.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eickrono.api.identidade.AplicacaoApiIdentidade;
import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ClienteContextoPessoaPerfilSistema;
import com.eickrono.api.identidade.apresentacao.dto.ConfirmacaoRegistroResponse;
import com.eickrono.api.identidade.apresentacao.dto.PoliticaOfflineDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoResponse;
import com.eickrono.api.identidade.apresentacao.dto.RegistroDispositivoSessaoResponse;
import com.eickrono.api.identidade.apresentacao.dto.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.identidade.dominio.modelo.CanalVerificacao;
import com.eickrono.api.identidade.dominio.modelo.EventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TipoEventoOfflineDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.EventoOfflineDispositivoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.TokenDispositivoRepositorio;
import com.eickrono.api.identidade.support.ClienteAdministracaoCadastroKeycloakStubConfiguration;
import com.eickrono.api.identidade.support.InfraestruturaTesteIdentidade;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {
        AplicacaoApiIdentidade.class,
        RegistroDispositivoControllerITConfiguration.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({
        RegistroDispositivoControllerITConfiguration.class,
        ClienteAdministracaoCadastroKeycloakStubConfiguration.class
})
@ContextConfiguration(initializers = InfraestruturaTesteIdentidade.Initializer.class)
class RegistroDispositivoControllerIT {

    private static final String REGISTRO_ENDPOINT = "/identidade/dispositivos/registro";
    private static final String REGISTRO_ENDPOINT_CONTA = "/api/conta/dispositivos/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CodigoCapturador codigoCapturador;

    @Autowired
    private TokenDispositivoRepositorio tokenDispositivoRepositorio;

    @Autowired
    private EventoOfflineDispositivoRepositorio eventoOfflineDispositivoRepositorio;

    @Autowired
    private ClienteAdministracaoCadastroKeycloakStubConfiguration keycloakStub;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockBean
    private ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;

    private void prepararContextoDeTeste() {
        keycloakStub.limparIdentidadesFederadas();
        codigoCapturador.limpar();
        jdbcTemplate.getJdbcOperations().update("""
                TRUNCATE TABLE
                    dispositivos.tokens_dispositivo,
                    dispositivos.dispositivos_confiaveis,
                    dispositivos.codigos_verificacao_dispositivo,
                    dispositivos.registros_dispositivo,
                    eventos_offline_dispositivo,
                    token_dispositivo,
                    dispositivos_identidade,
                    codigo_verificacao,
                    registro_dispositivo,
                    cadastros_conta,
                    autenticacao.usuarios_clientes_ecossistema,
                    autenticacao.usuarios_formas_acesso,
                    autenticacao.usuarios
                CASCADE
                """);
        ContextoPessoaPerfilSistema contexto = new ContextoPessoaPerfilSistema(
                123L,
                "usuario-xyz",
                "teste@eickrono.com",
                "Usuario Teste",
                "usuario.teste",
                null,
                "ATIVO"
        );
        when(clienteContextoPessoaPerfilSistema.buscarPorSub("usuario-xyz"))
                .thenReturn(Optional.of(contexto));
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("teste@eickrono.com"))
                .thenReturn(Optional.of(contexto));
    }

    private MockMvc mockMvc() {
        return Objects.requireNonNull(mockMvc);
    }

    private ObjectMapper objectMapper() {
        return Objects.requireNonNull(objectMapper);
    }

    private CodigoCapturador codigoCapturador() {
        return Objects.requireNonNull(codigoCapturador);
    }

    private TokenDispositivoRepositorio tokenDispositivoRepositorio() {
        return Objects.requireNonNull(tokenDispositivoRepositorio);
    }

    private EventoOfflineDispositivoRepositorio eventoOfflineDispositivoRepositorio() {
        return Objects.requireNonNull(eventoOfflineDispositivoRepositorio);
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        return Objects.requireNonNull(jdbcTemplate);
    }

    @Test
    void fluxoCompletoDeRegistroConfirmacaoERevogacao() throws Exception {
        prepararContextoDeTeste();

        RegistroDispositivoResponse registro = solicitarRegistro();
        assertThat(registro.status()).isEqualTo(StatusRegistroDispositivo.PENDENTE);
        assertThat(registro.canaisConfirmacao()).containsExactlyInAnyOrder(CanalVerificacao.EMAIL, CanalVerificacao.SMS);

        String codigoSms = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Código SMS não capturado"));
        String codigoEmail = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Código e-mail não capturado"));

        ConfirmacaoRegistroResponse confirmacao = confirmarRegistro(registro.registroId(), codigoSms, codigoEmail);

        assertThat(confirmacao.tokenDispositivo()).isNotBlank();

        // GET com token válido deve passar pelo filtro; o endpoint em si está desativado e responde 410.
        mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isGone());

        // Sem o cabeçalho obrigatório deve retornar 428
        mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt())))
                .andExpect(status().isPreconditionRequired());
        MvcResult semCabecalho = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt())))
                .andExpect(status().isPreconditionRequired())
                .andReturn();
        assertThat(semCabecalho.getResponse().getContentAsString()).contains("DEVICE_TOKEN_MISSING");

        // Token inválido retorna 423
        MvcResult tokenInvalido = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", "token-invalido"))
                .andExpect(status().isLocked())
                .andReturn();
        assertThat(tokenInvalido.getResponse().getContentAsString()).contains("DEVICE_TOKEN_INVALID");

        MvcResult validacao = mockMvc().perform(get("/identidade/dispositivos/token/validacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        ValidacaoTokenDispositivoResponse payloadValidacao = objectMapper().readValue(
                validacao.getResponse().getContentAsByteArray(),
                ValidacaoTokenDispositivoResponse.class);
        assertThat(payloadValidacao.valido()).isTrue();
        assertThat(payloadValidacao.codigo()).isEqualTo("DEVICE_TOKEN_VALID");

        MvcResult validacaoInterna = mockMvc().perform(get("/api/conta/dispositivos/token/validacao/interna")
                        .with(Objects.requireNonNull(clienteJwtInterno()))
                        .header("X-Eickrono-Internal-Secret", "local-internal-secret")
                        .header("X-Usuario-Sub", "usuario-xyz")
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        ValidacaoTokenDispositivoResponse payloadInterno = objectMapper().readValue(
                validacaoInterna.getResponse().getContentAsByteArray(),
                ValidacaoTokenDispositivoResponse.class);
        assertThat(payloadInterno.valido()).isTrue();

        MvcResult politicaOffline = mockMvc().perform(get("/identidade/dispositivos/offline/politica")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andReturn();
        PoliticaOfflineDispositivoResponse politica = objectMapper().readValue(
                politicaOffline.getResponse().getContentAsByteArray(),
                PoliticaOfflineDispositivoResponse.class);
        assertThat(politica.permitido()).isTrue();
        assertThat(politica.exigeReconciliacao()).isTrue();
        assertThat(politica.condicoesBloqueio()).contains("TOKEN_REVOGADO");
        assertThat(politica.eventosPermitidos())
                .contains("MODO_OFFLINE_ATIVADO", "RECONCILIACAO_REALIZADA")
                .doesNotContain("ACESSO_OFFLINE_LIBERADO");

        String payloadEventosOffline = Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                "eventos", java.util.List.of(Map.of(
                        "tipoEvento", TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO.name(),
                        "detalhes", "usuario entrou em modo offline"
                )))));
        mockMvc().perform(post("/identidade/dispositivos/offline/eventos")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payloadEventosOffline))
                .andExpect(status().isAccepted());

        assertThat(eventoOfflineDispositivoRepositorio().findAll())
                .extracting(EventoOfflineDispositivo::getTipoEvento)
                .contains(TipoEventoOfflineDispositivo.MODO_OFFLINE_ATIVADO);

        String payloadEventoNaoPermitido = Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                "eventos", java.util.List.of(Map.of(
                        "tipoEvento", TipoEventoOfflineDispositivo.ACESSO_OFFLINE_LIBERADO.name(),
                        "detalhes", "evento sensivel nao permitido"
                )))));
        mockMvc().perform(post("/identidade/dispositivos/offline/eventos")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payloadEventoNaoPermitido))
                .andExpect(status().isBadRequest());

        // Revogação
        mockMvc().perform(post("/identidade/dispositivos/revogar")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo())
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("{\"motivo\":\"SOLICITACAO_CLIENTE\"}"))
                .andExpect(status().isNoContent());

        MvcResult revogado = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isLocked())
                .andReturn();
        assertThat(revogado.getResponse().getContentAsString()).contains("DEVICE_TOKEN_REVOKED");

        Optional<TokenDispositivo> tokenPersistido = tokenDispositivoRepositorio().findAll().stream().findFirst();
        assertThat(tokenPersistido).isPresent();
        assertThat(tokenPersistido.get().getMotivoRevogacao()).contains(MotivoRevogacaoToken.SOLICITACAO_CLIENTE);
    }

    @Test
    void deveAceitarFluxoInterativoPelaRotaCanonicaDeConta() throws Exception {
        prepararContextoDeTeste();

        RegistroDispositivoResponse registro = solicitarRegistro(
                REGISTRO_ENDPOINT_CONTA,
                "ios|iphone14,3|device-canonico"
        );
        assertThat(registro.status()).isEqualTo(StatusRegistroDispositivo.PENDENTE);

        String codigoSms = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Codigo SMS nao capturado"));
        String codigoEmail = codigoCapturador().obterCodigo(registro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Codigo e-mail nao capturado"));

        ConfirmacaoRegistroResponse confirmacao = confirmarRegistro(
                REGISTRO_ENDPOINT_CONTA,
                registro.registroId(),
                codigoSms,
                codigoEmail
        );

        mockMvc().perform(get("/api/conta/dispositivos/offline/politica")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", confirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permitido").value(true));
    }

    @Test
    void deveRevogarTokenAnteriorQuandoNovoDispositivoForConfirmado() throws Exception {
        prepararContextoDeTeste();

        RegistroDispositivoResponse primeiroRegistro = solicitarRegistro("ios|iphone14,3|device-1");
        String primeiroCodigoSms = codigoCapturador().obterCodigo(primeiroRegistro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Codigo SMS do primeiro dispositivo nao capturado"));
        String primeiroCodigoEmail = codigoCapturador().obterCodigo(primeiroRegistro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Codigo e-mail do primeiro dispositivo nao capturado"));
        ConfirmacaoRegistroResponse primeiraConfirmacao = confirmarRegistro(
                primeiroRegistro.registroId(),
                primeiroCodigoSms,
                primeiroCodigoEmail
        );

        mockMvc().perform(get("/identidade/dispositivos/token/validacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", primeiraConfirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("DEVICE_TOKEN_VALID"));

        RegistroDispositivoResponse segundoRegistro = solicitarRegistro("ios|iphone14,3|device-2");
        String segundoCodigoSms = codigoCapturador().obterCodigo(segundoRegistro.registroId(), CanalVerificacao.SMS)
                .orElseThrow(() -> new IllegalStateException("Codigo SMS do segundo dispositivo nao capturado"));
        String segundoCodigoEmail = codigoCapturador().obterCodigo(segundoRegistro.registroId(), CanalVerificacao.EMAIL)
                .orElseThrow(() -> new IllegalStateException("Codigo e-mail do segundo dispositivo nao capturado"));
        ConfirmacaoRegistroResponse segundaConfirmacao = confirmarRegistro(
                segundoRegistro.registroId(),
                segundoCodigoSms,
                segundoCodigoEmail
        );

        mockMvc().perform(get("/identidade/dispositivos/token/validacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", segundaConfirmacao.tokenDispositivo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("DEVICE_TOKEN_VALID"));

        MvcResult tokenAnteriorRevogado = mockMvc().perform(get("/identidade/perfil")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .header("X-Device-Token", primeiraConfirmacao.tokenDispositivo()))
                .andExpect(status().isLocked())
                .andReturn();
        assertThat(tokenAnteriorRevogado.getResponse().getContentAsString()).contains("DEVICE_TOKEN_REVOKED");

        List<String> fingerprintsAtivos = jdbcTemplate().getJdbcOperations().queryForList("""
                SELECT registro.fingerprint
                FROM token_dispositivo token
                JOIN registro_dispositivo registro
                  ON registro.id = token.registro_id
                WHERE token.status = 'ATIVO'
                ORDER BY registro.fingerprint
                """, String.class);
        assertThat(fingerprintsAtivos)
                .containsExactly("ios|iphone14,3|device-2");

        List<String> motivosRevogacao = jdbcTemplate().getJdbcOperations().queryForList("""
                SELECT token.motivo_revogacao
                FROM token_dispositivo token
                WHERE token.status = 'REVOGADO'
                """, String.class);
        assertThat(motivosRevogacao)
                .contains(MotivoRevogacaoToken.NOVO_DISPOSITIVO_CONFIRMANDO.name());
    }

    @Test
    void reenviarCodigoRespeitaLimites() throws Exception {
        prepararContextoDeTeste();

        RegistroDispositivoResponse registro = solicitarRegistro();

        mockMvc().perform(post(REGISTRO_ENDPOINT + "/" + registro.registroId() + "/reenviar")
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void deveRegistrarSessaoSilenciosaQuandoContextoDoProdutoExistir() throws Exception {
        prepararContextoDeTeste();

        criarCadastroCentralConcluido("usuario-xyz", "teste@eickrono.com", "usuario.teste");

        MvcResult resultado = mockMvc().perform(post("/identidade/dispositivos/registro/silencioso")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "plataforma": "IOS",
                                  "identificadorInstalacao": "instalacao-silenciosa",
                                  "modelo": "iphone17,1",
                                  "fabricante": "apple",
                                  "sistemaOperacional": "ios",
                                  "versaoSistema": "18.0",
                                  "versaoApp": "1.0.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        RegistroDispositivoSessaoResponse resposta = objectMapper().readValue(
                resultado.getResponse().getContentAsByteArray(),
                RegistroDispositivoSessaoResponse.class
        );
        assertThat(resposta.tokenDispositivo()).isNotBlank();
        assertThat(resposta.tokenDispositivoExpiraEm()).isNotNull();
    }

    @Test
    void deveRetornarSocialSemContaLocalQuandoSubAutenticadoNaoPossuirContextoNoProjeto() throws Exception {
        prepararContextoDeTeste();

        when(clienteContextoPessoaPerfilSistema.buscarPorSub("usuario-xyz"))
                .thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("teste@eickrono.com"))
                .thenReturn(Optional.empty());
        keycloakStub.definirIdentidadesFederadas(
                "usuario-xyz",
                java.util.List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com",
                        "Pessoa Google",
                        "https://cdn.eickrono.test/google.png")));

        mockMvc().perform(post("/identidade/dispositivos/registro/silencioso")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "plataforma": "IOS",
                                  "identificadorInstalacao": "instalacao-social",
                                  "modelo": "iphone17,1",
                                  "fabricante": "apple",
                                  "sistemaOperacional": "ios",
                                  "versaoSistema": "18.0",
                                  "versaoApp": "1.0.0"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("social_sem_conta_local"))
                .andExpect(jsonPath("$.detalhes.acaoSugerida").value("ABRIR_CADASTRO"))
                .andExpect(jsonPath("$.detalhes.email").value("teste@eickrono.com"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"))
                .andExpect(jsonPath("$.detalhes.identificadorExterno").value("google-sub-1"));
    }

    @Test
    void deveRetornarEntrarEVincularQuandoJaExistirContaLocalNoProjetoComMesmoEmail() throws Exception {
        prepararContextoDeTeste();

        String emailSocial = "conta.existente@eickrono.com";
        when(clienteContextoPessoaPerfilSistema.buscarPorSub("usuario-sem-contexto"))
                .thenReturn(Optional.empty());
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail(emailSocial))
                .thenReturn(Optional.empty());
        criarContaLocalDoProjeto(emailSocial, "pedrosotc");
        keycloakStub.definirIdentidadesFederadas(
                "usuario-sem-contexto",
                java.util.List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-2",
                        emailSocial,
                        "Conta Existente",
                        "https://cdn.eickrono.test/google-vincular.png")));

        mockMvc().perform(post("/identidade/dispositivos/registro/silencioso")
                        .with(Objects.requireNonNull(clienteJwt("usuario-sem-contexto", emailSocial)))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content("""
                                {
                                  "aplicacaoId": "eickrono-thimisu-app",
                                  "plataforma": "IOS",
                                  "identificadorInstalacao": "instalacao-social-vincular",
                                  "modelo": "iphone17,1",
                                  "fabricante": "apple",
                                  "sistemaOperacional": "ios",
                                  "versaoSistema": "18.0",
                                  "versaoApp": "1.0.0"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("social_sem_conta_local"))
                .andExpect(jsonPath("$.detalhes.acaoSugerida").value("ENTRAR_E_VINCULAR"))
                .andExpect(jsonPath("$.detalhes.email").value(emailSocial))
                .andExpect(jsonPath("$.detalhes.emailContaExistente").value(emailSocial))
                .andExpect(jsonPath("$.detalhes.loginSugerido").value("pedrosotc"))
                .andExpect(jsonPath("$.detalhes.provedor").value("google"))
                .andExpect(jsonPath("$.detalhes.identificadorExterno").value("google-sub-2"));
    }

    private void criarCadastroCentralConcluido(final String sub, final String email, final String usuario) {
        UUID cadastroId = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cadastroId", cadastroId)
                .addValue("sub", sub)
                .addValue("email", email)
                .addValue("usuario", usuario)
                .addValue("perfilSistemaId", UUID.randomUUID().toString())
                .addValue("protocoloSuporte", "cad-" + cadastroId.toString().replace("-", ""));
        jdbcTemplate().update("""
                INSERT INTO cadastros_conta (
                    cadastro_id,
                    subject_remoto,
                    pessoa_id_perfil,
                    usuario_id_perfil,
                    tipo_pessoa,
                    nome_completo,
                    usuario,
                    email_principal,
                    status,
                    codigo_email_hash,
                    codigo_email_gerado_em,
                    codigo_email_expira_em,
                    email_confirmado_em,
                    sistema_solicitante,
                    criado_em,
                    atualizado_em,
                    protocolo_suporte
                ) VALUES (
                    :cadastroId,
                    :sub,
                    123,
                    :perfilSistemaId,
                    'FISICA',
                    'Usuario Teste',
                    :usuario,
                    :email,
                    'EMAIL_CONFIRMADO',
                    'hash-email-teste',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP,
                    'eickrono-thimisu-app',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    :protocoloSuporte
                )
                """, params);
    }

    private RegistroDispositivoResponse solicitarRegistro() throws Exception {
        return solicitarRegistro(REGISTRO_ENDPOINT, "ios|iphone14,3|device");
    }

    private RegistroDispositivoResponse solicitarRegistro(final String fingerprint) throws Exception {
        return solicitarRegistro(REGISTRO_ENDPOINT, fingerprint);
    }

    private RegistroDispositivoResponse solicitarRegistro(final String endpoint, final String fingerprint) throws Exception {
        String payload = """
                {
                  "email": "teste@eickrono.com",
                  "telefone": "+55-11-99999-0000",
                  "fingerprint": "%s",
                  "plataforma": "iOS",
                  "versaoAplicativo": "1.0.0"
                }
                """.formatted(fingerprint);

        MvcResult resultado = mockMvc().perform(post(endpoint)
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();

        return objectMapper().readValue(resultado.getResponse().getContentAsByteArray(), RegistroDispositivoResponse.class);
    }

    private ConfirmacaoRegistroResponse confirmarRegistro(UUID registroId, String codigoSms, String codigoEmail) throws Exception {
        return confirmarRegistro(REGISTRO_ENDPOINT, registroId, codigoSms, codigoEmail);
    }

    private ConfirmacaoRegistroResponse confirmarRegistro(
            final String endpoint,
            UUID registroId,
            String codigoSms,
            String codigoEmail) throws Exception {
        String payload = Objects.requireNonNull(objectMapper().writeValueAsString(Map.of(
                "codigoSms", codigoSms,
                "codigoEmail", codigoEmail
        )));

        MvcResult resultado = mockMvc().perform(post(endpoint + "/" + registroId + "/confirmacao")
                        .with(Objects.requireNonNull(clienteJwt()))
                        .contentType(Objects.requireNonNull(jsonMediaType()))
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper().readValue(resultado.getResponse().getContentAsByteArray(), ConfirmacaoRegistroResponse.class);
    }

    private RequestPostProcessor clienteJwt() {
        return clienteJwt("usuario-xyz", "teste@eickrono.com");
    }

    private RequestPostProcessor clienteJwt(final String sub, final String email) {
        return Objects.requireNonNull(jwt().jwt(builder -> builder
                        .subject(sub)
                        .claim("email", email)
                        .claim("name", "Usuario Teste")
                        .claim("preferred_username", "usuario.teste")
                        .claim("scope", "identidade:ler"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_cliente"),
                        new SimpleGrantedAuthority("SCOPE_identidade:ler")));
    }

    private RequestPostProcessor clienteJwtInterno() {
        return Objects.requireNonNull(jwt().jwt(builder -> builder
                        .subject("service-account-eickrono-keycloak")
                        .claim("azp", "eickrono-keycloak")
                        .claim("preferred_username", "service-account-eickrono-keycloak")));
    }

    private MediaType jsonMediaType() {
        return Objects.requireNonNull(MediaType.APPLICATION_JSON);
    }

    private void criarContaLocalDoProjeto(final String email, final String loginSugerido) {
        UUID usuarioId = UUID.randomUUID();
        MapSqlParameterSource paramsUsuario = new MapSqlParameterSource()
                .addValue("usuarioId", usuarioId)
                .addValue("pessoaId", UUID.randomUUID());
        jdbcTemplate().update("""
                INSERT INTO autenticacao.usuarios (
                    id,
                    pessoa_id,
                    sub_remoto,
                    status_global,
                    credencial_local_habilitada,
                    criado_em,
                    atualizado_em
                ) VALUES (
                    :usuarioId,
                    :pessoaId,
                    NULL,
                    'ATIVO',
                    TRUE,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """, paramsUsuario);

        MapSqlParameterSource paramsFormaAcesso = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("usuarioId", usuarioId)
                .addValue("email", email);
        jdbcTemplate().update("""
                INSERT INTO autenticacao.usuarios_formas_acesso (
                    id,
                    usuario_id,
                    tipo,
                    provedor,
                    identificador_externo,
                    principal,
                    verificado_em,
                    vinculado_em
                ) VALUES (
                    :id,
                    :usuarioId,
                    'EMAIL_SENHA',
                    'EMAIL',
                    :email,
                    TRUE,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """, paramsFormaAcesso);

        MapSqlParameterSource paramsVinculo = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("usuarioId", usuarioId)
                .addValue("loginSugerido", loginSugerido);
        jdbcTemplate().update("""
                INSERT INTO autenticacao.usuarios_clientes_ecossistema (
                    id,
                    usuario_id,
                    cliente_ecossistema_id,
                    status_vinculo,
                    identificador_publico_cliente,
                    ultimo_acesso_em,
                    vinculado_em,
                    atualizado_em
                )
                SELECT
                    :id,
                    :usuarioId,
                    cliente.id,
                    'LIBERADO',
                    :loginSugerido,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM catalogo.clientes_ecossistema cliente
                WHERE cliente.codigo = 'eickrono-thimisu-app'
                """, paramsVinculo);
    }

    static class CodigoCapturador {
        private final Map<CanalVerificacao, Map<UUID, String>> mapa = new ConcurrentHashMap<>();

        void registrar(UUID registroId, CanalVerificacao canal, String codigo) {
            mapa.computeIfAbsent(canal, c -> new ConcurrentHashMap<>())
                    .put(registroId, codigo);
        }

        void limpar() {
            mapa.clear();
        }

        Optional<String> obterCodigo(UUID registroId, CanalVerificacao canal) {
            return Optional.ofNullable(mapa.getOrDefault(canal, Map.of()).get(registroId));
        }
    }
}
