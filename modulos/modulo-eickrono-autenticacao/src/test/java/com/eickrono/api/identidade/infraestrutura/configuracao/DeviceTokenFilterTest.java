package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.StatusValidacaoTokenDispositivo;

import jakarta.servlet.ServletException;

class DeviceTokenFilterTest {

    private TokenDispositivoServiceStub tokenDispositivoService;
    private DeviceTokenFilter filter;

    /**
     * Configura o filtro antes de cada cenário preparando um stub de TokenDispositivoService.
     * Assim controlamos quando o dispositivo será aceito ou bloqueado sem depender de integrações externas.
     */
    private void inicializarFiltro() {
        tokenDispositivoService = new TokenDispositivoServiceStub();
        filter = new DeviceTokenFilter(tokenDispositivoService);
        SecurityContextHolder.clearContext();
    }

    private void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Garante que o filtro não interfere em requisições sem autenticação JWT.
     * Ao simular um Authentication genérico, esperamos que o fluxo continue sem validação de token de dispositivo.
     */
    @Test
    void deveIgnorarQuandoNaoHaAutenticacaoJwt() throws ServletException, IOException {
        inicializarFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/identidade/perfil");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "credencial"));

        assertThat(filter).as("setUp deve inicializar o filtro antes de cada cenário").isNotNull();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tokenDispositivoService.ultimoUsuario()).isEmpty();
    }

    /**
     * Exercita o fluxo em que o cabeçalho X-Device-Token é informado, porém inválido.
     * O stub devolve o estado INVALIDO e esperamos a resposta 423 com código explícito.
     */
    @Test
    void deveRetornar423QuandoTokenInvalido() throws ServletException, IOException {
        inicializarFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/identidade/perfil");
        request.addHeader("X-Device-Token", "token-invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarCliente("usuario-xyz");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenDispositivoService.configurarResultado(new ResultadoValidacaoTokenDispositivo(
                StatusValidacaoTokenDispositivo.INVALIDO,
                null));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(423);
        assertThat(response.getContentAsString()).contains("DEVICE_TOKEN_INVALID");
    }

    /**
     * Valida o caminho feliz: token presente e reconhecido pelo serviço de dispositivos.
     * O stub devolve o mesmo TokenDispositivo e esperamos que o filtro permita a continuação da cadeia.
     */
    @Test
    void devePermitirQuandoTokenValido() throws ServletException, IOException {
        inicializarFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/identidade/perfil");
        request.addHeader("X-Device-Token", "token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarCliente("usuario-xyz");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenDispositivoService.configurarResultado(new ResultadoValidacaoTokenDispositivo(
                StatusValidacaoTokenDispositivo.VALIDO,
                criarToken().getExpiraEm()));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void deveExigirTokenQuandoJwtTemEscopoProtegidoSemRoleCliente() throws ServletException, IOException {
        inicializarFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/conta/avatar-preferido/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarComAutoridades(
                "usuario-xyz",
                List.of("SCOPE_vinculos:escrever"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(428);
        assertThat(response.getContentAsString()).contains("DEVICE_TOKEN_MISSING");
        assertThat(tokenDispositivoService.ultimoUsuario()).isEmpty();
    }

    /**
     * Garante que a reconciliacao offline tambem e bloqueada no proprio filtro quando o token
     * do dispositivo foi revogado. Isso impede que o app envie eventos offline com uma sessao
     * que ja perdeu validade no servidor.
     */
    @Test
    void deveBloquearEventosOfflineQuandoTokenRevogado() throws ServletException, IOException {
        inicializarFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/identidade/dispositivos/offline/eventos");
        request.addHeader("X-Device-Token", "token-revogado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarCliente("usuario-xyz");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenDispositivoService.configurarResultado(new ResultadoValidacaoTokenDispositivo(
                StatusValidacaoTokenDispositivo.REVOGADO,
                null));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(423);
        assertThat(response.getContentAsString()).contains("DEVICE_TOKEN_REVOKED");
    }

    /**
     * Exercita diretamente o método @AfterEach para garantir que o context seja limpo entre os testes.
     */
    @Test
    void deveLimparSecurityContextQuandoSolicitado() {
        inicializarFiltro();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "credencial"));

        limparContexto();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtAuthenticationToken autenticarCliente(String sub) {
        return autenticarComAutoridades(sub, List.of("ROLE_cliente"));
    }

    private JwtAuthenticationToken autenticarComAutoridades(String sub, List<String> authorities) {
        Jwt jwt = Jwt.withTokenValue("teste")
                .header("alg", "none")
                .subject(sub)
                .build();
        return new JwtAuthenticationToken(
                jwt,
                authorities.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private TokenDispositivo criarToken() {
        RegistroDispositivo registro = new RegistroDispositivo(
                UUID.randomUUID(),
                "usuario-xyz",
                "teste@eickrono.com",
                "+551199999999",
                "fingerprint",
                "plataforma",
                "1.0.0",
                null,
                StatusRegistroDispositivo.CONFIRMADO,
                OffsetDateTime.now(Clock.systemUTC()),
                OffsetDateTime.now(Clock.systemUTC()).plusHours(9)
        );
        return new TokenDispositivo(
                UUID.randomUUID(),
                registro,
                criarDispositivo(),
                "usuario-xyz",
                "fingerprint",
                "plataforma",
                "1.0.0",
                "hash",
                StatusTokenDispositivo.ATIVO,
                OffsetDateTime.now(Clock.systemUTC()),
                OffsetDateTime.now(Clock.systemUTC()).plusHours(24)
        );
    }

    private DispositivoIdentidade criarDispositivo() {
        OffsetDateTime agora = OffsetDateTime.now(Clock.systemUTC());
        Pessoa pessoa = new Pessoa(
                "usuario-xyz",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                agora);
        return new DispositivoIdentidade(
                pessoa,
                "fingerprint",
                "plataforma",
                "1.0.0",
                null,
                StatusDispositivoIdentidade.ATIVO,
                agora,
                agora);
    }

    private static class TokenDispositivoServiceStub extends com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService {

        private ResultadoValidacaoTokenDispositivo resultado =
                new ResultadoValidacaoTokenDispositivo(StatusValidacaoTokenDispositivo.INVALIDO, null);
        private Optional<String> ultimoUsuario = Optional.empty();

        TokenDispositivoServiceStub() {
            super(null, new com.eickrono.api.identidade.infraestrutura.configuracao.DispositivoProperties(), Clock.systemUTC());
        }

        void configurarResultado(ResultadoValidacaoTokenDispositivo resultado) {
            this.resultado = resultado;
        }

        Optional<String> ultimoUsuario() {
            return ultimoUsuario;
        }

        @Override
        public ResultadoValidacaoTokenDispositivo validarToken(String usuarioSub, String tokenClaro) {
            this.ultimoUsuario = Optional.ofNullable(usuarioSub);
            return resultado;
        }

        @Override
        public TokenEmitido emitirToken(RegistroDispositivo registro,
                                        DispositivoIdentidade dispositivo,
                                        String usuarioSub) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revogarTokensAtivos(String usuarioSub, MotivoRevogacaoToken motivo) {
            throw new UnsupportedOperationException();
        }
    }
}
