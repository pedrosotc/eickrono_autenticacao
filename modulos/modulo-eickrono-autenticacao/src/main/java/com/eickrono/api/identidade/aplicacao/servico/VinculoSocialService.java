package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.ApiAutenticadaException;
import com.eickrono.api.identidade.aplicacao.modelo.IdentidadeFederadaKeycloak;
import com.eickrono.api.identidade.aplicacao.modelo.ProjetoFluxoPublicoResolvido;
import com.eickrono.api.identidade.aplicacao.modelo.VinculoSocialConfirmadoCadastro;
import com.eickrono.api.identidade.apresentacao.dto.AtualizarAvatarPreferidoApiRequest;
import com.eickrono.api.identidade.apresentacao.dto.VinculosSociaisDto;
import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.ProvedorVinculoSocial;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import com.eickrono.api.identidade.dominio.repositorio.FormaAcessoRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.VinculoSocialDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Serviço para manutenção de vínculos sociais.
 */
@Service
public class VinculoSocialService {

    private final FormaAcessoRepositorio formaAcessoRepositorio;
    private final AuditoriaService auditoriaService;
    private final ProvisionamentoIdentidadeService provisionamentoIdentidadeService;
    private final ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak;
    private final ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak;
    private final AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico;
    private final ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico;
    private final AvatarSocialProjetoJdbc avatarSocialProjetoJdbc;

    public VinculoSocialService(FormaAcessoRepositorio formaAcessoRepositorio,
                                AuditoriaService auditoriaService,
                                ProvisionamentoIdentidadeService provisionamentoIdentidadeService,
                                ClienteAdministracaoVinculosSociaisKeycloak clienteAdministracaoVinculosSociaisKeycloak,
                                ClienteAdministracaoCadastroKeycloak clienteAdministracaoCadastroKeycloak,
                                AutenticacaoSessaoInternaServico autenticacaoSessaoInternaServico,
                                ResolvedorProjetoFluxoPublico resolvedorProjetoFluxoPublico,
                                AvatarSocialProjetoJdbc avatarSocialProjetoJdbc) {
        this.formaAcessoRepositorio = formaAcessoRepositorio;
        this.auditoriaService = auditoriaService;
        this.provisionamentoIdentidadeService = provisionamentoIdentidadeService;
        this.clienteAdministracaoVinculosSociaisKeycloak = clienteAdministracaoVinculosSociaisKeycloak;
        this.clienteAdministracaoCadastroKeycloak = clienteAdministracaoCadastroKeycloak;
        this.autenticacaoSessaoInternaServico = autenticacaoSessaoInternaServico;
        this.resolvedorProjetoFluxoPublico = resolvedorProjetoFluxoPublico;
        this.avatarSocialProjetoJdbc = avatarSocialProjetoJdbc;
    }

    @Transactional
    public VinculosSociaisDto listar(final Jwt jwt) {
        return listar(jwt, null);
    }

