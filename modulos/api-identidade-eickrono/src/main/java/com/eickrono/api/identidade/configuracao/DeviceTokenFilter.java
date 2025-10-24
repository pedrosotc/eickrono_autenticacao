package com.eickrono.api.identidade.configuracao;

import com.eickrono.api.identidade.servico.TokenDispositivoService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Garante que requisições autenticadas contenham um token de dispositivo ativo.
 */
@Component
public class DeviceTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_DEVICE_TOKEN = "X-Device-Token";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final TokenDispositivoService tokenDispositivoService;

    public DeviceTokenFilter(TokenDispositivoService tokenDispositivoService) {
        this.tokenDispositivoService = tokenDispositivoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
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
            responder(response, HttpStatus.PRECONDITION_REQUIRED, "Cabeçalho X-Device-Token é obrigatório");
            return;
        }

        String usuarioSub = jwtAuthenticationToken.getToken().getSubject();
        boolean valido = tokenDispositivoService.validarTokenAtivo(usuarioSub, deviceToken).isPresent();
        if (!valido) {
            responder(response, HttpStatus.LOCKED, "Token de dispositivo revogado ou inválido");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (PATH_MATCHER.match("/actuator/**", path)
                || PATH_MATCHER.match("/.well-known/**", path)
                || PATH_MATCHER.match("/identidade/dispositivos/registro/**", path)) {
            return true;
        }
        return false;
    }

    private void responder(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String payload = """
                {"erro":"%s"}
                """.formatted(mensagem);
        response.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
    }
}
