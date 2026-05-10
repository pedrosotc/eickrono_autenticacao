package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.aplicacao.modelo.ContextoPessoaPerfilSistema;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolvedorContextoAutenticacaoServiceTest {

    @Mock
    private ClienteContextoPessoaPerfilSistema clienteContextoPessoaPerfilSistema;

    @Mock
    private CadastroContaInternaServico cadastroContaInternaServico;

    @InjectMocks
    private ResolvedorContextoAutenticacaoService resolvedorContextoAutenticacaoService;

    @Test
    @DisplayName("deve preferir o contexto de produto quando ele estiver disponível")
    void devePreferirContextoDeProdutoPorEmail() {
        ContextoPessoaPerfilSistema contexto = contexto("sub-123", "ana@eickrono.com", "perfil-1");
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenReturn(Optional.of(contexto));

        Optional<ContextoPessoaPerfilSistema> resultado = resolvedorContextoAutenticacaoService
                .buscarPorEmailPublicoPreferindoProduto(" ana@eickrono.com ");

        assertThat(resultado).contains(contexto);
        verify(clienteContextoPessoaPerfilSistema).buscarPorEmail("ana@eickrono.com");
        verifyNoInteractions(cadastroContaInternaServico);
    }

    @Test
    @DisplayName("deve usar o fallback central quando o produto falhar na busca por email")
    void deveUsarFallbackCentralQuandoProdutoFalhar() {
        ContextoPessoaPerfilSistema contexto = contexto("sub-123", "ana@eickrono.com", "perfil-1");
        when(clienteContextoPessoaPerfilSistema.buscarPorEmail("ana@eickrono.com"))
                .thenThrow(new IllegalStateException("produto indisponivel"));
        when(cadastroContaInternaServico.buscarContextoCentralPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(contexto));

        Optional<ContextoPessoaPerfilSistema> resultado = resolvedorContextoAutenticacaoService
                .buscarPorEmailPublicoPreferindoProduto("ana@eickrono.com");

        assertThat(resultado).contains(contexto);
        verify(cadastroContaInternaServico).buscarContextoCentralPorEmailPublico("ana@eickrono.com");
    }

    @Test
    @DisplayName("deve tentar email central quando nao localizar contexto por sub")
    void deveBuscarPorSubOuEmailComFallbackDeEmail() {
        ContextoPessoaPerfilSistema contexto = contexto("sub-123", "ana@eickrono.com", "perfil-1");
        when(cadastroContaInternaServico.buscarContextoCentralPorSubPublico("sub-123"))
                .thenReturn(Optional.empty());
        when(cadastroContaInternaServico.buscarContextoCentralPorEmailPublico("ana@eickrono.com"))
                .thenReturn(Optional.of(contexto));

        Optional<ContextoPessoaPerfilSistema> resultado = resolvedorContextoAutenticacaoService
                .buscarPorSubOuEmailPublico("sub-123", "ana@eickrono.com");

        assertThat(resultado).contains(contexto);
    }

    private ContextoPessoaPerfilSistema contexto(final String sub,
                                                 final String email,
                                                 final String perfilSistemaId) {
        return new ContextoPessoaPerfilSistema(
                10L,
                sub,
                email,
                "Ana",
                perfilSistemaId,
                "LIBERADO"
        );
    }
}
