package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.Config;
import org.keycloak.authentication.InitiatedActionSupport;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.RequiredActionFactory;
import org.keycloak.authentication.RequiredActionProvider;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.ModelException;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

/**
 * Required action de atualizacao de senha com derivacao por pepper + createdTimestamp do usuario.
 */
public class EickronoUpdatePassword implements RequiredActionProvider, RequiredActionFactory {

    public static final String PROVIDER_ID = "eickrono-update-password";

    @Override
    public InitiatedActionSupport initiatedActionSupport() {
        return InitiatedActionSupport.SUPPORTED;
    }

    @Override
    public void evaluateTriggers(RequiredActionContext context) {
    }

    @Override
    public void requiredActionChallenge(RequiredActionContext context) {
        Response challenge = context.form()
                .setAttribute("username", context.getAuthenticationSession().getAuthenticatedUser().getUsername())
                .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
        context.challenge(challenge);
    }

    @Override
    public void processAction(RequiredActionContext context) {
        EventBuilder event = context.getEvent();
        UserModel user = context.getUser();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        event.event(EventType.UPDATE_CREDENTIAL).detail("credential_type", "password");
        String passwordNew = formData.getFirst("password-new");
        String passwordConfirm = formData.getFirst("password-confirm");

        EventBuilder errorEvent = event.clone().event(EventType.UPDATE_CREDENTIAL_ERROR)
                .client(context.getAuthenticationSession().getClient())
                .user(context.getAuthenticationSession().getAuthenticatedUser());

        if (Validation.isBlank(passwordNew)) {
            challengeComErro(context, Validation.FIELD_PASSWORD, Messages.MISSING_PASSWORD);
            errorEvent.error(Errors.PASSWORD_MISSING);
            return;
        }
        if (!passwordNew.equals(passwordConfirm)) {
            challengeComErro(context, Validation.FIELD_PASSWORD_CONFIRM, Messages.NOTMATCH_PASSWORD);
            errorEvent.error(Errors.PASSWORD_CONFIRM_ERROR);
            return;
        }

        try {
            String senhaDerivada = DerivadorSenhaEickrono.derivarParaUsuario(user, passwordNew);
            user.credentialManager().updateCredential(UserCredentialModel.password(senhaDerivada, false));
            context.success();
        } catch (ModelException ex) {
            errorEvent.detail(Details.REASON, ex.getMessage()).error(Errors.PASSWORD_REJECTED);
            challengeComMensagem(context, ex.getMessage());
        } catch (RuntimeException ex) {
            errorEvent.detail(Details.REASON, ex.getMessage()).error(Errors.PASSWORD_REJECTED);
            challengeComMensagem(context, ex.getMessage());
        }
    }

    private void challengeComErro(RequiredActionContext context, String campo, String mensagem) {
        Response challenge = context.form()
                .setAttribute("username", context.getAuthenticationSession().getAuthenticatedUser().getUsername())
                .addError(new FormMessage(campo, mensagem))
                .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
        context.challenge(challenge);
    }

    private void challengeComMensagem(RequiredActionContext context, String mensagem) {
        Response challenge = context.form()
                .setAttribute("username", context.getAuthenticationSession().getAuthenticatedUser().getUsername())
                .setError(mensagem)
                .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
        context.challenge(challenge);
    }

    @Override
    public void close() {
    }

    @Override
    public RequiredActionProvider create(KeycloakSession session) {
        return new EickronoUpdatePassword();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public String getDisplayText() {
        return "Eickrono Update Password";
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isOneTimeAction() {
        return true;
    }

    @Override
    public int getMaxAuthAge(KeycloakSession session) {
        return session == null ? 300 : RequiredActionProvider.super.getMaxAuthAge(session);
    }
}
