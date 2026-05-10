package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.FormaAcesso;
import com.eickrono.api.identidade.dominio.modelo.Pessoa;
import com.eickrono.api.identidade.dominio.modelo.TipoFormaAcesso;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório das formas de acesso vinculadas a uma pessoa.
 */
public interface FormaAcessoRepositorio extends JpaRepository<FormaAcesso, Long> {

    Optional<FormaAcesso> findByTipoAndProvedorAndIdentificador(TipoFormaAcesso tipo, String provedor,
                                                                String identificador);

    Optional<FormaAcesso> findByPessoaAndTipoAndPrincipalTrue(Pessoa pessoa, TipoFormaAcesso tipo);

    List<FormaAcesso> findByPessoa(Pessoa pessoa);
}
