package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;

class DerivadorSenhaEickronoTest {

    @Test
    void deveDerivarSenhaConcatenandoSenhaPepperEMarcadorDeCriacao() {
        String derivada = DerivadorSenhaEickrono.derivar("Senha@123", "1710792000000", "pepper-secreto");
        assertEquals("Senha@123pepper-secreto1710792000000", derivada);
    }

    @Test
    void deveUsarCreatedTimestampComoMarcadorDeCriacaoDoUsuario() {
        UserModel user = userModel(1710792000000L, "1710000000000");
        assertEquals("1710792000000", DerivadorSenhaEickrono.obterCreatedTimestampComoTexto(user));
    }

    @Test
    void deveIgnorarAtributoDataCriacaoContaQuandoCreatedTimestampNaoExiste() {
        UserModel user = userModel(null, "1710000000000");
        assertNull(DerivadorSenhaEickrono.obterCreatedTimestampComoTexto(user));
    }

    private static UserModel userModel(final Long createdTimestamp, final String dataCriacaoConta) {
        return (UserModel) Proxy.newProxyInstance(
                DerivadorSenhaEickronoTest.class.getClassLoader(),
                new Class<?>[] {UserModel.class},
                (proxy, method, args) -> {
                    if ("getCreatedTimestamp".equals(method.getName())) {
                        return createdTimestamp;
                    }
                    if ("getFirstAttribute".equals(method.getName()) && args != null && args.length == 1) {
                        return "data_criacao_conta".equals(args[0]) ? dataCriacaoConta : null;
                    }
                    return null;
                });
    }
}
