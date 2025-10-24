package com.eickrono.api.contas.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.ContaRepositorio;
import com.eickrono.api.contas.dto.ContaResumoDto;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private ContaRepositorio contaRepositorio;
    @Mock
    private AuditoriaEventoContasRepositorio eventoRepositorio;
    @Mock
    private AuditoriaAcessoContasRepositorio acessoRepositorio;

    private AuditoriaContasService auditoriaContasService;
    private ContaService contaService;

    /** Prepara o serviço real com auditoria controlada via mocks. */
    @BeforeEach
    void setUp() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio, acessoRepositorio);
        contaService = new ContaService(contaRepositorio, auditoriaContasService);
    }

    @Nested
    @DisplayName("Caso de uso: listagem de contas")
    class ListarContas {

        /** Esperamos o mapeamento correto dos campos da entidade para o DTO retornado. */
        @Test
        @DisplayName("deve mapear entidades para DTOs quando cliente possuir contas")
        void deveMapearEntidadesParaDtos() throws Exception {
            Conta conta = novaConta("123", "cliente-1", BigDecimal.TEN);
            definirId(conta, 10L);
            when(contaRepositorio.findByClienteId("cliente-1")).thenReturn(List.of(conta));

            List<ContaResumoDto> resultado = contaService.listarPorCliente("cliente-1");

            assertThat(resultado).hasSize(1);
            ContaResumoDto dto = resultado.getFirst();
            assertThat(dto.id()).isEqualTo(10L);
            assertThat(dto.numero()).isEqualTo("123");
            assertThat(dto.saldo()).isEqualTo(BigDecimal.TEN);
        }
    }

    @Nested
    @DisplayName("Caso de uso: consulta de conta específica")
    class BuscarContaPorId {

        /** Quando o cliente é dono da conta, auditoria deve registrar o acesso. */
        @Test
        @DisplayName("deve retornar conta e registrar auditoria quando cliente for titular")
        void deveRetornarContaQuandoClienteForProprietario() throws Exception {
            Conta conta = novaConta("123", "cliente-1", BigDecimal.ONE);
            definirId(conta, 7L);
            when(contaRepositorio.findById(7L)).thenReturn(Optional.of(conta));

            Optional<ContaResumoDto> resultado = contaService.buscarPorId(7L, "cliente-1");

            assertThat(resultado).isPresent();
            ContaResumoDto dto = resultado.orElseThrow();
            assertThat(dto.id()).isEqualTo(7L);

            ArgumentCaptor<AuditoriaAcessoContas> captor = ArgumentCaptor.forClass(AuditoriaAcessoContas.class);
            verify(acessoRepositorio).save(captor.capture());
            assertThat(captor.getValue().getEndpoint()).isEqualTo("/contas/7");
        }

        /** Caso a conta não pertença ao cliente informado, nenhum registro deve ser retornado nem auditado. */
        @Test
        @DisplayName("deve retornar vazio quando conta não pertencer ao cliente")
        void deveRetornarVazioQuandoNaoPertencerAoCliente() throws Exception {
            Conta conta = novaConta("123", "cliente-2", BigDecimal.ONE);
            definirId(conta, 7L);
            when(contaRepositorio.findById(7L)).thenReturn(Optional.of(conta));

            Optional<ContaResumoDto> resultado = contaService.buscarPorId(7L, "cliente-1");

            assertThat(resultado).isEmpty();
            verify(acessoRepositorio, never()).save(any());
        }
    }

    private Conta novaConta(String numero, String clienteId, BigDecimal saldo) {
        OffsetDateTime agora = OffsetDateTime.parse("2024-05-10T09:00:00Z");
        return new Conta(numero, clienteId, saldo, agora.minusDays(10), agora);
    }

    private void definirId(Conta conta, Long id) throws Exception {
        Field field = Conta.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(conta, id);
    }
}
