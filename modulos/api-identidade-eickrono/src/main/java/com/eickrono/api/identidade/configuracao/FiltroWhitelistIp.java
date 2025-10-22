package com.eickrono.api.identidade.configuracao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro simples que libera acesso ao Swagger apenas para IPs autorizados.
 */
public class FiltroWhitelistIp extends OncePerRequestFilter {

    private final Set<String> ipsPermitidos;

    public FiltroWhitelistIp(List<String> ipsPermitidos) {
        this.ipsPermitidos = Set.copyOf(Objects.requireNonNull(ipsPermitidos));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (!ipsPermitidos.contains(ip)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "IP não autorizado");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
