package com.eickrono.servidor.autorizacao.infraestrutura.credenciais;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.forms.RegistrationPage;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

/**
 * FormAction de cadastro que grava senha derivada usando pepper + marcador de criacao da conta.
 */
public class EickronoRegistrationPassword implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "eickrono-registration-password";
    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();
        context.getEvent().detail(Details.REGISTER_METHOD, "form");
        String senha = formData.getFirst(RegistrationPage.FIELD_PASSWORD);
        String confirmacao = formData.getFirst(RegistrationPage.FIELD_PASSWORD_CONFIRM);
        if (Validation.isBlank(senha)) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, Messages.MISSING_PASSWORD));
        } else if (!senha.equals(confirmacao)) {
            errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD_CONFIRM, Messages.INVALID_PASSWORD_CONFIRM));
        }
        if (senha != null) {
            PolicyError err = context.getSession()
                    .getProvider(PasswordPolicyManagerProvider.class)
                    .validate(context.getRealm().isRegistrationEmailAsUsername()
                            ? formData.getFirst(RegistrationPage.FIELD_EMAIL)
                            : formData.getFirst(RegistrationPage.FIELD_USERNAME), senha);
            if (err != null) {
                errors.add(new FormMessage(RegistrationPage.FIELD_PASSWORD, err.getMessage(), err.getParameters()));
            }
        }
        if (!errors.isEmpty()) {
            context.error(Errors.INVALID_REGISTRATION);
            formData.remove(RegistrationPage.FIELD_PASSWORD);
            formData.remove(RegistrationPage.FIELD_PASSWORD_CONFIRM);
            context.validationError(formData, errors);
            return;
        }
        context.success();
    }

    @Override
    public void success(FormContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        UserModel user = context.getUser();
        String dataNascimento = DerivadorSenhaEickrono.obterDataNascimento(formData);
        if (dataNascimento != null && !dataNascimento.isBlank()) {
            user.setSingleAttribute(DerivadorSenhaEickrono.ATRIBUTO_DATA_NASCIMENTO, dataNascimento);
        }
        boolean atualizacaoSenhaFalhou = true;
        try {
            String createdTimestamp = DerivadorSenhaEickrono.garantirCreatedTimestampComoTexto(user);
            String senhaDerivada = DerivadorSenhaEickrono.derivar(
                    formData.getFirst(RegistrationPage.FIELD_PASSWORD),
                    createdTimestamp
            );
            user.credentialManager().updateCredential(UserCredentialModel.password(senhaDerivada, false));
            atualizacaoSenhaFalhou = false;
        } finally {
            if (atualizacaoSenhaFalhou) {
                user.addRequiredAction(EickronoUpdatePassword.PROVIDER_ID);
            }
        }
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        form.setAttribute("passwordRequired", true);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public String getDisplayType() {
        return "Eickrono Password Validation";
    }

    @Override
    public String getReferenceCategory() {
        return PasswordCredentialModel.TYPE;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES.clone();
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Valida e grava senha derivada usando senha + pepper + createdTimestamp do usuario.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
