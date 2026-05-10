package com.eickrono.api.identidade.infraestrutura.configuracao;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class ValidadorChamadaInterna {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidadorChamadaInterna.class);

    private final String segredoInternoEsperado;
    private final Set<String> clientesPermitidos;

    public ValidadorChamadaInterna(final IntegracaoInternaProperties properties) {
        IntegracaoInternaProperties configuracao = Objects.requireNonNull(properties, "properties é obrigatório");
        this.segredoInternoEsperado = Objects.requireNonNull(
                configuracao.getSegredo(),
                "integracao.interna.segredo é obrigatório");
        this.clientesPermitidos = Set.copyOf(new LinkedHashSet<>(configuracao.getClientesPermitidos()));
        if (clientesPermitidos.isEmpty()) {
            throw new IllegalStateException("integracao.interna.clientes-permitidos deve possuir ao menos um client_id.");
        }
    }

    public void validar(final String segredoInformado, final Jwt jwt, final String componente) {
        validarSegredo(segredoInformado, componente);
        validarCliente(jwt, componente);
    }

    private void validarSegredo(final String segredoInformado, final String componente) {
        if (!Objects.equals(segredoInternoEsperado, segredoInformado)) {
            LOGGER.warn("Segredo interno invalido em {}.", componente);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Segredo interno invalido");
        }
    }

    private void validarCliente(final Jwt jwt, final String componente) {
        if (jwt == null) {
            LOGGER.warn("JWT interno ausente em {}.", componente);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT interno obrigatorio");
        }
        String clienteChamador = extrairClienteChamador(jwt);
        if (!clientesPermitidos.contains(clienteChamador)) {
            LOGGER.warn("Cliente interno nao autorizado em {}. azp={}", componente, clienteChamador);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cliente interno nao autorizado");
        }
    }

    private static String extrairClienteChamador(final Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if (StringUtils.hasText(azp)) {
            return azp;
        }
        String clientId = jwt.getClaimAsString("client_id");
        if (StringUtils.hasText(clientId)) {
            return clientId;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT interno sem identificacao do cliente chamador");
    }
}
