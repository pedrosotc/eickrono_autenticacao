package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.keycloak.models.UserModel;

/**
 * Deriva a senha efetiva usada pelo Keycloak a partir da senha informada,
 * do pepper do ambiente e do createdTimestamp interno do usuario.
 */
public final class DerivadorSenhaEickrono {

    public static final String ENV_PASSWORD_PEPPER = "EICKRONO_PASSWORD_PEPPER";
    public static final String ATRIBUTO_DATA_NASCIMENTO = "data_nascimento";
    public static final String ATRIBUTO_DATA_NASCIMENTO_FALLBACK = "birthdate";

    private DerivadorSenhaEickrono() {
    }

    public static String derivarParaUsuario(final UserModel user, final String senhaPura) {
        String createdTimestamp = obterCreatedTimestampComoTexto(user);
        return derivar(senhaPura, createdTimestamp);
    }

    public static String derivar(final String senhaPura, final String createdTimestamp) {
        String pepper = System.getenv(ENV_PASSWORD_PEPPER);
        return derivar(senhaPura, createdTimestamp, pepper);
    }

    static String derivar(final String senhaPura, final String createdTimestamp, final String pepper) {
        if (senhaPura == null || senhaPura.isBlank()) {
            throw new IllegalArgumentException("Senha obrigatoria para derivacao.");
        }
        if (createdTimestamp == null || createdTimestamp.isBlank()) {
            throw new IllegalStateException("createdTimestamp do usuario obrigatorio para derivacao da senha.");
        }
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente EICKRONO_PASSWORD_PEPPER nao configurada.");
        }
        return senhaPura + pepper + createdTimestamp.trim();
    }

    public static String garantirCreatedTimestampComoTexto(final UserModel user) {
        if (user == null) {
            throw new IllegalStateException("Usuario obrigatorio para derivacao da senha.");
        }
        Long createdTimestamp = user.getCreatedTimestamp();
        if (createdTimestamp == null || createdTimestamp <= 0L) {
            createdTimestamp = System.currentTimeMillis();
            user.setCreatedTimestamp(createdTimestamp);
        }
        return Long.toString(createdTimestamp);
    }

    public static String obterCreatedTimestampComoTexto(final UserModel user) {
        if (user == null) {
            return null;
        }
        Long createdTimestamp = user.getCreatedTimestamp();
        if (createdTimestamp != null && createdTimestamp > 0L) {
            return Long.toString(createdTimestamp);
        }
        return null;
    }

    public static String obterDataNascimento(final UserModel user) {
        if (user == null) {
            return null;
        }
        List<String> valores = user.getAttributes().get(ATRIBUTO_DATA_NASCIMENTO);
        if (valores != null && !valores.isEmpty() && !valores.get(0).isBlank()) {
            return valores.get(0);
        }
        String fallback = user.getFirstAttribute(ATRIBUTO_DATA_NASCIMENTO_FALLBACK);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    public static String obterDataNascimento(final MultivaluedMap<String, String> formData) {
        if (formData == null) {
            return null;
        }
        String valor = formData.getFirst(ATRIBUTO_DATA_NASCIMENTO);
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        valor = formData.getFirst(ATRIBUTO_DATA_NASCIMENTO_FALLBACK);
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        return null;
    }
}
