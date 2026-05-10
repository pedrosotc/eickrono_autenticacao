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
import java.util.Objects;
import java.util.Set;

/**
 * Agregado principal de identidade do usuário.
 */
@Entity
@Table(name = "pessoas_identidade")
public class Pessoa {

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
    @CollectionTable(name = "pessoas_identidade_perfis", joinColumns = @JoinColumn(name = "pessoa_id"))
    @Column(name = "perfil")
    private Set<String> perfis;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pessoas_identidade_papeis", joinColumns = @JoinColumn(name = "pessoa_id"))
    @Column(name = "papel")
    private Set<String> papeis;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    protected Pessoa() {
        // Construtor protegido para JPA.
    }

    public Pessoa(String sub, String email, String nome, Set<String> perfis, Set<String> papeis,
                  OffsetDateTime atualizadoEm) {
        this.sub = Objects.requireNonNull(sub, "sub é obrigatório");
        this.email = Objects.requireNonNull(email, "email é obrigatório");
        this.nome = Objects.requireNonNull(nome, "nome é obrigatório");
        this.perfis = new LinkedHashSet<>(perfis == null ? Set.of() : perfis);
        this.papeis = new LinkedHashSet<>(papeis == null ? Set.of() : papeis);
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm é obrigatório");
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

    public void atualizar(String novoEmail, String novoNome, Set<String> novosPerfis,
                          Set<String> novosPapeis, OffsetDateTime novoAtualizadoEm) {
        this.email = Objects.requireNonNull(novoEmail, "email é obrigatório");
        this.nome = Objects.requireNonNull(novoNome, "nome é obrigatório");
        this.perfis = new LinkedHashSet<>(novosPerfis == null ? Set.of() : novosPerfis);
        this.papeis = new LinkedHashSet<>(novosPapeis == null ? Set.of() : novosPapeis);
        this.atualizadoEm = Objects.requireNonNull(novoAtualizadoEm, "atualizadoEm é obrigatório");
    }
}
