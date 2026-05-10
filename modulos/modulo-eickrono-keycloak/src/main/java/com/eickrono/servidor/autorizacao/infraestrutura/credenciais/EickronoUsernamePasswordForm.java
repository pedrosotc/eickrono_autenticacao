package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordForm;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.CredentialRepresentation;

/**
 * Autenticador que valida senha derivada por senha + pepper + createdTimestamp do usuario.
 */
public class EickronoUsernamePasswordForm extends UsernamePasswordForm {

    private static final Logger LOGGER = Logger.getLogger(EickronoUsernamePasswordForm.class);

    @Override
    public boolean validatePassword(AuthenticationFlowContext context, UserModel user,
            MultivaluedMap<String, String> inputData, boolean clearUser) {
        String senha = inputData.getFirst(CredentialRepresentation.PASSWORD);
        if (senha == null || senha.isBlank()) {
            return lidarComSenhaInvalida(context, user, clearUser, true);
        }
        if (isDisabledByBruteForce(context, user)) {
            return false;
        }

        try {
            String senhaDerivada = DerivadorSenhaEickrono.derivarParaUsuario(user, senha);
            if (user.credentialManager().isValid(UserCredentialModel.password(senhaDerivada))) {
                context.getAuthenticationSession().setAuthNote(
                        org.keycloak.services.managers.AuthenticationManager.PASSWORD_VALIDATED, "true");
                return true;
            }
        } catch (RuntimeException ex) {
            LOGGER.warnf(ex, "Falha ao derivar senha para o usuario %s", user.getUsername());
        }
        return lidarComSenhaInvalida(context, user, clearUser, false);
    }

    private boolean lidarComSenhaInvalida(AuthenticationFlowContext context, UserModel user,
            boolean clearUser, boolean senhaVazia) {
        context.getEvent().user(user);
        context.getEvent().error(org.keycloak.events.Errors.INVALID_USER_CREDENTIALS);

        if (isUserAlreadySetBeforeUsernamePasswordAuth(context)) {
            LoginFormsProvider form = context.form();
            form.setAttribute(LoginFormsProvider.USERNAME_HIDDEN, true);
            form.setAttribute(LoginFormsProvider.REGISTRATION_DISABLED, true);
        }

        Response challengeResponse = challenge(context, getDefaultChallengeMessage(context),
                org.keycloak.services.validation.Validation.FIELD_PASSWORD);
        if (senhaVazia) {
            context.forceChallenge(challengeResponse);
        } else {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challengeResponse);
        }
        if (clearUser) {
            context.clearUser();
        }
        return false;
    }
}
