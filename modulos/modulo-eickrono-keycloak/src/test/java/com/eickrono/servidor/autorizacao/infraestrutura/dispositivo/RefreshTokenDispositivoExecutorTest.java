package com.eickrono.servidor.autorizacao.infraestrutura.dispositivo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.core.MultivaluedHashMap;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.keycloak.services.clientpolicy.context.TokenRefreshContext;

class RefreshTokenDispositivoExecutorTest {

    @Test
    void deveBloquearRefreshSemDeviceToken() {
        RefreshTokenDispositivoExecutor executor = new RefreshTokenDispositivoExecutor(
                keycloakSession("user-1"),
                (usuarioSub, deviceToken) -> new ResultadoValidacaoRefreshDispositivo(true, "DEVICE_TOKEN_VALID", "ok"));

        ClientPolicyException exception = assertThrows(
                ClientPolicyException.class,
                () -> executor.executeOnEvent(tokenRefreshContext(null)));

        assertEquals("invalid_grant", exception.getError());
    }

    @Test
    void deveBloquearRefreshQuandoDeviceTokenForRevogado() {
        RefreshTokenDispositivoExecutor executor = new RefreshTokenDispositivoExecutor(
                keycloakSession("user-1"),
                (usuarioSub, deviceToken) -> new ResultadoValidacaoRefreshDispositivo(false, "DEVICE_TOKEN_REVOKED", "revogado"));

        ClientPolicyException exception = assertThrows(
                ClientPolicyException.class,
                () -> executor.executeOnEvent(tokenRefreshContext("device-1")));

        assertEquals("invalid_grant", exception.getError());
        assertEquals("DEVICE_TOKEN_REVOKED: revogado", exception.getErrorDetail());
    }

    @Test
    void devePermitirRefreshQuandoDeviceTokenForValido() {
        RefreshTokenDispositivoExecutor executor = new RefreshTokenDispositivoExecutor(
                keycloakSession("user-1"),
                (usuarioSub, deviceToken) -> {
                    assertEquals("user-1", usuarioSub);
                    assertEquals("device-1", deviceToken);
                    return new ResultadoValidacaoRefreshDispositivo(true, "DEVICE_TOKEN_VALID", "ok");
                });

        assertDoesNotThrow(() -> executor.executeOnEvent(tokenRefreshContext("device-1")));
    }

    private static TokenRefreshContext tokenRefreshContext(String deviceToken) {
        MultivaluedHashMap<String, String> params = new MultivaluedHashMap<>();
        if (deviceToken != null) {
            params.add("device_token", deviceToken);
        }
        return new TokenRefreshContext(params, clientModel());
    }

    private static org.keycloak.models.ClientModel clientModel() {
        return (org.keycloak.models.ClientModel) Proxy.newProxyInstance(
                RefreshTokenDispositivoExecutorTest.class.getClassLoader(),
                new Class<?>[] {org.keycloak.models.ClientModel.class},
                (proxy, method, args) -> null);
    }

    private static KeycloakSession keycloakSession(String userId) {
        KeycloakContext context = (KeycloakContext) Proxy.newProxyInstance(
                RefreshTokenDispositivoExecutorTest.class.getClassLoader(),
                new Class<?>[] {KeycloakContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUserSession" -> userSession(userId);
                    case "getUser" -> userModel(userId);
                    default -> null;
                });
        return (KeycloakSession) Proxy.newProxyInstance(
                RefreshTokenDispositivoExecutorTest.class.getClassLoader(),
                new Class<?>[] {KeycloakSession.class},
                (proxy, method, args) -> {
                    if ("getContext".equals(method.getName())) {
                        return context;
                    }
                    return null;
                });
    }

    private static UserSessionModel userSession(String userId) {
        return (UserSessionModel) Proxy.newProxyInstance(
                RefreshTokenDispositivoExecutorTest.class.getClassLoader(),
                new Class<?>[] {UserSessionModel.class},
                (proxy, method, args) -> {
                    if ("getUser".equals(method.getName())) {
                        return userModel(userId);
                    }
                    return null;
                });
    }

    private static UserModel userModel(String userId) {
        return (UserModel) Proxy.newProxyInstance(
                RefreshTokenDispositivoExecutorTest.class.getClassLoader(),
                new Class<?>[] {UserModel.class},
                (proxy, method, args) -> {
                    if ("getId".equals(method.getName())) {
                        return userId;
                    }
                    return null;
                });
    }
}
