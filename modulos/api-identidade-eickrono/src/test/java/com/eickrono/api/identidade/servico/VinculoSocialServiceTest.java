package com.eickrono.api.identidade.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.identidade.dominio.modelo.AuditoriaEventoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.PerfilIdentidade;
import com.eickrono.api.identidade.dominio.modelo.VinculoSocial;
import com.eickrono.api.identidade.dominio.repositorio.AuditoriaEventoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PerfilIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.VinculoSocialRepositorio;
import com.eickrono.api.identidade.dto.CriarVinculoSocialRequisicao;
import com.eickrono.api.identidade.dto.VinculoSocialDto;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VinculoSocialServiceTest {

    @Mock
    private PerfilIdentidadeRepositorio perfilRepositorio;
    @Mock
    private VinculoSocialRepositorio vinculoRepositorio;
    @Mock
    private AuditoriaEventoIdentidadeRepositorio auditoriaRepositorio;

    private VinculoSocialService vinculoSocialService;

    /**
     * Instancia o serviço com dependências Mockito para controlar respostas e capturar auditoria.
     */
    @BeforeEach
    void setUp() {
        AuditoriaService auditoriaService = new AuditoriaService(auditoriaRepositorio);
        vinculoSocialService = new VinculoSocialService(perfilRepositorio, vinculoRepositorio, auditoriaService);
    }

    @Nested
    @DisplayName("Caso de uso: listar vínculos sociais")
    class ListarVinculos {

        /**
         * Arrange: criamos dois vínculos e configuramos o repositório para retorná-los.
         * Act: executamos o método listar.
         * Assert: confirmamos o mapeamento para DTO e a ordem dos registros.
         */
        @Test
        @DisplayName("deve converter vínculos persistidos em DTOs ordenados")
        void deveRetornarVinculosDoPerfil() throws Exception {
            PerfilIdentidade perfil = criarPerfil();
            when(perfilRepositorio.findBySub("sub-123")).thenReturn(Optional.of(perfil));

            VinculoSocial google = criarVinculo(perfil, 1L, "GOOGLE", "123");
            VinculoSocial github = criarVinculo(perfil, 2L, "GITHUB", "abc");
            when(vinculoRepositorio.findByPerfil(perfil)).thenReturn(List.of(google, github));

            List<VinculoSocialDto> resultado = vinculoSocialService.listar("sub-123");

            assertThat(resultado)
                    .extracting(VinculoSocialDto::id, VinculoSocialDto::provedor, VinculoSocialDto::identificador)
                    .containsExactly(tuple(1L, "GOOGLE", "123"), tuple(2L, "GITHUB", "abc"));
        }

        /**
         * Quando o perfil não existe devemos lançar IllegalArgumentException e não acessar o repositório de vínculos.
         */
        @Test
        @DisplayName("deve lançar exceção quando perfil do usuário não existir")
        void deveLancarQuandoPerfilNaoExistir() {
            when(perfilRepositorio.findBySub("sub-inexistente")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vinculoSocialService.listar("sub-inexistente"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Perfil não encontrado");
            verify(vinculoRepositorio, never()).findByPerfil(any());
        }
    }

    @Nested
    @DisplayName("Caso de uso: criar vínculo social")
    class CriarVinculo {

        /**
         * Além de devolver o DTO criado, garantimos que a auditoria é registrada com o tipo correto.
         */
        @Test
        @DisplayName("deve persistir vínculo e registrar auditoria")
        void devePersistirVinculo() throws Exception {
            PerfilIdentidade perfil = criarPerfil();
            when(perfilRepositorio.findBySub("sub-123")).thenReturn(Optional.of(perfil));
            when(vinculoRepositorio.save(any())).thenAnswer(invocation -> {
                VinculoSocial novo = invocation.getArgument(0);
                definirId(novo, 42L);
                return novo;
            });

            VinculoSocialDto dto = vinculoSocialService.criar("sub-123", new CriarVinculoSocialRequisicao("GOOGLE", "123456"));

            assertThat(dto.id()).isEqualTo(42L);
            assertThat(dto.provedor()).isEqualTo("GOOGLE");
            assertThat(dto.identificador()).isEqualTo("123456");

            ArgumentCaptor<AuditoriaEventoIdentidade> captor = ArgumentCaptor.forClass(AuditoriaEventoIdentidade.class);
            verify(auditoriaRepositorio).save(captor.capture());
            assertThat(captor.getValue().getTipoEvento()).isEqualTo("VINCULO_SOCIAL_CRIADO");
        }

        /**
         * Sem perfil associado não devemos salvar nada e a exceção deve indicar o problema.
         */
        @Test
        @DisplayName("deve recusar criação caso o perfil não seja encontrado")
        void deveLancarQuandoPerfilNaoExiste() {
            when(perfilRepositorio.findBySub("sub-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vinculoSocialService.criar("sub-123", new CriarVinculoSocialRequisicao("GOOGLE", "123")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Perfil não encontrado");
            verify(vinculoRepositorio, never()).save(any());
        }
    }

    private PerfilIdentidade criarPerfil() {
        return new PerfilIdentidade(
                "sub-123",
                "teste@eickrono.com",
                "Pessoa Teste",
                Set.of("CLIENTE"),
                Set.of("ROLE_cliente"),
                OffsetDateTime.parse("2024-05-01T12:00:00Z"));
    }

    private VinculoSocial criarVinculo(PerfilIdentidade perfil, Long id, String provedor, String identificador) throws Exception {
        VinculoSocial vinculo = new VinculoSocial(perfil, provedor, identificador, OffsetDateTime.parse("2024-05-02T15:00:00Z"));
        definirId(vinculo, id);
        return vinculo;
    }

    private void definirId(VinculoSocial vinculo, Long id) throws Exception {
        Field field = VinculoSocial.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(vinculo, id);
    }
}
