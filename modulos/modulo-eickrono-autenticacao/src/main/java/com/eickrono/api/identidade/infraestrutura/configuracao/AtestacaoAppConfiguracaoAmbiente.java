package com.eickrono.api.identidade.infraestrutura.configuracao;

import com.eickrono.api.identidade.infraestrutura.atestacao.AppleAppAttestProperties;
import com.eickrono.api.identidade.infraestrutura.atestacao.GooglePlayIntegrityProperties;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class AtestacaoAppConfiguracaoAmbiente {

    private final AtestacaoAppProperties properties;
    private final Environment environment;

    public AtestacaoAppConfiguracaoAmbiente(final AtestacaoAppProperties properties,
                                            final Environment environment) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.environment = Objects.requireNonNull(environment, "environment é obrigatório");
    }

    @PostConstruct
    void validar() {
        if (!ambienteFlexivel() && properties.isPermitirValidacaoLocalSemProvedorOficial()) {
            throw new IllegalStateException(
                    "A validacao local sem provedor oficial so pode ser habilitada em dev, hml ou test."
            );
        }

        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        GooglePlayIntegrityProperties google = properties.getGoogle();
        AppleAppAttestProperties apple = properties.getApple();

        if (!google.isHabilitado()) {
            throw new IllegalStateException("A validacao oficial Android deve permanecer habilitada em prod.");
        }
        if (!apple.isHabilitado()) {
            throw new IllegalStateException("A validacao oficial iOS deve permanecer habilitada em prod.");
        }
        validarGoogle(google);
        validarApple(apple);
    }

    private boolean ambienteFlexivel() {
        return environment.acceptsProfiles(Profiles.of("dev", "hml", "test"));
    }

    private static void validarGoogle(final GooglePlayIntegrityProperties google) {
        if (google.getPackageName() == null || google.getPackageName().isBlank()) {
            throw new IllegalStateException("identidade.atestacao.app.google.package-name é obrigatório em prod.");
        }
        if (google.getServiceAccountJsonArquivo() == null || google.getServiceAccountJsonArquivo().isBlank()) {
            throw new IllegalStateException(
                    "identidade.atestacao.app.google.service-account-json-arquivo é obrigatório em prod."
            );
        }
    }

    private static void validarApple(final AppleAppAttestProperties apple) {
        if (apple.getTeamIdentifier() == null || apple.getTeamIdentifier().isBlank()) {
            throw new IllegalStateException("identidade.atestacao.app.apple.team-identifier é obrigatório em prod.");
        }
        if (apple.getBundleIdentifier() == null || apple.getBundleIdentifier().isBlank()) {
            throw new IllegalStateException("identidade.atestacao.app.apple.bundle-identifier é obrigatório em prod.");
        }
    }
}
