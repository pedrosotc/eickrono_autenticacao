package com.eickrono.api.contas.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eickrono.api.contas.dominio.modelo.AuditoriaAcessoContas;
import com.eickrono.api.contas.dominio.modelo.Conta;
import com.eickrono.api.contas.dominio.modelo.Transacao;
import com.eickrono.api.contas.dominio.modelo.Transacao.TipoTransacao;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaAcessoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.AuditoriaEventoContasRepositorio;
import com.eickrono.api.contas.dominio.repositorio.ContaRepositorio;
import com.eickrono.api.contas.dominio.repositorio.TransacaoRepositorio;
import com.eickrono.api.contas.dto.TransacaoDto;
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
class TransacaoServiceTest {

    @Mock
    private ContaRepositorio contaRepositorio;
    @Mock
    private TransacaoRepositorio transacaoRepositorio;
    @Mock
    private AuditoriaEventoContasRepositorio eventoRepositorio;
    @Mock
    private AuditoriaAcessoContasRepositorio acessoRepositorio;

    private AuditoriaContasService auditoriaContasService;
    private TransacaoService transacaoService;

    /** Prepara o serviço com dependências mockadas para observar auditoria e acesso. */
    @BeforeEach
    void setUp() {
        auditoriaContasService = new AuditoriaContasService(eventoRepositorio, acessoRepositorio);
        transacaoService = new TransacaoService(contaRepositorio, transacaoRepositorio, auditoriaContasService);
    }

    @Nested
    @DisplayName("Caso de uso: listar transações")
    class ListarTransacoes {

        /** Verificamos que o serviço somente retorna transações do titular e registra auditoria. */
        @Test
        @DisplayName("deve retornar transações ordenadas da conta do cliente")
        void deveRetornarTransacoesERegistrarAuditoria() throws Exception {
            Conta conta = novaConta("cliente-1");
            definirIdConta(conta, 15L);
            when(contaRepositorio.findById(15L)).thenReturn(Optional.of(conta));

            Transacao credito = novaTransacao(conta, 101L, TipoTransacao.CREDITO, BigDecimal.valueOf(100), "Depósito");
            Transacao debito = novaTransacao(conta, 102L, TipoTransacao.DEBITO, BigDecimal.valueOf(50), "Transferência");
            when(transacaoRepositorio.findByContaOrderByEfetivadaEmDesc(conta)).thenReturn(List.of(credito, debito));

            List<TransacaoDto> resultado = transacaoService.listarPorConta(15L, "cliente-1");

            assertThat(resultado).extracting(TransacaoDto::id).containsExactly(101L, 102L);
            ArgumentCaptor<AuditoriaAcessoContas> captor = ArgumentCaptor.forClass(AuditoriaAcessoContas.class);
            verify(acessoRepositorio).save(captor.capture());
            assertThat(captor.getValue().getEndpoint()).isEqualTo("/transacoes");
        }

        /** Caso a conta pertença a outro usuário, uma IllegalArgumentException deve ser lançada. */
        @Test
        @DisplayName("deve lançar exceção quando conta não for do cliente")
        void deveLancarQuandoContaNaoPertencerAoCliente() throws Exception {
            Conta conta = novaConta("cliente-2");
            definirIdConta(conta, 15L);
            when(contaRepositorio.findById(15L)).thenReturn(Optional.of(conta));

            assertThatThrownBy(() -> transacaoService.listarPorConta(15L, "cliente-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Conta não encontrada");
            verify(acessoRepositorio, never()).save(any());
        }
    }

    private Conta novaConta(String clienteId) {
        OffsetDateTime agora = OffsetDateTime.parse("2024-06-01T08:00:00Z");
        return new Conta("123", clienteId, BigDecimal.ZERO, agora.minusDays(3), agora);
    }

    private Transacao novaTransacao(Conta conta, Long id, TipoTransacao tipo, BigDecimal valor, String descricao) throws Exception {
        Transacao transacao = new Transacao(conta, tipo, valor, OffsetDateTime.parse("2024-06-02T10:00:00Z"), descricao);
        Field field = Transacao.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(transacao, id);
        return transacao;
    }

    private void definirIdConta(Conta conta, Long id) throws Exception {
        Field field = Conta.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(conta, id);
    }
}
