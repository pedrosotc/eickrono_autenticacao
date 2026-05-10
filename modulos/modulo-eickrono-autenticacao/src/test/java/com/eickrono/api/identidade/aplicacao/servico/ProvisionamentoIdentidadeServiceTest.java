package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProvisionamentoIdentidadeServiceTest {

    private final Map<String, Pessoa> pessoasPorSub = new LinkedHashMap<>();
    private final Map<String, PerfilIdentidade> perfisPorSub = new LinkedHashMap<>();
    private final List<FormaAcesso> formasAcesso = new ArrayList<>();

    private long proximoIdPessoa = 1L;
    private long proximoIdFormaAcesso = 1L;

    private ProvisionamentoIdentidadeService service;

    private void inicializarServico() {
        pessoasPorSub.clear();
        perfisPorSub.clear();
        formasAcesso.clear();
        proximoIdPessoa = 1L;
        proximoIdFormaAcesso = 1L;

        service = new ProvisionamentoIdentidadeService(
                pessoaRepositorio(),
                formaAcessoRepositorio(),
                perfilIdentidadeRepositorio());
    }

    @Test
    @DisplayName("deve criar pessoa, forma de acesso principal e projeção de perfil no primeiro provisionamento")
    void deveProvisionarPessoaPerfilEFormaDeAcesso() {
        inicializarServico();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-03-11T18:00:00Z");

        Pessoa pessoa = service.provisionarOuAtualizar(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                atualizadoEm);

        assertThat(pessoa.getId()).isEqualTo(1L);
        assertThat(formasAcesso).hasSize(1);
        assertThat(formasAcesso.getFirst().getTipo()).isEqualTo(TipoFormaAcesso.EMAIL_SENHA);
        assertThat(formasAcesso.getFirst().getProvedor()).isEqualTo("EMAIL");
        assertThat(perfisPorSub).containsKey("sub-123");
    }

    @Test
    @DisplayName("deve falhar quando o e-mail já estiver vinculado a outra pessoa")
    void deveFalharQuandoEmailConflitar() throws Exception {
        inicializarServico();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2026-03-11T18:00:00Z");

        Pessoa existente = pessoaComId(1L, "sub-123", "teste@eickrono.com", "Pessoa Teste");
        pessoasPorSub.put(existente.getSub(), existente);

        Pessoa outra = pessoaComId(2L, "sub-999", "teste@eickrono.com", "Outra Pessoa");
        FormaAcesso acessoOutraPessoa = new FormaAcesso(
                outra,
                TipoFormaAcesso.EMAIL_SENHA,
                "EMAIL",
                "teste@eickrono.com",
                true,
                atualizadoEm,
                atualizadoEm);
        definirId(FormaAcesso.class, acessoOutraPessoa, 9L);
        formasAcesso.add(acessoOutraPessoa);

        assertThatThrownBy(() -> service.provisionarOuAtualizar(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                atualizadoEm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("E-mail já vinculado");

        assertThat(perfisPorSub).doesNotContainKey("sub-123");
    }

    @Test
    @DisplayName("deve registrar forma de acesso social para a pessoa provisionada")
    void deveRegistrarFormaAcessoSocial() throws Exception {
        inicializarServico();
        OffsetDateTime vinculadoEm = OffsetDateTime.parse("2026-03-11T18:00:00Z");
        Pessoa pessoa = pessoaComId(10L, "sub-123", "teste@eickrono.com", "Pessoa Teste");
        pessoasPorSub.put(pessoa.getSub(), pessoa);

        FormaAcesso forma = service.registrarFormaAcessoSocial(pessoa, "google", "abc", vinculadoEm);

        assertThat(forma.getTipo()).isEqualTo(TipoFormaAcesso.SOCIAL);
        assertThat(forma.getProvedor()).isEqualTo("GOOGLE");
        assertThat(forma.getPessoa().getId()).isEqualTo(10L);
        assertThat(formasAcesso).hasSize(1);
    }

    private PessoaRepositorio pessoaRepositorio() {
        return (PessoaRepositorio) Proxy.newProxyInstance(
                PessoaRepositorio.class.getClassLoader(),
                new Class<?>[] {PessoaRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySub" -> Optional.ofNullable(pessoasPorSub.get((String) Objects.requireNonNull(args)[0]));
                    case "save" -> salvarPessoaLocal((Pessoa) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PessoaRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private FormaAcessoRepositorio formaAcessoRepositorio() {
        return (FormaAcessoRepositorio) Proxy.newProxyInstance(
                FormaAcessoRepositorio.class.getClassLoader(),
                new Class<?>[] {FormaAcessoRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByTipoAndProvedorAndIdentificador" -> localizarFormaPorChave(args);
                    case "findByPessoaAndTipoAndPrincipalTrue" -> localizarFormaPrincipal(args);
                    case "findByPessoa" -> localizarFormasDaPessoa((Pessoa) Objects.requireNonNull(args)[0]);
                    case "save" -> salvarFormaLocal((FormaAcesso) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FormaAcessoRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private PerfilIdentidadeRepositorio perfilIdentidadeRepositorio() {
        return (PerfilIdentidadeRepositorio) Proxy.newProxyInstance(
                PerfilIdentidadeRepositorio.class.getClassLoader(),
                new Class<?>[] {PerfilIdentidadeRepositorio.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySub" -> Optional.ofNullable(perfisPorSub.get((String) Objects.requireNonNull(args)[0]));
                    case "save" -> salvarPerfilLocal((PerfilIdentidade) Objects.requireNonNull(args)[0]);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "PerfilIdentidadeRepositorioFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private Pessoa salvarPessoaLocal(Pessoa pessoa) throws Exception {
        if (pessoa.getId() == null) {
            definirId(Pessoa.class, pessoa, proximoIdPessoa++);
        }
        pessoasPorSub.put(pessoa.getSub(), pessoa);
        return pessoa;
    }

    private FormaAcesso salvarFormaLocal(FormaAcesso forma) throws Exception {
        if (forma.getId() == null) {
            definirId(FormaAcesso.class, forma, proximoIdFormaAcesso++);
        }
        formasAcesso.removeIf(existente -> Objects.equals(existente.getId(), forma.getId()));
        formasAcesso.add(forma);
        return forma;
    }

    private PerfilIdentidade salvarPerfilLocal(PerfilIdentidade perfil) {
        perfisPorSub.put(perfil.getSub(), perfil);
        return perfil;
    }

    private Optional<FormaAcesso> localizarFormaPorChave(Object[] args) {
        TipoFormaAcesso tipo = (TipoFormaAcesso) Objects.requireNonNull(args)[0];
        String provedor = (String) args[1];
        String identificador = (String) args[2];
        return formasAcesso.stream()
                .filter(forma -> forma.getTipo() == tipo)
                .filter(forma -> Objects.equals(forma.getProvedor(), provedor))
                .filter(forma -> Objects.equals(forma.getIdentificador(), identificador))
                .findFirst();
    }

    private Optional<FormaAcesso> localizarFormaPrincipal(Object[] args) {
        Pessoa pessoa = (Pessoa) Objects.requireNonNull(args)[0];
        TipoFormaAcesso tipo = (TipoFormaAcesso) args[1];
        return formasAcesso.stream()
                .filter(forma -> Objects.equals(forma.getPessoa().getId(), pessoa.getId()))
                .filter(forma -> forma.getTipo() == tipo)
                .filter(FormaAcesso::isPrincipal)
                .findFirst();
    }

    private List<FormaAcesso> localizarFormasDaPessoa(Pessoa pessoa) {
        return formasAcesso.stream()
                .filter(forma -> Objects.equals(forma.getPessoa().getId(), pessoa.getId()))
                .toList();
    }

    private Pessoa pessoaComId(Long id, String sub, String email, String nome) throws Exception {
        Pessoa pessoa = new Pessoa(
                sub,
                email,
                nome,
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.parse("2026-03-11T18:00:00Z"));
        definirId(Pessoa.class, pessoa, id);
        return pessoa;
    }

    private void definirId(Class<?> tipo, Object alvo, Long id) throws Exception {
        Field field = tipo.getDeclaredField("id");
        field.setAccessible(true);
        field.set(alvo, id);
    }
}
