package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.CredencialSocialDeclarada;
import com.eickrono.api.identidade.aplicacao.modelo.CredencialSocialValidada;
import com.eickrono.api.identidade.infraestrutura.configuracao.CredenciaisSociaisNativasProperties;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ValidadorCredencialSocialNativaService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA_RESPOSTA =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate;
    private final CredenciaisSociaisNativasProperties properties;

    public ValidadorCredencialSocialNativaService(final RestTemplateBuilder restTemplateBuilder,
                                                  final CredenciaisSociaisNativasProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(this.properties.getTimeout())
                .setReadTimeout(this.properties.getTimeout())
                .build();
    }

    public CredencialSocialValidada validar(final String provedor,
                                            final String tokenExterno,
                                            final CredencialSocialDeclarada declarada) {
        String provedorNormalizado = normalizarProvedor(provedor);
        if (!StringUtils.hasText(tokenExterno)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial social ausente.");
        }
        return switch (provedorNormalizado) {
            case "google" -> validarGoogle(tokenExterno, declarada);
            case "apple" -> validarApple(tokenExterno, declarada);
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provedor social não suportado para autenticação nativa."
            );
        };
    }

    private CredencialSocialValidada validarGoogle(final String accessToken,
                                                   final CredencialSocialDeclarada declarada) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> response;
        try {
            response = restTemplate.exchange(
                    URI.create(properties.getGoogle().getUserInfoUrl()),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    MAPA_RESPOSTA
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google inválida.", exception);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google inválida.");
        }
        Map<String, Object> corpo = response.getBody();
        String identificadorExterno = texto(corpo.get("sub"));
        if (!StringUtils.hasText(identificadorExterno)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google sem identificador.");
        }
        String email = texto(corpo.get("email"));
        Object emailVerificado = corpo.get("email_verified");
        if (StringUtils.hasText(email) && Boolean.FALSE.equals(emailVerificado)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail Google não verificado.");
        }
        return new CredencialSocialValidada(
                "google",
                identificadorExterno,
                primeiroTexto(email, declarada == null ? null : declarada.email()),
                primeiroTexto(
                        declarada == null ? null : declarada.nomeUsuarioExterno(),
                        texto(corpo.get("preferred_username"))
                ),
                primeiroTexto(texto(corpo.get("name")), declarada == null ? null : declarada.nomeCompleto()),
                primeiroTexto(texto(corpo.get("picture")), declarada == null ? null : declarada.urlAvatarExterno())
        );
    }

    private CredencialSocialValidada validarApple(final String identityToken,
                                                  final CredencialSocialDeclarada declarada) {
        Jwt jwt;
        try {
            jwt = NimbusJwtDecoder.withJwkSetUri(properties.getApple().getJwkSetUri())
                    .build()
                    .decode(identityToken);
        } catch (JwtException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Apple inválida.", exception);
        }
        validarIssuerApple(jwt);
        validarAudienceApple(jwt);
        if (!StringUtils.hasText(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Apple sem identificador.");
        }
        return new CredencialSocialValidada(
                "apple",
                jwt.getSubject(),
                primeiroTexto(jwt.getClaimAsString("email"), declarada == null ? null : declarada.email()),
                declarada == null ? null : declarada.nomeUsuarioExterno(),
                declarada == null ? null : declarada.nomeCompleto(),
                declarada == null ? null : declarada.urlAvatarExterno()
        );
    }

    private void validarIssuerApple(final Jwt jwt) {
        String issuerEsperado = properties.getApple().getIssuer();
        String issuerRecebido = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (!Objects.equals(issuerEsperado, issuerRecebido)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Apple com emissor inválido.");
        }
    }

    private void validarAudienceApple(final Jwt jwt) {
        List<String> audienciasPermitidas = properties.getApple().getAudiences();
        if (audienciasPermitidas == null || audienciasPermitidas.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Audiência Apple não configurada para validação social."
            );
        }
        boolean audienciaValida = jwt.getAudience().stream()
                .anyMatch(audienciasPermitidas::contains);
        if (!audienciaValida) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Apple com audiência inválida.");
        }
    }

    private static String normalizarProvedor(final String provedor) {
        if (!StringUtils.hasText(provedor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provedor social é obrigatório.");
        }
        return provedor.trim().toLowerCase(Locale.ROOT);
    }

    private static String texto(final Object valor) {
        return valor == null || !StringUtils.hasText(valor.toString()) ? null : valor.toString().trim();
    }

    private static String primeiroTexto(final String... valores) {
        for (String valor : valores) {
            if (StringUtils.hasText(valor)) {
                return valor.trim();
            }
        }
        return null;
    }
}
