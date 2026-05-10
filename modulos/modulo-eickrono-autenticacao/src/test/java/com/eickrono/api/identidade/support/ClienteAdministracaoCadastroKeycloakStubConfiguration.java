package com.eickrono.api.identidade.support;

import com.eickrono.api.identidade.aplicacao.modelo.CadastroKeycloakProvisionado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.UsuarioCadastroKeycloakExistente;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoCadastroKeycloak;
import com.eickrono.api.identidade.aplicacao.servico.ClienteAdministracaoVinculosSociaisKeycloak;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("test")
public class ClienteAdministracaoCadastroKeycloakStubConfiguration
        implements ClienteAdministracaoCadastroKeycloak, ClienteAdministracaoVinculosSociaisKeycloak {

    private final Map<String, AtomicBoolean> usuarios = new ConcurrentHashMap<>();
    private final Map<String, Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak>> identidadesFederadas =
            new ConcurrentHashMap<>();

    @Override
    public CadastroKeycloakProvisionado criarUsuarioPendente(final String nomeCompleto,
                                                             final String emailPrincipal,
                                                             final String senhaPura) {
        String sub = "keycloak-" + emailPrincipal.toLowerCase();
        usuarios.putIfAbsent(sub, new AtomicBoolean(false));
        return new CadastroKeycloakProvisionado(sub, emailPrincipal, nomeCompleto);
    }

    @Override
    public void confirmarEmailEAtivarUsuario(final String subjectRemoto,
                                             final String nomeCompleto,
                                             final LocalDate dataNascimento) {
        AtomicBoolean status = usuarios.get(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
        if (status == null) {
            throw new IllegalStateException("Usuário pendente não encontrado no stub do Keycloak.");
        }
        status.set(true);
    }

    @Override
    public void removerUsuarioPendente(final String subjectRemoto) {
        usuarios.remove(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
    }

    @Override
    public Optional<UsuarioCadastroKeycloakExistente> buscarUsuarioPorEmail(final String emailPrincipal) {
        String emailNormalizado = Objects.requireNonNull(emailPrincipal, "emailPrincipal é obrigatório").trim().toLowerCase();
        return usuarios.entrySet().stream()
                .filter(entry -> entry.getKey().equals("keycloak-" + emailNormalizado))
                .findFirst()
                .map(entry -> new UsuarioCadastroKeycloakExistente(
                        entry.getKey(),
                        emailNormalizado,
                        entry.getValue().get(),
                        entry.getValue().get(),
                        1L
                ));
    }

    @Override
    public void vincularIdentidadeFederada(final String subjectRemoto,
                                           final IdentidadeFederadaKeycloak identidadeFederada) {
        Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório");
        Objects.requireNonNull(identidadeFederada, "identidadeFederada é obrigatória");
        identidadesFederadas.computeIfAbsent(subjectRemoto, ignored -> new ConcurrentHashMap<>())
                .put(identidadeFederada.provedor(), identidadeFederada);
    }

    @Override
    public void redefinirSenha(final String subjectRemoto, final String senhaPura) {
        AtomicBoolean status = usuarios.get(Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"));
        if (status == null) {
            throw new IllegalStateException("Usuário não encontrado para redefinição de senha no stub do Keycloak.");
        }
    }

    @Override
    public List<IdentidadeFederadaKeycloak> listarIdentidadesFederadas(final String subjectRemoto) {
        return new ArrayList<>(identidadesFederadas.getOrDefault(
                Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"),
                Map.of()).values());
    }

    @Override
    public void removerIdentidadeFederada(final String subjectRemoto, final ProvedorVinculoSocial provedor) {
        identidadesFederadas.computeIfAbsent(
                        Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório"),
                        ignored -> new ConcurrentHashMap<>())
                .remove(Objects.requireNonNull(provedor, "provedor é obrigatório"));
    }

    public void definirIdentidadesFederadas(final String subjectRemoto,
                                            final List<IdentidadeFederadaKeycloak> identidades) {
        Objects.requireNonNull(subjectRemoto, "subjectRemoto é obrigatório");
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> porProvedor = new LinkedHashMap<>();
        for (IdentidadeFederadaKeycloak identidade : Objects.requireNonNull(identidades, "identidades são obrigatórias")) {
            porProvedor.put(identidade.provedor(), identidade);
        }
        identidadesFederadas.put(subjectRemoto, new ConcurrentHashMap<>(porProvedor));
    }

    public void limparIdentidadesFederadas() {
        identidadesFederadas.clear();
    }
}
