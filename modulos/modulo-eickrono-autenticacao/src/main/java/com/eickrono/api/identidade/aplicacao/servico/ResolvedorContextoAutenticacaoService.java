package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResolvedorContextoAutenticacaoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolvedorContextoAutenticacaoService.class);

    private final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;
    private final CadastroContaInternaServico cadastroContaInternaServico;

    public ResolvedorContextoAutenticacaoService(final ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema,
                                                 final CadastroContaInternaServico cadastroContaInternaServico) {
        this.clienteContextoPessoaPerfilSistema = Objects.requireNonNull(
                clienteContextoPessoaPerfilSistema, "clienteContextoPessoaPerfilSistema é obrigatório");
        this.cadastroContaInternaServico = Objects.requireNonNull(
                cadastroContaInternaServico, "cadastroContaInternaServico é obrigatório");
    }

    public Optional<ContextoPessoaPerfilSistema> buscarPorEmailPublicoPreferindoProduto(final String emailPrincipal) {
        String emailNormalizado = normalizar(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        return buscarContextoProdutoPorEmailTolerante(emailNormalizado)
                .or(() -> cadastroContaInternaServico.buscarContextoCentralPorEmailPublico(emailNormalizado));
    }

    public Optional<ContextoPessoaPerfilSistema> buscarPorEmailPublico(final String emailPrincipal) {
        String emailNormalizado = normalizar(emailPrincipal, "emailPrincipal").toLowerCase(Locale.ROOT);
        return cadastroContaInternaServico.buscarContextoCentralPorEmailPublico(emailNormalizado);
    }

    public Optional<ContextoPessoaPerfilSistema> buscarPorSubPublico(final String subPessoa) {
        return cadastroContaInternaServico.buscarContextoCentralPorSubPublico(normalizar(subPessoa, "subPessoa"));
    }

    public Optional<ContextoPessoaPerfilSistema> buscarPorSubOuEmailPublico(final String subPessoa,
                                                                             final String emailPrincipal) {
        Optional<ContextoPessoaPerfilSistema> porSub = buscarPorSubPublico(subPessoa);
        if (porSub.isPresent()) {
            return porSub;
        }
        if (emailPrincipal == null || emailPrincipal.isBlank()) {
            return Optional.empty();
        }
        return buscarPorEmailPublico(emailPrincipal);
    }

    private Optional<ContextoPessoaPerfilSistema> buscarContextoProdutoPorEmailTolerante(final String email) {
        try {
            return clienteContextoPessoaPerfilSistema.buscarPorEmail(email);
        } catch (RuntimeException ex) {
            LOGGER.warn("contexto_produto_indisponivel_busca_email email={} motivo={}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    private static String normalizar(final String valor, final String campo) {
        String normalizado = Objects.requireNonNull(valor, campo + " é obrigatório").trim();
        if (normalizado.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }
}
