package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.apresentacao.dto.ValidacaoTokenDispositivoResponse;
import com.eickrono.api.identidade.aplicacao.servico.ResultadoValidacaoTokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.StatusValidacaoTokenDispositivo;
import com.eickrono.api.identidade.aplicacao.servico.TokenDispositivoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante que requisições autenticadas contenham um token de dispositivo ativo.
 */
public class DeviceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TokenDispositivoService tokenDispositivoService;
    private final ObjectMapper objectMapper;

    public DeviceTokenFilter(TokenDispositivoService tokenDispositivoService) {
        this(tokenDispositivoService, new ObjectMapper());
    }

    public DeviceTokenFilter(TokenDispositivoService tokenDispositivoService, ObjectMapper objectMapper) {
        this.tokenDispositivoService = tokenDispositivoService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper e obrigatorio").copy();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtAuthenticationToken.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch("ROLE_cliente"::equals)) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceToken = request.getHeader(HEADER_DEVICE_TOKEN);
        if (!StringUtils.hasText(deviceToken)) {
            responder(response, HttpStatus.PRECONDITION_REQUIRED,
                    new ResultadoValidacaoTokenDispositivo(StatusValidacaoTokenDispositivo.AUSENTE, null));
            return;
        }

        String usuarioSub = jwtAuthenticationToken.getToken().getSubject();
        ResultadoValidacaoTokenDispositivo resultado = tokenDispositivoService.validarToken(usuarioSub, deviceToken);
        if (!resultado.valido()) {
            responder(response, HttpStatus.LOCKED, resultado);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            throw new IllegalStateException("Request URI obrigatoria.");
        }
        String method = request.getMethod();
        if (method == null) {
            throw new IllegalStateException("Metodo HTTP obrigatorio.");
        }
        if (HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        if (PATH_MATCHER.match("/actuator/**", path)
                || PATH_MATCHER.match("/.well-known/**", path)
                || PATH_MATCHER.match("/identidade/dispositivos/registro/**", path)
                || PATH_MATCHER.match("/api/conta/dispositivos/registro/**", path)
                || PATH_MATCHER.match("/identidade/atestacoes/interna/**", path)
                || PATH_MATCHER.match("/identidade/cadastros/interna", path)
                || PATH_MATCHER.match("/identidade/cadastros/interna/**", path)
                || PATH_MATCHER.match("/identidade/perfis-sistema/interna", path)
                || PATH_MATCHER.match("/identidade/perfis-sistema/interna/**", path)
                || PATH_MATCHER.match("/identidade/sessoes/interna", path)
                || PATH_MATCHER.match("/identidade/dispositivos/token/validacao/interna", path)
                || PATH_MATCHER.match("/api/conta/dispositivos/token/validacao/interna", path)) {
            return true;
        }
        return false;
    }

    private void responder(HttpServletResponse response,
            HttpStatus status,
            ResultadoValidacaoTokenDispositivo resultado) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ValidacaoTokenDispositivoResponse payload = new ValidacaoTokenDispositivoResponse(
                resultado.valido(),
                resultado.codigo(),
                resultado.mensagem(),
                resultado.expiraEmOpt().orElse(null));
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}
