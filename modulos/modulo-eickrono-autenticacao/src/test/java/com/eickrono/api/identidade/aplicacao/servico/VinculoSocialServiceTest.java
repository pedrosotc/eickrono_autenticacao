package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.excecao.ApiAutenticadaException;
import com.eickrono.api.identidade.aplicacao.modelo.AvatarCadastroConfirmado;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.SessaoInternaAutenticada;
import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.UploadAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculoSocialDto;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class VinculoSocialServiceTest {

    @Mock
    private ProvisionamentoIdentidadeService provisionamentoIdentidadeService;
    @Mock
    private ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;
    @Mock
    private AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;
    @Mock
    private ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;
    @Mock
    private AvatarSocialProjetoJdbc avatarSocialProjetoJdbc;
    @Mock
    private UploadAvatarCadastroServico uploadAvatarCadastroServico;

    private final List<FormaAcesso> formasAcessoPersistidas = new ArrayList<>();
    private final List<AuditoriaEventoIdentidade> auditorias = new ArrayList<>();
    private final ClienteAdministracaoVinculosSociaisKeycloakFake clienteAdministracaoVinculosSociaisKeycloak =
            new ClienteAdministracaoVinculosSociaisKeycloakFake();

    private VinculoSocialService vinculoSocialService;
    private long proximoIdFormaAcesso = 100L;

    @Test
    @DisplayName("listar vínculos sociais: deve retornar os provedores suportados e ignorar vínculos legados não suportados")
    void deveListarProvedoresSuportados() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        clienteAdministracaoVinculosSociaisKeycloak.definir(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "teste@gmail.com",
                        "teste@gmail.com")));
        formasAcessoPersistidas.add(criarFormaAcesso(pessoa, 1L, "GOOGLE", "teste@gmail.com"));

        VinculosSociaisDto resposta = vinculoSocialService.listar(jwt);

        assertThat(resposta.provedores()).hasSize(5);
        assertThat(resposta.provedores())
                .extracting(VinculoSocialDto::provedor, VinculoSocialDto::vinculado)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("google", true),
                        org.assertj.core.groups.Tuple.tuple("apple", false),
                        org.assertj.core.groups.Tuple.tuple("facebook", false),
                        org.assertj.core.groups.Tuple.tuple("linkedin", false),
                        org.assertj.core.groups.Tuple.tuple("instagram", false));
        assertThat(resposta.provedores().getFirst().identificadorMascarado()).isEqualTo("t***@gmail.com");
    }

    @Test
    @DisplayName("entrar e vincular: deve materializar vínculo social confirmado sem contexto pendente e sem token externo")
    void deveVincularDadosSociaisConfirmadosAposLoginLocal() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        VinculosSociaisDto resposta = vinculoSocialService.vincularConfirmado(
                jwt,
                "google",
                new VinculoSocialConfirmadoCadastro(
                        "google",
                        "google-sub-confirmado",
                        "google-user",
                        "teste@google.test",
                        "Pessoa Google",
                        "https://cdn.eickrono.test/google.png",
                        true),
                "eickrono-thimisu-app");

        verify(clienteAdministracaoCadastroKeycloak).vincularIdentidadeFederada(
                eq("sub-123"),
                eq(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-confirmado",
                        "google-user",
                        "Pessoa Google",
                        "https://cdn.eickrono.test/google.png")));
        verify(avatarSocialProjetoJdbc).definirAvatarSocial(
                eq("sub-123"),
                eq(1L),
                eq(ProvedorVinculoSocial.GOOGLE),
                any());
        assertThat(formasAcessoPersistidas)
                .extracting(FormaAcesso::getProvedor, FormaAcesso::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GOOGLE", "google-sub-confirmado"));
        assertThat(resposta.provedores().stream()
                .filter(item -> item.provedor().equals("google"))
                .findFirst()
                .orElseThrow()
                .vinculado()).isTrue();
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.getFirst().getTipoEvento()).isEqualTo("VINCULO_SOCIAL_VINCULADO");
    }

    @Test
    @DisplayName("remover vínculo social: deve remover no Keycloak e limpar a projeção local")
    void deveRemoverVinculoSocial() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        formasAcessoPersistidas.add(criarFormaAcessoEmailSenha(
                pessoa,
                9L,
                "EMAIL",
                "teste@eickrono.com",
                true));
        formasAcessoPersistidas.add(criarFormaAcesso(
                pessoa,
                10L,
                "GOOGLE",
                "google-sub-1"));
        clienteAdministracaoVinculosSociaisKeycloak.definir(
                "sub-123",
                List.of(new IdentidadeFederadaKeycloak(
                        ProvedorVinculoSocial.GOOGLE,
                        "google-sub-1",
                        "teste@gmail.com")));
        when(autenticacaoSessaoInternaServico.autenticar("teste@eickrono.com", "SenhaAtual123"))
                .thenReturn(new SessaoInternaAutenticada(true, "Bearer", "access", "refresh", 300L));

        VinculosSociaisDto resposta = vinculoSocialService.remover(jwt, "google", "SenhaAtual123", null);

        assertThat(clienteAdministracaoVinculosSociaisKeycloak.provedorRemovido("sub-123"))
                .contains(ProvedorVinculoSocial.GOOGLE);
        assertThat(formasAcessoPersistidas)
                .extracting(FormaAcesso::getTipo, FormaAcesso::getIdentificador)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        TipoFormaAcesso.EMAIL_SENHA,
                        "teste@eickrono.com"));
        assertThat(auditorias.getFirst().getTipoEvento()).isEqualTo("VINCULO_SOCIAL_REMOVIDO");
        assertThat(resposta.provedores().stream()
                .filter(item -> item.provedor().equals("google"))
                .findFirst()
                .orElseThrow()
                .vinculado()).isFalse();
    }

    @Test
    @DisplayName("remover vínculo social: deve exigir senha atual para confirmar a desvinculação")
    void deveExigirSenhaAtualParaRemoverVinculoSocial() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        formasAcessoPersistidas.add(criarFormaAcessoEmailSenha(
                pessoa,
                9L,
                "EMAIL",
                "teste@eickrono.com",
                true));

        assertThatThrownBy(() -> vinculoSocialService.remover(jwt, "google", null, null))
                .isInstanceOf(ApiAutenticadaException.class)
                .satisfies(erro -> {
                    ApiAutenticadaException exception = (ApiAutenticadaException) erro;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCodigo()).isEqualTo("senha_confirmacao_obrigatoria");
                    assertThat(exception.getMessage()).contains("Informe a senha atual");
                });
    }

    @Test
    @DisplayName("remover vínculo social: deve rejeitar senha incorreta")
    void deveRejeitarSenhaIncorretaAoRemoverVinculoSocial() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        formasAcessoPersistidas.add(criarFormaAcessoEmailSenha(
                pessoa,
                9L,
                "EMAIL",
                "teste@eickrono.com",
                true));
        when(autenticacaoSessaoInternaServico.autenticar("teste@eickrono.com", "SenhaErrada"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciais_invalidas"));

        assertThatThrownBy(() -> vinculoSocialService.remover(jwt, "google", "SenhaErrada", null))
                .isInstanceOf(ApiAutenticadaException.class)
                .satisfies(erro -> {
                    ApiAutenticadaException exception = (ApiAutenticadaException) erro;
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getCodigo()).isEqualTo("senha_confirmacao_invalida");
                    assertThat(exception.getMessage()).contains("não confere");
                });
    }

    @Test
    @DisplayName("atualizar avatar preferido: deve refletir o avatar social principal do projeto")
    void deveAtualizarAvatarPreferidoSocialDoProjeto() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        FormaAcesso formaAcesso = criarFormaAcesso(pessoa, 10L, "GOOGLE", "google-sub-1");
        formaAcesso.atualizarDadosExternos(
                "Pessoa Google",
                "https://cdn.eickrono.test/google.png",
                OffsetDateTime.parse("2024-05-03T10:00:00Z"));
        formasAcessoPersistidas.add(formaAcesso);
        when(avatarSocialProjetoJdbc.buscarPreferencia("sub-123", 1L))
                .thenReturn(new AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto(
                        "SOCIAL",
                        "https://cdn.eickrono.test/google.png",
                        "GOOGLE",
                        "avatar-v-google",
                        OffsetDateTime.parse("2024-05-03T10:00:00Z")));

        VinculosSociaisDto resposta = vinculoSocialService.atualizarAvatarPreferido(
                jwt,
                new AtualizarAvatarPreferidoApiRequest("eickrono-thimisu-app", "SOCIAL", "google", null));

        verify(avatarSocialProjetoJdbc).definirAvatarSocial(
                eq("sub-123"),
                eq(1L),
                eq(ProvedorVinculoSocial.GOOGLE),
                any());
        assertThat(resposta.avatarPreferidoOrigem()).isEqualTo("SOCIAL");
        assertThat(resposta.avatarPreferidoUrl()).isEqualTo("https://cdn.eickrono.test/google.png");
        VinculoSocialDto google = resposta.provedores().stream()
                .filter(item -> item.provedor().equals("google"))
                .findFirst()
                .orElseThrow();
        assertThat(google.avatarPrincipalNoProjeto()).isTrue();
        assertThat(google.urlAvatarExterno()).isEqualTo("https://cdn.eickrono.test/google.png");
    }

    @Test
    @DisplayName("upload avatar preferido: deve materializar arquivo e definir URL do projeto")
    void deveMaterializarUploadEDefinirAvatarPreferidoUrlDoProjeto() {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);
        when(uploadAvatarCadastroServico.materializar(any()))
                .thenReturn(new AvatarCadastroConfirmado(
                        "THIMISU",
                        "https://cdn.eickrono.test/avatar-upload.jpg",
                        "avatares/thimisu/avatar-upload.jpg",
                        "avatar.jpg",
                        "image/jpeg",
                        3L,
                        "hash",
                        "avatar-v1",
                        null,
                        true));
        when(avatarSocialProjetoJdbc.buscarPreferencia("sub-123", 1L))
                .thenReturn(new AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto(
                        "URL_EXTERNA",
                        "https://cdn.eickrono.test/avatar-upload.jpg",
                        null,
                        "avatar-v1",
                        OffsetDateTime.parse("2024-05-03T10:00:00Z")));

        VinculosSociaisDto resposta = vinculoSocialService.uploadAvatarPreferido(
                jwt,
                new UploadAvatarPreferidoApiRequest(
                        "eickrono-thimisu-app",
                        "avatar.jpg",
                        "image/jpeg",
                        3L,
                        "YWJj"));

        verify(uploadAvatarCadastroServico).materializar(new AvatarCadastroConfirmado(
                "THIMISU",
                null,
                null,
                "avatar.jpg",
                "image/jpeg",
                3L,
                null,
                null,
                "YWJj",
                true));
        verify(avatarSocialProjetoJdbc).definirAvatarUrl(
                eq("sub-123"),
                eq(1L),
                eq("https://cdn.eickrono.test/avatar-upload.jpg"),
                any());
        assertThat(resposta.avatarPreferidoOrigem()).isEqualTo("URL_EXTERNA");
        assertThat(resposta.avatarPreferidoUrl()).isEqualTo("https://cdn.eickrono.test/avatar-upload.jpg");
    }

    @Test
    @DisplayName("vincular rede social confirmada: deve manter o vínculo mesmo quando o provedor não informar foto")
    void deveVincularRedeSocialConfirmadaSemFotoDisponivel() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        VinculosSociaisDto resposta = vinculoSocialService.vincularConfirmado(
                jwt,
                "google",
                new VinculoSocialConfirmadoCadastro(
                        "google",
                        "google-sub-1",
                        "teste@gmail.com",
                        "teste@gmail.com",
                        "Pessoa Google",
                        null,
                        false),
                "eickrono-thimisu-app");

        VinculoSocialDto google = resposta.provedores().stream()
                .filter(item -> item.provedor().equals("google"))
                .findFirst()
                .orElseThrow();
        assertThat(google.vinculado()).isTrue();
        assertThat(google.urlAvatarExterno()).isNull();
        assertThat(google.avatarPrincipalNoProjeto()).isFalse();
        assertThat(google.statusAvatarSocial()).isEqualTo("FOTO_NAO_DISPONIVEL");
        assertThat(google.mensagemAvatarSocial())
                .isEqualTo("Esta conta esta vinculada, mas nao ha foto disponivel para usar no perfil neste momento.");
        assertThat(formasAcessoPersistidas.getFirst().getUrlAvatarExterno()).isNull();
    }

    @Test
    @DisplayName("vincular rede social confirmada: deve manter o vínculo da Apple mesmo quando o provedor não informar foto")
    void deveVincularRedeSocialConfirmadaAppleSemFotoDisponivel() throws Exception {
        inicializarServico();
        Pessoa pessoa = criarPessoa();
        Jwt jwt = jwt("sub-123");
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(pessoa);

        VinculosSociaisDto resposta = vinculoSocialService.vincularConfirmado(
                jwt,
                "apple",
                new VinculoSocialConfirmadoCadastro(
                        "apple",
                        "apple-sub-1",
                        "usuario@icloud.test",
                        "usuario@icloud.test",
                        "Pessoa Apple",
                        "   ",
                        false),
                "eickrono-thimisu-app");

        VinculoSocialDto apple = resposta.provedores().stream()
                .filter(item -> item.provedor().equals("apple"))
                .findFirst()
                .orElseThrow();
        assertThat(apple.vinculado()).isTrue();
        assertThat(apple.urlAvatarExterno()).isNull();
        assertThat(apple.avatarPrincipalNoProjeto()).isFalse();
        assertThat(apple.statusAvatarSocial()).isEqualTo("PROVEDOR_SEM_SUPORTE_DE_FOTO");
        assertThat(apple.mensagemAvatarSocial())
                .isEqualTo("Esta conta esta vinculada, mas este provedor nao disponibiliza foto para uso no perfil neste aplicativo.");
        assertThat(formasAcessoPersistidas.getFirst().getProvedor()).isEqualTo("APPLE");
        assertThat(formasAcessoPersistidas.getFirst().getUrlAvatarExterno()).isNull();
    }

    @Test
    @DisplayName("atualizar avatar preferido: deve rejeitar quando a rede social não possui foto disponível")
    void deveRejeitarAvatarPreferidoSocialSemFotoDisponivel() {
        inicializarServico();
        Jwt jwt = jwt("sub-123");
        doThrow(ApiAutenticadaException.conflito(
                "avatar_social_indisponivel",
                "A rede social informada ainda nao possui foto disponivel para este projeto.",
                Map.of("provedor", "google")))
                .when(avatarSocialProjetoJdbc)
                .definirAvatarSocial(eq("sub-123"), eq(1L), eq(ProvedorVinculoSocial.GOOGLE), any());

        assertThatThrownBy(() -> vinculoSocialService.atualizarAvatarPreferido(
                jwt,
                new AtualizarAvatarPreferidoApiRequest("eickrono-thimisu-app", "SOCIAL", "google", null)))
                .isInstanceOf(ApiAutenticadaException.class)
                .extracting("codigo")
                .isEqualTo("avatar_social_indisponivel");
    }

    @Test
    @DisplayName("atualizar avatar preferido: deve rejeitar Apple quando a rede social não possui foto disponível")
    void deveRejeitarAvatarPreferidoAppleSemFotoDisponivel() {
        inicializarServico();
        Jwt jwt = jwt("sub-123");
        doThrow(ApiAutenticadaException.conflito(
                "avatar_social_indisponivel",
                "A rede social informada ainda nao possui foto disponivel para este projeto.",
                Map.of("provedor", "apple")))
                .when(avatarSocialProjetoJdbc)
                .definirAvatarSocial(eq("sub-123"), eq(1L), eq(ProvedorVinculoSocial.APPLE), any());

        assertThatThrownBy(() -> vinculoSocialService.atualizarAvatarPreferido(
                jwt,
                new AtualizarAvatarPreferidoApiRequest("eickrono-thimisu-app", "SOCIAL", "apple", null)))
                .isInstanceOf(ApiAutenticadaException.class)
                .extracting("codigo")
                .isEqualTo("avatar_social_indisponivel");
    }

    private void inicializarServico() {
        formasAcessoPersistidas.clear();
        auditorias.clear();
        clienteAdministracaoVinculosSociaisKeycloak.limpar();
        proximoIdFormaAcesso = 100L;
        Mockito.lenient().when(avatarSocialProjetoJdbc.buscarPreferencia(any(), any()))
                .thenReturn(AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto.vazia());
        Mockito.lenient().when(resolvedorProjetoFluxoPublico.resolverAtivo(any()))
                .thenReturn(new ProjetoFluxoPublicoResolvido(
                        1L,
                        "thimisu",
                        "Thimisu",
                        "Aplicacao",
                        "Thimisu",
                        "Mobile",
                        false));

        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio());
        vinculoSocialService = new VinculoSocialService(
                formaAcessoRepositorio(),
                auditoriaService,
                Objects.requireNonNull(provisionamentoIdentidadeService),
                clienteAdministracaoVinculosSociaisKeycloak,
                Objects.requireNonNull(clienteAdministracaoCadastroKeycloak),
                Objects.requireNonNull(autenticacaoSessaoInternaServico),
                Objects.requireNonNull(resolvedorProjetoFluxoPublico),
                Objects.requireNonNull(avatarSocialProjetoJdbc),
                Objects.requireNonNull(uploadAvatarCadastroServico));
    }

    private FormaAcessoRepositorio formaAcessoRepositorio() {
        return (FormaAcessoRepositorio) Proxy.newProxyInstance(
                FormaAcessoRepositorio.class.getClassLoader(),
                new Class<?>[] {FormaAcessoRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTipoAndProvedorAndIdentificador" -> localizarFormaPorChave(args);
                    case "findByPessoa" -> localizarFormasDaPessoa((Pessoa) Objects.requireNonNull(args)[0]);
                    case "save" -> salvarFormaAcesso((FormaAcesso) Objects.requireNonNull(args)[0]);
                    case "deleteAll" -> {
                        for (Object item : (Iterable<?>) Objects.requireNonNull(args)[0]) {
                            formasAcessoPersistidas.remove(item);
                        }
                        yield null;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FormaAcessoRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio() {
        return (AuditoriaEventoIdentidadeRepositorio) Proxy.newProxyInstance(
                AuditoriaEventoIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {AuditoriaEventoIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        AuditoriaEventoIdentidade auditoria = (AuditoriaEventoIdentidade) Objects.requireNonNull(args)[0];
                        auditorias.add(auditoria);
                        yield auditoria;
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "AuditoriaEventoIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Optional<FormaAcesso> localizarFormaPorChave(final Object[] args) {
        TipoFormaAcesso tipo = (TipoFormaAcesso) Objects.requireNonNull(args)[0];
        String provedor = (String) args[1];
        String identificador = (String) args[2];
        return formasAcessoPersistidas.stream()
                .filter(forma -> forma.getTipo() == tipo)
                .filter(forma -> Objects.equals(forma.getProvedor(), provedor))
                .filter(forma -> Objects.equals(forma.getIdentificador(), identificador))
                .findFirst();
    }

    private List<FormaAcesso> localizarFormasDaPessoa(final Pessoa pessoa) {
        return formasAcessoPersistidas.stream()
                .filter(forma -> Objects.equals(forma.getPessoa().getId(), pessoa.getId()))
                .toList();
    }

    private Pessoa criarPessoa() {
        return new Pessoa(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.parse("2024-05-01T12:00:00Z"));
    }

    private Jwt jwt(final String sub) {
        return Jwt.withTokenValue("token")
                .subject(sub)
                .claim("email", "teste@eickrono.com")
                .claim("name", "Pessoa Teste")
                .header("alg", "none")
                .build();
    }

    private FormaAcesso criarFormaAcesso(final Pessoa pessoa,
                                         final Long id,
                                         final String provedor,
                                         final String identificador) throws Exception {
        FormaAcesso formaAcesso = new FormaAcesso(
                pessoa,
                TipoFormaAcesso.SOCIAL,
                provedor,
                identificador,
                false,
                OffsetDateTime.parse("2024-05-02T15:00:00Z"),
                OffsetDateTime.parse("2024-05-02T15:00:00Z"));
        definirId(FormaAcesso.class, formaAcesso, id);
        return formaAcesso;
    }

    private FormaAcesso criarFormaAcessoEmailSenha(final Pessoa pessoa,
                                                   final Long id,
                                                   final String provedor,
                                                   final String identificador,
                                                   final boolean principal) throws Exception {
        FormaAcesso formaAcesso = new FormaAcesso(
                pessoa,
                TipoFormaAcesso.EMAIL_SENHA,
                provedor,
                identificador,
                principal,
                OffsetDateTime.parse("2024-05-02T15:00:00Z"),
                OffsetDateTime.parse("2024-05-02T15:00:00Z"));
        definirId(FormaAcesso.class, formaAcesso, id);
        return formaAcesso;
    }

    private FormaAcesso salvarFormaAcesso(final FormaAcesso formaAcesso) throws Exception {
        FormaAcesso salvo = Objects.requireNonNull(formaAcesso);
        if (salvo.getId() == null) {
            definirId(FormaAcesso.class, salvo, proximoIdFormaAcesso++);
        }
        formasAcessoPersistidas.removeIf(existente -> Objects.equals(existente.getId(), salvo.getId()));
        formasAcessoPersistidas.add(salvo);
        return salvo;
    }

    private void definirId(final Class<?> tipo, final Object alvo, final Long id) throws Exception {
        Field field = tipo.getDeclaredField("id");
        field.setAccessible(true);
        field.set(alvo, id);
    }

    private static final class ClienteAdministracaoVinculosSociaisKeycloakFake
            implements ClienteAdministracaoVinculosSociaisKeycloak {

        private final Map<String, Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak>> identidadesPorUsuario =
                new java.util.LinkedHashMap<>();
        private final Map<String, ProvedorVinculoSocial> remocoes = new java.util.LinkedHashMap<>();

        @Override
        public List<IdentidadeFederadaKeycloak> listarIdentidadesFederadas(final String subjectRemoto) {
            return new ArrayList<>(identidadesPorUsuario.getOrDefault(subjectRemoto, Map.of()).values());
        }

        @Override
        public void removerIdentidadeFederada(final String subjectRemoto, final ProvedorVinculoSocial provedor) {
            identidadesPorUsuario.computeIfAbsent(subjectRemoto, ignored -> new java.util.LinkedHashMap<>())
                    .remove(provedor);
            remocoes.put(subjectRemoto, provedor);
        }

        void definir(final String subjectRemoto, final List<IdentidadeFederadaKeycloak> identidadesFederadas) {
            Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> porProvedor = new java.util.LinkedHashMap<>();
            for (IdentidadeFederadaKeycloak identidadeFederada : identidadesFederadas) {
                porProvedor.put(identidadeFederada.provedor(), identidadeFederada);
            }
            identidadesPorUsuario.put(subjectRemoto, porProvedor);
        }

        Optional<ProvedorVinculoSocial> provedorRemovido(final String subjectRemoto) {
            return Optional.ofNullable(remocoes.get(subjectRemoto));
        }

        void limpar() {
            identidadesPorUsuario.clear();
            remocoes.clear();
        }
    }
}
