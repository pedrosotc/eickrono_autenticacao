package com.eickrono.api.identidade.aplicacao.servico;

import com.eickrono.api.identidade.dominio.modelo.DispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.RegistroDispositivo;
import com.eickrono.api.identidade.dominio.modelo.StatusDispositivoIdentidade;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import com.eickrono.api.identidade.dominio.repositorio.DispositivoIdentidadeRepositorio;
import com.eickrono.api.identidade.dominio.repositorio.PessoaRepositorio;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Mantém a identidade explícita do aparelho vinculada à pessoa.
 */
@Service
public class DispositivoIdentidadeService {

    private final DispositivoIdentidadeRepositorio dispositivoRepositorio;
    private final Clock clock;
    private final SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService;

    @Autowired
    public DispositivoIdentidadeService(DispositivoIdentidadeRepositorio dispositivoRepositorio,
                                        Clock clock,
                                        SincronizacaoModeloMultiappService sincronizacaoModeloMultiappService) {
        this.dispositivoRepositorio = dispositivoRepositorio;
        this.clock = clock;
        this.sincronizacaoModeloMultiappService = sincronizacaoModeloMultiappService;
    }

    public DispositivoIdentidadeService(final DispositivoIdentidadeRepositorio dispositivoRepositorio,
                                        final Clock clock) {
        this(dispositivoRepositorio, clock, null);
    }

    public DispositivoIdentidadeService(final DispositivoIdentidadeRepositorio dispositivoRepositorio,
                                        final PessoaRepositorio pessoaRepositorio,
                                        final Clock clock) {
        this(dispositivoRepositorio, clock);
    }

    @Transactional
    public DispositivoIdentidade garantirDispositivo(String usuarioSub,
                                                     Long pessoaIdPerfil,
                                                     RegistroDispositivo registro) {
        String usuarioSubObrigatorio = Objects.requireNonNull(usuarioSub, "usuarioSub é obrigatório");
        RegistroDispositivo registroObrigatorio = Objects.requireNonNull(registro, "registro é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);

        DispositivoIdentidade dispositivo = dispositivoRepositorio
                .findByUsuarioSubAndFingerprint(usuarioSubObrigatorio, registroObrigatorio.getFingerprint())
                .orElseGet(() -> new DispositivoIdentidade(
                        usuarioSubObrigatorio,
                        pessoaIdPerfil,
                        registroObrigatorio.getFingerprint(),
                        registroObrigatorio.getPlataforma(),
                        registroObrigatorio.getVersaoAplicativo().orElse(null),
                        registroObrigatorio.getChavePublica().orElse(null),
                        StatusDispositivoIdentidade.ATIVO,
                        agora,
                        agora));

        dispositivo.atualizarMetadados(
                registroObrigatorio.getPlataforma(),
                registroObrigatorio.getVersaoAplicativo().orElse(null),
                registroObrigatorio.getChavePublica().orElse(null),
                agora);
        dispositivo.atualizarPessoaIdPerfil(pessoaIdPerfil, agora);

        DispositivoIdentidade salvo = dispositivoRepositorio.save(dispositivo);
        sincronizarDispositivoSeConfigurado(salvo);
        return salvo;
    }

    @Transactional
    public DispositivoIdentidade garantirDispositivo(final Pessoa pessoa, final RegistroDispositivo registro) {
        Pessoa pessoaObrigatoria = Objects.requireNonNull(pessoa, "pessoa é obrigatória");
        return garantirDispositivo(pessoaObrigatoria.getSub(), pessoaObrigatoria.getId(), registro);
    }

    @Transactional
    public DispositivoIdentidade garantirDispositivoParaToken(TokenDispositivo token) {
        TokenDispositivo tokenObrigatorio = Objects.requireNonNull(token, "token é obrigatório");
        return tokenObrigatorio.getDispositivo().orElseGet(() -> {
            return garantirDispositivo(
                    tokenObrigatorio.getRegistro().getUsuarioSub().orElseThrow(
                            () -> new IllegalStateException("Token sem usuarioSub no registro associado.")),
                    tokenObrigatorio.getRegistro().getPessoaIdPerfil().orElse(null),
                    tokenObrigatorio.getRegistro());
        });
    }

    private void sincronizarDispositivoSeConfigurado(final DispositivoIdentidade dispositivo) {
        if (sincronizacaoModeloMultiappService != null) {
            sincronizacaoModeloMultiappService.sincronizarDispositivoIdentidade(dispositivo);
        }
    }
}
