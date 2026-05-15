package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ResolvedorContextoAutenticacaoService {

    private final CadastroContaInternaServico cadastroContaInternaServico;

    public ResolvedorContextoAutenticacaoService(final CadastroContaInternaServico cadastroContaInternaServico) {
        this.cadastroContaInternaServico = Objects.requireNonNull(
                cadastroContaInternaServico, "cadastroContaInternaServico é obrigatório");
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

    private static String normalizar(final String valor, final String campo) {
        String normalizado = Objects.requireNonNull(valor, campo + " é obrigatório").trim();
        if (normalizado.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return normalizado;
    }
}
