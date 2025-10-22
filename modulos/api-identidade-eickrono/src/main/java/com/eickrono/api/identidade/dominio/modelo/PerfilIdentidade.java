package com.eickrono.api.identidade.dominio.modelo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Representa o perfil principal de um usuário.
 */
@Entity
@Table(name = "perfis_identidade")
public class PerfilIdentidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sub;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nome;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfis_identidade_perfis", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "perfil")
    private Set<String> perfis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfis_identidade_papeis", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "papel")
    private Set<String> papeis;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected PerfilIdentidade() {
        // Construtor protegido para JPA.
    }

    public PerfilIdentidade(String sub, String email, String nome, Set<String> perfis, Set<String> papeis,
            OffsetDateTime atualizadoEm) {
        this.sub = sub;
        this.email = email;
        this.nome = nome;
        this.perfis = new LinkedHashSet<>(perfis == null ? Set.of() : perfis);
        this.papeis = new LinkedHashSet<>(papeis == null ? Set.of() : papeis);
        this.atualizadoEm = atualizadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getSub() {
        return sub;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public Set<String> getPerfis() {
        return Set.copyOf(perfis);
    }

    public Set<String> getPapeis() {
        return Set.copyOf(papeis);
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void atualizarPerfil(String novoEmail, String novoNome, Set<String> novosPerfis,
            Set<String> novosPapeis, OffsetDateTime atualizadoEm) {
        this.email = novoEmail;
        this.nome = novoNome;
        this.perfis = new LinkedHashSet<>(novosPerfis == null ? Set.of() : novosPerfis);
        this.papeis = new LinkedHashSet<>(novosPapeis == null ? Set.of() : novosPapeis);
        this.atualizadoEm = atualizadoEm;
    }
}
