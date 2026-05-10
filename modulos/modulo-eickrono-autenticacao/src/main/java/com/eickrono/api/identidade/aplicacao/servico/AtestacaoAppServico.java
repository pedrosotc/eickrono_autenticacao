package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.AtestacaoAppInvalidaException;
import com.eickrono.api.identidade.aplicacao.modelo.ComprovanteAtestacaoAppEntrada;
import com.eickrono.api.identidade.aplicacao.modelo.DesafioAtestacaoGerado;
import com.eickrono.api.identidade.aplicacao.modelo.StatusValidacaoAtestacaoApp;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoAtestacaoAppConcluida;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoLocalAtestacaoAppResultado;
import com.eickrono.api.identidade.aplicacao.modelo.ValidacaoOficialAtestacaoAppResultado;
import com.eickrono.api.identidade.dominio.modelo.DesafioAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.OperacaoAtestacaoApp;
import com.eickrono.api.identidade.dominio.modelo.PlataformaAtestacaoApp;
import com.eickrono.api.identidade.dominio.repositorio.DesafioAtestacaoAppRepositorio;
import com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orquestra a emissão de desafios efêmeros e a validação local do comprovante antes da integração com os provedores.
 */
@Service
@Transactional
public class AtestacaoAppServico {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAMANHO_DESAFIO_BYTES = 32;

    private final DesafioAtestacaoAppRepositorio desafioRepositorio;
    private final AtestacaoAppProperties properties;
    private final List<ValidadorOficialAtestacaoApp> validadoresOficiais;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;

    @Autowired
    public AtestacaoAppServico(final DesafioAtestacaoAppRepositorio desafioRepositorio,
                               final AtestacaoAppProperties properties,
                               final List<ValidadorOficialAtestacaoApp> validadoresOficiais,
                               final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this.desafioRepositorio = Objects.requireNonNull(desafioRepositorio, "desafioRepositorio é obrigatório");
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.validadoresOficiais = List.copyOf(Objects.requireNonNull(validadoresOficiais, "validadoresOficiais é obrigatório"));
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
    }

    public AtestacaoAppServico(final DesafioAtestacaoAppRepositorio desafioRepositorio,
                               final AtestacaoAppProperties properties,
                               final List<ValidadorOficialAtestacaoApp> validadoresOficiais) {
        this(desafioRepositorio, properties, validadoresOficiais, null);
    }

    public DesafioAtestacaoGerado gerarDesafio(final OperacaoAtestacaoApp operacao,
                                               final PlataformaAtestacaoApp plataforma,
                                               final String usuarioSub,
                                               final Long pessoaIdPerfil,
                                               final UUID cadastroId,
                                               final UUID registroDispositivoId,
                                               final String ipSolicitante,
                                               final String userAgentSolicitante) {
        Objects.requireNonNull(operacao, "operacao é obrigatória");
        Objects.requireNonNull(plataforma, "plataforma é obrigatória");

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiraEm = agora.plus(properties.getDuracaoDesafio());
        String identificadorDesafio = UUID.randomUUID().toString();
        String desafioBase64 = gerarDesafioBase64();

        DesafioAtestacaoApp desafio = desafioRepositorio.save(new DesafioAtestacaoApp(
                identificadorDesafio,
                desafioBase64,
                operacao,
                plataforma,
                usuarioSub,
                pessoaIdPerfil,
                cadastroId,
                registroDispositivoId,
                ipSolicitante,
                userAgentSolicitante,
                agora,
                expiraEm
        ));
        sincronizarDesafioSeConfigurado(desafio);

        return new DesafioAtestacaoGerado(
                desafio.getIdentificadorDesafio(),
                desafio.getDesafioBase64(),
                desafio.getExpiraEm(),
                desafio.getOperacao(),
                desafio.getPlataforma(),
                desafio.getProvedorEsperado(),
                plataforma == PlataformaAtestacaoApp.ANDROID ? properties.getNumeroProjetoNuvemAndroid() : null
        );
    }

    public ValidacaoLocalAtestacaoAppResultado validarComprovanteLocal(final ComprovanteAtestacaoAppEntrada comprovante) {
        Objects.requireNonNull(comprovante, "comprovante é obrigatório");
        validarCamposObrigatorios(comprovante);

        DesafioAtestacaoApp desafio = desafioRepositorio.findByIdentificadorDesafio(comprovante.identificadorDesafio())
                .orElseThrow(() -> new AtestacaoAppInvalidaException(
                        "desafio_nao_encontrado",
                        "Nenhum desafio de atestação foi encontrado para o identificador informado."
                ));

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (desafio.estaConsumido()) {
            throw new AtestacaoAppInvalidaException(
                    "desafio_ja_consumido",
                    "O desafio de atestação já foi consumido por outra operação."
            );
        }
        if (desafio.estaExpirado(agora)) {
            throw new AtestacaoAppInvalidaException(
                    "desafio_expirado",
                    "O desafio de atestação expirou e precisa ser renovado."
            );
        }
        if (!desafio.getDesafioBase64().equals(comprovante.desafioBase64())) {
            throw new AtestacaoAppInvalidaException(
                    "desafio_divergente",
                    "O comprovante recebido não corresponde ao desafio emitido pelo backend."
            );
        }
        if (desafio.getPlataforma() != comprovante.plataforma()) {
            throw new AtestacaoAppInvalidaException(
                    "plataforma_divergente",
                    "A plataforma do comprovante não corresponde à plataforma do desafio emitido."
            );
        }
        if (desafio.getProvedorEsperado() != comprovante.provedor()) {
            throw new AtestacaoAppInvalidaException(
                    "provedor_divergente",
                    "O provedor do comprovante não corresponde ao provedor esperado para o desafio."
            );
        }
        if (!desafio.getPlataforma().aceita(comprovante.tipoComprovante())) {
            throw new AtestacaoAppInvalidaException(
                    "tipo_comprovante_invalido",
                    "O tipo de comprovante não é aceito para a plataforma informada."
            );
        }

        return new ValidacaoLocalAtestacaoAppResultado(
                desafio.getIdentificadorDesafio(),
                desafio.getOperacao(),
                desafio.getPlataforma(),
                desafio.getProvedorEsperado(),
                desafio.getCriadoEm(),
                desafio.getExpiraEm(),
                comprovante.geradoEm(),
                comprovante.chaveId()
        );
    }