    @Transactional
    public VinculosSociaisDto listar(final Jwt jwt, final String aplicacaoId) {
        Jwt jwtLocal = Objects.requireNonNull(jwt, "jwt é obrigatório");
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwtLocal);
        return montarResposta(
                formaAcessoRepositorio.findByPessoa(pessoa),
                resolverProjetoOpcional(aplicacaoId),
                jwtLocal.getSubject());
    }

    @Transactional
    public VinculosSociaisDto vincularConfirmado(final Jwt jwt,
                                                 final String aliasProvedor,
                                                 final VinculoSocialConfirmadoCadastro vinculoSocialConfirmado,
                                                 final String aplicacaoId) {
        ProvedorVinculoSocial provedor = validarProvedor(aliasProvedor);
        VinculoSocialConfirmadoCadastro vinculo = Objects.requireNonNull(
                vinculoSocialConfirmado,
                "vinculoSocialConfirmado é obrigatório");
        String identificadorExterno = normalizarTexto(vinculo.identificadorExterno());
        if (identificadorExterno == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Identificador social externo é obrigatório.");
        }
        Jwt jwtLocal = Objects.requireNonNull(jwt, "jwt é obrigatório");
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwtLocal);
        IdentidadeFederadaKeycloak identidadeFederada = new IdentidadeFederadaKeycloak(
                provedor,
                identificadorExterno,
                normalizarTexto(vinculo.nomeUsuarioExterno()),
                normalizarTexto(vinculo.nomeCompleto()),
                normalizarTexto(vinculo.urlAvatarExterno()));
        validarConflitoVinculoSocial(pessoa, identidadeFederada);
        clienteAdministracaoCadastroKeycloak.vincularIdentidadeFederada(jwtLocal.getSubject(), identidadeFederada);

        OffsetDateTime instanteSincronizacao = OffsetDateTime.now();
        List<IdentidadeFederadaKeycloak> identidadesFederadas = new ArrayList<>(
                clienteAdministracaoVinculosSociaisKeycloak.listarIdentidadesFederadas(jwtLocal.getSubject()));
        if (!contemIdentidadeFederada(identidadesFederadas, identidadeFederada)) {
            identidadesFederadas.add(identidadeFederada);
        }
        identidadesFederadas = enriquecerIdentidadesFederadas(
                identidadesFederadas,
                provedor,
                identidadeFederada.nomeExibicaoExterno(),
                identidadeFederada.urlAvatarExterno());
        reconciliarFormasAcessoSociais(pessoa, identidadesFederadas, instanteSincronizacao);
        sincronizarAvataresMultiapp(jwtLocal, pessoa, instanteSincronizacao, identidadesFederadas, aplicacaoId);
        if (vinculo.avatarPreferido() && normalizarTexto(vinculo.urlAvatarExterno()) != null) {
            resolverProjetoOpcional(aplicacaoId).ifPresent(projeto -> avatarSocialProjetoJdbc.definirAvatarSocial(
                    jwtLocal.getSubject(),
                    projeto.clienteEcossistemaId(),
                    provedor,
                    instanteSincronizacao));
        }
        auditoriaService.registrarEvento(
                "VINCULO_SOCIAL_VINCULADO",
                jwtLocal.getSubject(),
                "Provedor=" + provedor.getAliasApi());
        return montarResposta(
                formaAcessoRepositorio.findByPessoa(pessoa),
                resolverProjetoOpcional(aplicacaoId),
                jwtLocal.getSubject());
    }

    @Transactional
    public VinculosSociaisDto remover(final Jwt jwt, final String aliasProvedor) {
        return remover(jwt, aliasProvedor, null, null);
    }

    @Transactional
    public VinculosSociaisDto remover(final Jwt jwt,
                                      final String aliasProvedor,
                                      final String senhaConfirmacao,
                                      final String aplicacaoId) {
        ProvedorVinculoSocial provedor = validarProvedor(aliasProvedor);
        Objects.requireNonNull(jwt, "jwt é obrigatório");
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        confirmarReautenticacaoPorSenha(pessoa, provedor, senhaConfirmacao);
        OffsetDateTime instanteSincronizacao = OffsetDateTime.now();
        clienteAdministracaoVinculosSociaisKeycloak.removerIdentidadeFederada(jwt.getSubject(), provedor);
        List<IdentidadeFederadaKeycloak> identidadesFederadas = clienteAdministracaoVinculosSociaisKeycloak
                .listarIdentidadesFederadas(jwt.getSubject());
        reconciliarFormasAcessoSociais(pessoa, identidadesFederadas, instanteSincronizacao);
        sincronizarAvataresMultiapp(jwt, pessoa, instanteSincronizacao, identidadesFederadas, aplicacaoId);
        auditoriaService.registrarEvento("VINCULO_SOCIAL_REMOVIDO", jwt.getSubject(),
                "Provedor=" + provedor.getAliasApi());
        return montarResposta(
                formaAcessoRepositorio.findByPessoa(pessoa),
                resolverProjetoOpcional(aplicacaoId),
                jwt.getSubject());
    }

    @Transactional
    public VinculosSociaisDto atualizarAvatarPreferido(final Jwt jwt,
                                                       final AtualizarAvatarPreferidoApiRequest requisicao) {
        Objects.requireNonNull(jwt, "jwt é obrigatório");
        Objects.requireNonNull(requisicao, "requisicao é obrigatória");
        ProjetoFluxoPublicoResolvido projeto = resolvedorProjetoFluxoPublico.resolverAtivo(requisicao.aplicacaoId());
        OffsetDateTime agora = OffsetDateTime.now();
        String origem = Objects.requireNonNull(requisicao.origem(), "origem é obrigatória").trim().toUpperCase(Locale.ROOT);
        switch (origem) {
            case "SOCIAL" -> avatarSocialProjetoJdbc.definirAvatarSocial(
                    jwt.getSubject(),
                    projeto.clienteEcossistemaId(),
                    validarProvedor(requisicao.provedor()),
                    agora
            );
            case "URL_EXTERNA" -> avatarSocialProjetoJdbc.definirAvatarUrl(
                    jwt.getSubject(),
                    projeto.clienteEcossistemaId(),
                    requisicao.url(),
                    agora
            );
            case "NENHUM" -> avatarSocialProjetoJdbc.limparAvatarPreferido(
                    jwt.getSubject(),
                    projeto.clienteEcossistemaId(),
                    agora
            );
            default -> throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Origem de avatar preferido inválida."
            );
        }
        Pessoa pessoa = provisionamentoIdentidadeService.provisionarOuAtualizar(jwt);
        return montarResposta(
                formaAcessoRepositorio.findByPessoa(pessoa),
                Optional.of(projeto),
                jwt.getSubject());
    }

    private boolean possuiAutenticacaoPorSenha(final Pessoa pessoa) {
        return formaAcessoRepositorio.findByPessoa(pessoa).stream()
                .anyMatch(formaAcesso -> formaAcesso.getTipo() == TipoFormaAcesso.EMAIL_SENHA);
    }

    private void confirmarReautenticacaoPorSenha(final Pessoa pessoa,
                                                 final ProvedorVinculoSocial provedor,
                                                 final String senhaConfirmacao) {
        String senhaNormalizada = senhaConfirmacao == null ? "" : senhaConfirmacao.trim();
        if (senhaNormalizada.isEmpty()) {
            throw new ApiAutenticadaException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "senha_confirmacao_obrigatoria",
                    "Informe a senha atual para confirmar a desvinculação.",
                    Map.of(
                            "provedor", provedor.getAliasApi(),
                            "exigeReautenticacao", true
                    )
            );
        }
        if (!possuiAutenticacaoPorSenha(pessoa)) {
            throw new ApiAutenticadaException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "reautenticacao_senha_indisponivel",
                    "Esta conta não possui autenticação por senha disponível para confirmar a operação.",
                    Map.of(
                            "provedor", provedor.getAliasApi(),
                            "exigeReautenticacao", true
                    )
            );
        }
        try {
            autenticacaoSessaoInternaServico.autenticar(pessoa.getEmail(), senhaNormalizada);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == UNAUTHORIZED.value()) {
                throw new ApiAutenticadaException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "senha_confirmacao_invalida",
                        "A senha informada não confere com a conta atual.",
                        Map.of(
                                "provedor", provedor.getAliasApi(),
                                "exigeReautenticacao", true
                        )
                );
            }
            throw exception;
        }
    }


    private void validarConflitoVinculoSocial(final Pessoa pessoa,
                                              final IdentidadeFederadaKeycloak identidadeFederada) {
        Optional<FormaAcesso> conflito = formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                TipoFormaAcesso.SOCIAL,
                identidadeFederada.provedor().getAliasFormaAcesso(),
                identidadeFederada.identificadorCanonico()
        );
        if (conflito.isPresent() && !Objects.equals(conflito.orElseThrow().getPessoa().getId(), pessoa.getId())) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Esta conta social já está vinculada a outro usuário."
            );
        }
    }


    private void reconciliarFormasAcessoSociais(final Pessoa pessoa,
                                                final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                                final OffsetDateTime instanteSincronizacao) {
        Map<ProvedorVinculoSocial, FormaAcesso> existentes = formaAcessoRepositorio.findByPessoa(pessoa).stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .filter(forma -> ProvedorVinculoSocial.fromAlias(forma.getProvedor().toLowerCase(Locale.ROOT)).isPresent())
                .collect(LinkedHashMap::new,
                        (mapa, forma) -> ProvedorVinculoSocial.fromAlias(forma.getProvedor().toLowerCase(Locale.ROOT))
                                .ifPresent(provedor -> mapa.put(provedor, forma)),
                        Map::putAll);
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> remotos = indexarPorProvedor(identidadesFederadas);

        for (ProvedorVinculoSocial provedor : ProvedorVinculoSocial.values()) {
            IdentidadeFederadaKeycloak identidadeFederada = remotos.get(provedor);
            FormaAcesso existente = existentes.get(provedor);
            if (identidadeFederada == null) {
                continue;
            }
            String identificadorCanonico = identidadeFederada.identificadorCanonico();
            Optional<FormaAcesso> conflito = formaAcessoRepositorio.findByTipoAndProvedorAndIdentificador(
                    TipoFormaAcesso.SOCIAL,
                    provedor.getAliasFormaAcesso(),
                    identificadorCanonico);
            if (conflito.isPresent() && !Objects.equals(conflito.orElseThrow().getPessoa().getId(), pessoa.getId())) {
                throw new IllegalStateException("Forma de acesso social já vinculada a outra pessoa");
            }
            if (existente == null) {
                formaAcessoRepositorio.save(new FormaAcesso(
                        pessoa,
                        TipoFormaAcesso.SOCIAL,
                        provedor.getAliasFormaAcesso(),
                        identificadorCanonico,
                        false,
                        instanteSincronizacao,
                        instanteSincronizacao,
                        identidadeFederada.nomeExibicaoExterno(),
                        identidadeFederada.urlAvatarExterno(),
                        identidadeFederada.urlAvatarExterno() == null ? null : instanteSincronizacao));
                continue;
            }
            existente.atualizarIdentificador(identificadorCanonico, false, instanteSincronizacao);
            existente.atualizarDadosExternos(
                    identidadeFederada.nomeExibicaoExterno(),
                    identidadeFederada.urlAvatarExterno(),
                    instanteSincronizacao);
            formaAcessoRepositorio.save(existente);
        }

        List<FormaAcesso> obsoletos = existentes.entrySet().stream()
                .filter(entry -> !remotos.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        if (!obsoletos.isEmpty()) {
            formaAcessoRepositorio.deleteAll(obsoletos);
        }
    }

    private Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> indexarPorProvedor(
            final List<IdentidadeFederadaKeycloak> identidadesFederadas) {
        Map<ProvedorVinculoSocial, IdentidadeFederadaKeycloak> remotos = new LinkedHashMap<>();
        for (IdentidadeFederadaKeycloak identidadeFederada : identidadesFederadas) {
            remotos.put(identidadeFederada.provedor(), identidadeFederada);
        }
        return remotos;
    }

    private VinculosSociaisDto montarResposta(final List<FormaAcesso> formasAcessoPersistidas,
                                              final Optional<ProjetoFluxoPublicoResolvido> projeto,
                                              final String subjectRemoto) {
        Map<ProvedorVinculoSocial, FormaAcesso> formasPorProvedor = formasAcessoPersistidas.stream()
                .filter(forma -> forma.getTipo() == TipoFormaAcesso.SOCIAL)
                .filter(forma -> resolverProvedor(forma.getProvedor()).isPresent())
                .collect(LinkedHashMap::new,
                        (mapa, forma) -> resolverProvedor(forma.getProvedor())
                                .ifPresent(provedor -> mapa.put(provedor, forma)),
                        Map::putAll);
        AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto preferencia = projeto
                .map(item -> avatarSocialProjetoJdbc.buscarPreferencia(subjectRemoto, item.clienteEcossistemaId()))
                .orElseGet(AvatarSocialProjetoJdbc.PreferenciaAvatarProjeto::vazia);
        List<VinculoSocialDto> provedores = Arrays.stream(ProvedorVinculoSocial.values())
                .map(provedor -> {
                    FormaAcesso forma = formasPorProvedor.get(provedor);
                    DiagnosticoAvatarSocial diagnosticoAvatar = diagnosticarAvatarSocial(provedor, forma);
                    return new VinculoSocialDto(
                            provedor.getAliasApi(),
                            true,
                            forma != null,
                            forma == null ? null : forma.getVerificadoEm(),
                            forma == null ? null : mascararIdentificador(forma.getIdentificador()),
                            forma == null ? null : forma.getNomeExibicaoExterno(),
                            forma == null ? null : forma.getUrlAvatarExterno(),
                            forma == null ? null : forma.getAvatarExternoAtualizadoEm(),
                            provedor.getAliasFormaAcesso().equalsIgnoreCase(
                                    Objects.requireNonNullElse(preferencia.provedorSocial(), "")),
                            diagnosticoAvatar.status(),
                            diagnosticoAvatar.mensagem());
                })
                .toList();
        return new VinculosSociaisDto(provedores, preferencia.origem(), preferencia.url());
    }

    private DiagnosticoAvatarSocial diagnosticarAvatarSocial(final ProvedorVinculoSocial provedor,
                                                             final FormaAcesso forma) {
        if (forma == null) {
            return DiagnosticoAvatarSocial.vazio();
        }
        if (!provedor.suportaAvatarPerfil()) {
            return new DiagnosticoAvatarSocial(
                    "PROVEDOR_SEM_SUPORTE_DE_FOTO",
                    "Esta conta esta vinculada, mas este provedor nao disponibiliza foto para uso no perfil neste aplicativo.");
        }
        if (forma != null && forma.getUrlAvatarExterno() != null && !forma.getUrlAvatarExterno().isBlank()) {
            return new DiagnosticoAvatarSocial("FOTO_DISPONIVEL", null);
        }
        if (forma != null && forma.getAvatarExternoAtualizadoEm() != null) {
            return new DiagnosticoAvatarSocial(
                    "FOTO_REMOVIDA_APOS_SINCRONIZACAO",
                    "A foto desta rede social nao esta mais disponivel. Por isso ela deixou de poder ser usada como foto de perfil.");
        }
        return new DiagnosticoAvatarSocial(
                "FOTO_NAO_DISPONIVEL",
                "Esta conta esta vinculada, mas nao ha foto disponivel para usar no perfil neste momento.");
    }

    private ProvedorVinculoSocial validarProvedor(final String aliasProvedor) {
        return resolverProvedor(aliasProvedor)
                .orElseThrow(() -> new ResponseStatusException(
                        BAD_REQUEST,
                        "Provedor social não suportado: " + aliasProvedor));
    }

    private Optional<ProvedorVinculoSocial> resolverProvedor(final String aliasProvedor) {
        if (aliasProvedor == null || aliasProvedor.isBlank()) {
            return Optional.empty();
        }
        return ProvedorVinculoSocial.fromAlias(aliasProvedor.trim().toLowerCase(Locale.ROOT));
    }

    private boolean contemIdentidadeFederada(final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                             final IdentidadeFederadaKeycloak identidadeFederada) {
        return identidadesFederadas.stream()
                .anyMatch(identidade -> identidade.provedor() == identidadeFederada.provedor()
                        && Objects.equals(identidade.identificadorCanonico(), identidadeFederada.identificadorCanonico()));
    }

    private String mascararIdentificador(final String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return null;
        }
        String valor = identificador.trim();
        int indiceArroba = valor.indexOf('@');
        if (indiceArroba > 0) {
            String inicio = valor.substring(0, 1);
            return inicio + "***" + valor.substring(indiceArroba);
        }
        if (valor.length() == 1) {
            return "*";
        }
        if (valor.length() == 2) {
            return valor.substring(0, 1) + "*";
        }
        return valor.substring(0, 1) + "***" + valor.substring(valor.length() - 1);
    }

    private Optional<ProjetoFluxoPublicoResolvido> resolverProjetoOpcional(final String aplicacaoId) {
        if (aplicacaoId == null || aplicacaoId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(resolvedorProjetoFluxoPublico.resolverAtivo(aplicacaoId));
    }

    private void sincronizarAvataresMultiapp(final Jwt jwt,
                                             final Pessoa pessoa,
                                             final OffsetDateTime instanteSincronizacao,
                                             final List<IdentidadeFederadaKeycloak> identidadesFederadas,
                                             final String aplicacaoId) {
        Optional<ProjetoFluxoPublicoResolvido> projeto = resolverProjetoOpcional(aplicacaoId);
        if (projeto.isEmpty()) {
            return;
        }
        avatarSocialProjetoJdbc.sincronizar(
                jwt.getSubject(),
                pessoa.getEmail(),
                projeto.orElseThrow().clienteEcossistemaId(),
                instanteSincronizacao,
                instanteSincronizacao,
                identidadesFederadas);
    }

    private List<IdentidadeFederadaKeycloak> enriquecerIdentidadesFederadas(
            final List<IdentidadeFederadaKeycloak> identidadesFederadas,
            final ProvedorVinculoSocial provedor,
            final String nomeExibicaoExterno,
            final String urlAvatarExterno) {
        return identidadesFederadas.stream()
                .map(identidade -> identidade.provedor() != provedor
                        ? identidade
                        : new IdentidadeFederadaKeycloak(
                                identidade.provedor(),
                                identidade.identificadorExterno(),
                                identidade.nomeUsuarioExterno(),
                                normalizarTexto(identidade.nomeExibicaoExterno(), nomeExibicaoExterno),
                                normalizarTexto(identidade.urlAvatarExterno(), urlAvatarExterno)))
                .toList();
    }

    private String normalizarTexto(final String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.trim();
            }
        }
        return null;
    }

    private record DiagnosticoAvatarSocial(String status, String mensagem) {
        private static DiagnosticoAvatarSocial vazio() {
            return new DiagnosticoAvatarSocial(null, null);
        }
    }
}
