package com.eickrono.api.identidade.configuracao;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.eickrono.api.identidade.dominio.modelo.MotivoRevogacaoToken;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusRegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;

import jakarta.servlet.ServletException;

class DeviceTokenFilterTest {

    private TokenDispositivoServiceStub tokenDispositivoService;
    private DeviceTokenFilter filter;

    /**
     * Configura o filtro antes de cada cenário preparando um stub de TokenDispositivoService.
     * Assim controlamos quando o dispositivo será aceito ou bloqueado sem depender de integrações externas.
     */
    @SuppressWarnings("unused")
    @BeforeEach
    void setUp() {
        tokenDispositivoService = new TokenDispositivoServiceStub();
        filter = new DeviceTokenFilter(tokenDispositivoService);
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unused")
    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Garante que o filtro não interfere em requisições sem autenticação JWT.
     * Ao simular um Authentication genérico, esperamos que o fluxo continue sem validação de token de dispositivo.
     */
    @Test
    void deveIgnorarQuandoNaoHaAutenticacaoJwt() throws ServletException, IOException {
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
     * O stub retorna Optional.empty() e esperamos a resposta 423 (Locked).
     */
    @Test
    void deveRetornar423QuandoTokenInvalido() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/identidade/perfil");
        request.addHeader("X-Device-Token", "token-invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarCliente("usuario-xyz");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenDispositivoService.configurarResultado(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(423);
    }

    /**
     * Valida o caminho feliz: token presente e reconhecido pelo serviço de dispositivos.
     * O stub devolve o mesmo TokenDispositivo e esperamos que o filtro permita a continuação da cadeia.
     */
    @Test
    void devePermitirQuandoTokenValido() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/identidade/perfil");
        request.addHeader("X-Device-Token", "token-valido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        JwtAuthenticationToken authentication = autenticarCliente("usuario-xyz");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenDispositivoService.configurarResultado(Optional.of(criarToken()));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    /**
     * Exercita diretamente o método @AfterEach para garantir que o context seja limpo entre os testes.
     */
    @Test
    void cleanDeveLimparSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "credencial"));

        clean();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private JwtAuthenticationToken autenticarCliente(String sub) {
        Jwt jwt = Jwt.withTokenValue("teste")
                .header("alg", "none")
                .subject(sub)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_cliente")));
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

    private static class TokenDispositivoServiceStub extends com.eickrono.api.identidade.servico.TokenDispositivoService {

        private Optional<TokenDispositivo> resultado = Optional.empty();
        private Optional<String> ultimoUsuario = Optional.empty();

        TokenDispositivoServiceStub() {
            super(null, new com.eickrono.api.identidade.configuracao.DispositivoProperties(), Clock.systemUTC());
        }

        void configurarResultado(Optional<TokenDispositivo> resultado) {
            this.resultado = resultado;
        }

        Optional<String> ultimoUsuario() {
            return ultimoUsuario;
        }

        @Override
        public Optional<TokenDispositivo> validarTokenAtivo(String usuarioSub, String tokenClaro) {
            this.ultimoUsuario = Optional.ofNullable(usuarioSub);
            return resultado;
        }

        @Override
        public TokenEmitido emitirToken(RegistroDispositivo registro, String usuarioSub) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revogarTokensAtivos(String usuarioSub, MotivoRevogacaoToken motivo) {
            throw new UnsupportedOperationException();
        }
    }
}