    public ValidacaoAtestacaoAppConcluida validarComprovante(final ComprovanteAtestacaoAppEntrada comprovante) {
        ValidacaoLocalAtestacaoAppResultado validacaoLocal = validarComprovanteLocal(comprovante);
        ValidadorOficialAtestacaoApp validador = validadoresOficiais.stream()
                .filter(candidato -> candidato.suporta(comprovante.plataforma()))
                .findFirst()
                .orElse(null);

        if (validador == null) {
            if (!properties.isPermitirValidacaoLocalSemProvedorOficial()) {
                throw new AtestacaoAppInvalidaException(
                        "provedor_oficial_ausente",
                        "Nao existe validador oficial habilitado para a plataforma informada."
                );
            }
            marcarDesafioComoConsumido(validacaoLocal.identificadorDesafio());
            return new ValidacaoAtestacaoAppConcluida(
                    validacaoLocal,
                    ValidacaoOficialAtestacaoAppResultado.naoExecutada(
                            "Validacao oficial nao executada; ambiente aceitando apenas validacao local."
                    ),
                    StatusValidacaoAtestacaoApp.VALIDADA_LOCALMENTE
            );
        }

        ValidacaoOficialAtestacaoAppResultado validacaoOficial = validador.validar(comprovante, validacaoLocal);
        marcarDesafioComoConsumido(validacaoLocal.identificadorDesafio());
        return new ValidacaoAtestacaoAppConcluida(
                validacaoLocal,
                validacaoOficial,
                validacaoOficial.executada()
                        ? StatusValidacaoAtestacaoApp.VALIDADA_OFICIALMENTE
                        : StatusValidacaoAtestacaoApp.VALIDADA_LOCALMENTE
        );
    }

    public void marcarDesafioComoConsumido(final String identificadorDesafio) {
        DesafioAtestacaoApp desafio = desafioRepositorio.findByIdentificadorDesafio(identificadorDesafio)
                .orElseThrow(() -> new AtestacaoAppInvalidaException(
                        "desafio_nao_encontrado",
                "Nenhum desafio de atestação foi encontrado para o identificador informado."
                ));
        desafio.marcarConsumido(OffsetDateTime.now(ZoneOffset.UTC));
        sincronizarDesafioSeConfigurado(desafio);
    }

    private static String gerarDesafioBase64() {
        byte[] bytes = new byte[TAMANHO_DESAFIO_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void validarCamposObrigatorios(final ComprovanteAtestacaoAppEntrada comprovante) {
        if (comprovante.identificadorDesafio() == null || comprovante.identificadorDesafio().isBlank()) {
            throw new AtestacaoAppInvalidaException("identificador_obrigatorio", "O identificador do desafio é obrigatório.");
        }
        if (comprovante.desafioBase64() == null || comprovante.desafioBase64().isBlank()) {
            throw new AtestacaoAppInvalidaException("desafio_obrigatorio", "O desafio Base64 é obrigatório.");
        }
        if (comprovante.conteudoComprovante() == null || comprovante.conteudoComprovante().isBlank()) {
            throw new AtestacaoAppInvalidaException("conteudo_obrigatorio", "O conteúdo do comprovante é obrigatório.");
        }
        if (comprovante.plataforma() == null) {
            throw new AtestacaoAppInvalidaException("plataforma_obrigatoria", "A plataforma do comprovante é obrigatória.");
        }
        if (comprovante.provedor() == null) {
            throw new AtestacaoAppInvalidaException("provedor_obrigatorio", "O provedor do comprovante é obrigatório.");
        }
        if (comprovante.tipoComprovante() == null) {
            throw new AtestacaoAppInvalidaException(
                    "tipo_comprovante_obrigatorio",
                    "O tipo de comprovante é obrigatório."
            );
        }
        if (comprovante.geradoEm() == null) {
            throw new AtestacaoAppInvalidaException("gerado_em_obrigatorio", "A data de geração do comprovante é obrigatória.");
        }
    }

    private void sincronizarDesafioSeConfigurado(final DesafioAtestacaoApp desafio) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarDesafioAtestacao(desafio);
        }
    }
}
