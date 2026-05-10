package com.eickrono.api.identidade.dominio.repositorio;

import com.eickrono.api.identidade.dominio.modelo.StatusTokenDispositivo;
import com.eickrono.api.identidade.dominio.modelo.TokenDispositivo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenDispositivoRepositorio extends JpaRepository<TokenDispositivo, UUID> {

    @Query("""
            select token
            from TokenDispositivo token
            where token.registro.usuarioSub = :usuarioSub
              and token.status = :status
            """)
    List<TokenDispositivo> findByUsuarioSubAndStatus(@Param("usuarioSub") String usuarioSub,
                                                     @Param("status") StatusTokenDispositivo status);

    @Query("""
            select token
            from TokenDispositivo token
            where token.registro.usuarioSub = :usuarioSub
              and token.tokenHash = :tokenHash
            """)
    Optional<TokenDispositivo> findByUsuarioSubAndTokenHash(@Param("usuarioSub") String usuarioSub,
                                                            @Param("tokenHash") String tokenHash);

    @Query("""
            select token
            from TokenDispositivo token
            where token.registro.usuarioSub = :usuarioSub
              and token.tokenHash = :tokenHash
              and token.status = :status
            """)
    Optional<TokenDispositivo> findByUsuarioSubAndTokenHashAndStatus(@Param("usuarioSub") String usuarioSub,
                                                                     @Param("tokenHash") String tokenHash,
                                                                     @Param("status") StatusTokenDispositivo status);

    Optional<TokenDispositivo> findByTokenHash(String tokenHash);
}
