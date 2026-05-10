package com.eickrono.api.identidade.aplicacao.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.apresentacao.dto.PerfilDto;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock
    private PerfilIdentidadeRepositorio perfilRepositorio;
    @Mock
    private ProvisionamentoIdentidadeService provisionamentoIdentidadeService;

    private PerfilService perfilService;

    /**
     * Prepara o service real apontando para um repositório Mockito para que cada teste controle o retorno.
     */
    private void inicializarServico() {
        perfilService = new PerfilService(perfilRepositorio, provisionamentoIdentidadeService);
    }

    /**
     * Cenário feliz: o repositório encontra o perfil e esperamos que o DTO traga todos os campos mapeados.
     * Arrange: configuramos o mock com um PerfilIdentidade preenchido.
     * Act: chamamos buscarPorSub.
     * Assert: garantimos presença e os atributos principais.
     */
    @Test
    @DisplayName("deve mapear PerfilIdentidade para DTO quando encontrar registro")
    void deveRetornarPerfilQuandoEncontrar() {
        inicializarServico();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2024-06-01T10:00:00Z");
        PerfilIdentidade entidade = new PerfilIdentidade(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                atualizadoEm);
        when(perfilRepositorio.findBySub("sub-123")).thenReturn(Optional.of(entidade));

        Optional<PerfilDto> resultado = perfilService.buscarPorSub("sub-123");

        assertThat(resultado).isPresent();
        PerfilDto dto = resultado.orElseThrow();
        assertThat(dto.sub()).isEqualTo("sub-123");
        assertThat(dto.email()).isEqualTo("teste@eickrono.com");
        assertThat(dto.nome()).isEqualTo("Pessoa Teste");
        assertThat(dto.perfis()).containsExactly("CLIENTE");
        assertThat(dto.papeis()).containsExactly("ROLE_cliente");
        assertThat(dto.atualizadoEm()).isEqualTo(atualizadoEm);
    }

    /**
     * Cenário negativo: quando o repositório devolve Optional.empty(), o service deve propagar o vazio.
     */
    @Test
    @DisplayName("deve retornar Optional.empty quando perfil não existir")
    void deveRetornarVazioQuandoNaoEncontrar() {
        inicializarServico();
        when(perfilRepositorio.findBySub("sub-inexistente")).thenReturn(Optional.empty());

        Optional<PerfilDto> resultado = perfilService.buscarPorSub("sub-inexistente");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("deve provisionar e retornar o perfil quando receber um JWT válido")
    void deveProvisionarPerfilComJwt() {
        inicializarServico();
        OffsetDateTime atualizadoEm = OffsetDateTime.parse("2024-06-01T10:00:00Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .subject("sub-456")
                .claim("email", "novo@eickrono.com")
                .claim("name", "Pessoa Nova")
                .claim("perfis", Set.of("CLIENTE"))
                .claim("papeis", Set.of("ROLE_cliente"))
                .header("alg", "none")
                .build();
        when(provisionamentoIdentidadeService.provisionarOuAtualizar(jwt)).thenReturn(new Pessoa(
                "sub-456",
                "novo@eickrono.com",
                "Pessoa Nova",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                atualizadoEm));
        when(perfilRepositorio.findBySub("sub-456")).thenReturn(Optional.of(new PerfilIdentidade(
                "sub-456",
                "novo@eickrono.com",
                "Pessoa Nova",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                atualizadoEm)));

        PerfilDto dto = perfilService.buscarOuProvisionar(jwt);

        assertThat(dto.sub()).isEqualTo("sub-456");
        assertThat(dto.email()).isEqualTo("novo@eickrono.com");
        verify(provisionamentoIdentidadeService).provisionarOuAtualizar(jwt);
    }
}
