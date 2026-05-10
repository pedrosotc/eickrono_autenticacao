package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.excecao.FluxoPublicoException;
import com.eickrono.api.identidade.aplicacao.modelo.AvaliacaoSegurancaAplicativoRealizada;
import com.eickrono.api.identidade.apresentacao.dto.fluxo.SegurancaAplicativoApiRequest;
import com.eickrono.api.identidade.infraestrutura.configuracao.AtestacaoAppProperties;
import com.eickrono.api.identidade.infraestrutura.configuracao.SegurancaAplicativoProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AvaliacaoSegurancaAplicativoService {

    private final SegurancaAplicativoProperties properties;
    private final AtestacaoAppProperties atestacaoAppProperties;
    private final AuditoriaService auditoriaService;

    public AvaliacaoSegurancaAplicativoService(final SegurancaAplicativoProperties properties,
                                               final AtestacaoAppProperties atestacaoAppProperties,
                                               final AuditoriaService auditoriaService) {
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
        this.atestacaoAppProperties = Objects.requireNonNull(
                atestacaoAppProperties, "atestacaoAppProperties é obrigatório");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService é obrigatório");
    }

    public AvaliacaoSegurancaAplicativoRealizada avaliar(final String operacao,
                                                         final String aplicacaoId,
                                                         final String plataformaEsperada,
                                                         final SegurancaAplicativoApiRequest requisicao,
                                                         final String sujeitoAuditoria) {
        Objects.requireNonNull(operacao, "operacao é obrigatória");
        Objects.requireNonNull(aplicacaoId, "aplicacaoId é obrigatório");
        Objects.requireNonNull(plataformaEsperada, "plataformaEsperada é obrigatória");
        Objects.requireNonNull(requisicao, "requisicao é obrigatória");
        if (!properties.isHabilitado()) {
            return new AvaliacaoSegurancaAplicativoRealizada(false, true, 0, List.of());
        }

        Set<String> sinaisCalculados = new LinkedHashSet<>();
        int score = 0;

        if (requisicao.rootOuJailbreak()) {
            score += 50;
            sinaisCalculados.add("root_ou_jailbreak");
        }
        if (requisicao.debuggerDetectado()) {
            score += 25;
            sinaisCalculados.add("debugger_detectado");
        }
        if (requisicao.hookingSuspeito()) {
            score += 40;
            sinaisCalculados.add("hooking_suspeito");
        }
        if (requisicao.tamperSuspeito()) {
            score += 35;
            sinaisCalculados.add("tamper_suspeito");
        }
        if (requisicao.riscoCapturaTela()) {
            score += 10;
            sinaisCalculados.add("captura_tela_ativa");
        }
        if (!requisicao.assinaturaValida()) {
            score += 35;
            sinaisCalculados.add("assinatura_invalida");
        }
        if (!requisicao.identidadeAplicativoValida()) {
            score += 35;
            sinaisCalculados.add("identidade_aplicativo_invalida");
        }
        if (!equalsNormalizado(plataformaEsperada, requisicao.plataforma())) {
            score += 45;
            sinaisCalculados.add("plataforma_divergente");
        }
        if (!isAplicacaoPermitida(aplicacaoId)) {
            score += 70;
            sinaisCalculados.add("aplicacao_id_nao_permitida");
        }

        correlacionarComAtestacao(plataformaEsperada, requisicao, sinaisCalculados);
        score += scoreCorrelacao(plataformaEsperada, requisicao);
        score = Math.min(score, 100);

        List<String> sinaisRecebidos = sanitizarSinaisRecebidos(requisicao.sinaisRisco());
        if (!sinaisRecebidos.isEmpty()) {
            sinaisCalculados.addAll(sinaisRecebidos);
        }

        List<String> sinaisOrdenados = List.copyOf(sinaisCalculados);
        auditoriaService.registrarEvento(
                "SEGURANCA_APP_" + operacao.toUpperCase(Locale.ROOT),
                normalizarTexto(sujeitoAuditoria),
                "aplicacaoId=" + normalizarTexto(aplicacaoId)
                        + ";plataforma=" + normalizarTexto(plataformaEsperada)
                        + ";provedor=" + normalizarTexto(requisicao.provedorAtestacao())
                        + ";scoreCalculado=" + score
                        + ";scoreInformadoCliente=" + requisicao.scoreRiscoLocal()
                        + ";sinais=" + sinaisOrdenados
        );

        boolean bloqueada = !properties.isModoObservacao() && score > properties.getScoreMaximoPermitido();
        if (bloqueada) {
            throw new FluxoPublicoException(
                    HttpStatus.FORBIDDEN,
                    "seguranca_aplicativo_reprovada",
                    "Não foi possível validar a segurança do aplicativo nesta operação.",
                    Map.of(
                            "aplicacaoId", normalizarTexto(aplicacaoId),
                            "scoreRisco", score
                    )
            );
        }

        return new AvaliacaoSegurancaAplicativoRealizada(
                bloqueada,
                properties.isModoObservacao(),
                score,
                sinaisOrdenados
        );
    }

    private void correlacionarComAtestacao(final String plataformaEsperada,
                                           final SegurancaAplicativoApiRequest requisicao,
                                           final Set<String> sinaisCalculados) {
        if ("ANDROID".equalsIgnoreCase(plataformaEsperada)) {
            String packageEsperado = atestacaoAppProperties.getGoogle().getPackageName();
            if (packageEsperado != null && !packageEsperado.isBlank()
                    && !equalsNormalizado(packageEsperado, requisicao.packageName())) {
                sinaisCalculados.add("package_name_divergente");
            }
            if (!normalizarTexto(requisicao.provedorAtestacao()).contains("GOOGLE")) {
                sinaisCalculados.add("provedor_android_divergente");
            }
            return;
        }
        if ("IOS".equalsIgnoreCase(plataformaEsperada)) {
            String bundleEsperado = atestacaoAppProperties.getApple().getBundleIdentifier();
            if (bundleEsperado != null && !bundleEsperado.isBlank()
                    && !equalsNormalizado(bundleEsperado, requisicao.bundleIdentifier())) {
                sinaisCalculados.add("bundle_identifier_divergente");
            }
            String teamEsperado = atestacaoAppProperties.getApple().getTeamIdentifier();
            if (teamEsperado != null && !teamEsperado.isBlank()
                    && !equalsNormalizado(teamEsperado, requisicao.teamIdentifier())) {
                sinaisCalculados.add("team_identifier_divergente");
            }
            if (!normalizarTexto(requisicao.provedorAtestacao()).contains("APPLE")) {
                sinaisCalculados.add("provedor_ios_divergente");
            }
        }
    }

    private int scoreCorrelacao(final String plataformaEsperada,
                                final SegurancaAplicativoApiRequest requisicao) {
        int score = 0;
        if ("ANDROID".equalsIgnoreCase(plataformaEsperada)) {
            String packageEsperado = atestacaoAppProperties.getGoogle().getPackageName();
            if (packageEsperado != null && !packageEsperado.isBlank()
                    && !equalsNormalizado(packageEsperado, requisicao.packageName())) {
                score += 40;
            }
            if (!normalizarTexto(requisicao.provedorAtestacao()).contains("GOOGLE")) {
                score += 30;
            }
            return score;
        }
        if ("IOS".equalsIgnoreCase(plataformaEsperada)) {
            String bundleEsperado = atestacaoAppProperties.getApple().getBundleIdentifier();
            if (bundleEsperado != null && !bundleEsperado.isBlank()
                    && !equalsNormalizado(bundleEsperado, requisicao.bundleIdentifier())) {
                score += 40;
            }
            String teamEsperado = atestacaoAppProperties.getApple().getTeamIdentifier();
            if (teamEsperado != null && !teamEsperado.isBlank()
                    && !equalsNormalizado(teamEsperado, requisicao.teamIdentifier())) {
                score += 40;
            }
            if (!normalizarTexto(requisicao.provedorAtestacao()).contains("APPLE")) {
                score += 30;
            }
        }
        return score;
    }

    private boolean isAplicacaoPermitida(final String aplicacaoId) {
        List<String> permitidas = properties.getAplicacoesPermitidas();
        if (permitidas == null || permitidas.isEmpty()) {
            return true;
        }
        return permitidas.stream().anyMatch(item -> equalsNormalizado(item, aplicacaoId));
    }

    private List<String> sanitizarSinaisRecebidos(final List<String> sinaisRisco) {
        if (sinaisRisco == null || sinaisRisco.isEmpty()) {
            return List.of();
        }
        List<String> sanitizados = new ArrayList<>();
        for (String sinal : sinaisRisco) {
            String normalizado = normalizarTexto(sinal);
            if (!normalizado.isBlank()) {
                sanitizados.add(normalizado);
            }
        }
        return List.copyOf(sanitizados);
    }

    private boolean equalsNormalizado(final String esquerdo, final String direito) {
        return normalizarTexto(esquerdo).equalsIgnoreCase(normalizarTexto(direito));
    }

    private String normalizarTexto(final String valor) {
        return valor == null ? "" : valor.trim();
    }
}
