package com.eickrono.api.contas.configuracao;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Converte claims de JWT em autoridades considerando escopos e papéis do Keycloak.
 */
public class ConversorJwtFapi implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

    public ConversorJwtFapi() {
        scopesConverter.setAuthorityPrefix("SCOPE_");
        scopesConverter.setAuthoritiesClaimName("scope");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        Set<GrantedAuthority> authorities = new HashSet<>(converterEscopos(source));
        authorities.addAll(extrairPapeis(source));
        return new JwtAuthenticationToken(source, authorities, extrairNome(source));
    }

    private Collection<GrantedAuthority> converterEscopos(Jwt source) {
        Collection<GrantedAuthority> granted = scopesConverter.convert(source);
        return granted == null ? Set.of() : granted;
    }

    @SuppressWarnings("unchecked")
    private Collection<? extends GrantedAuthority> extrairPapeis(Jwt source) {
        Set<String> papeis = new HashSet<>();
        Map<String, Object> realmAccess = source.getClaim("realm_access");
        if (realmAccess != null) {
            Object roles = realmAccess.get("roles");
            if (roles instanceof List<?> lista) {
                lista.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(papeis::add);
            }
        }
        Map<String, Object> recursoAccess = source.getClaim("resource_access");
        if (recursoAccess != null) {
            recursoAccess.values().stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .forEach(valor -> {
                        Object roles = valor.get("roles");
                        if (roles instanceof List<?> lista) {
                            lista.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .forEach(papeis::add);
                        }
                    });
        }
        return papeis.stream()
                .map(papel -> new SimpleGrantedAuthority("ROLE_" + papel))
                .collect(Collectors.toSet());
    }

    private String extrairNome(Jwt source) {
        return source.getClaimAsString("preferred_username");
    }
}
