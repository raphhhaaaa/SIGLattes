package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "INSTITUICAO", schema = "LATTESEXTRATOR")

public class Instituicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // No MySQL usa auto-increment
    @Column(name = "cd_instituicao")
    private Long id;

    @Column(name = "nm_instituicao", length = 300)
    private String nomeInstituicao;

    @Column(name = "sg_instituicao", length = 20)
    private String siglaInstituicao;

    @Column(name = "nm_pais", length = 100)
    private String nomePais;

    @Column(name = "sg_uf", length = 2)
    private String siglaEstado;

    @Column(name = "nm_cidade", length = 100)
    private String nomeCidade;

    // construtores

    public Instituicao() {}

    public Instituicao(String nomeInstituicao) {
        this.nomeInstituicao = nomeInstituicao;
    }

    // Getters e Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeInstituicao() { return nomeInstituicao; }
    public void setNomeInstituicao(String nomeInstituicao) { this.nomeInstituicao = nomeInstituicao; }

    public String getSiglaInstituicao() { return siglaInstituicao; }
    public void setSiglaInstituicao(String siglaInstituicao) { this.siglaInstituicao = siglaInstituicao; }

    public String getNomePais() { return nomePais; }
    public void setNomePais(String nomePais) { this.nomePais = nomePais; }

    public String getSiglaEstado() { return siglaEstado; }
    public void setSiglaEstado(String siglaEstado) { this.siglaEstado = siglaEstado; }

    public String getNomeCidade() { return nomeCidade; }
    public void setNomeCidade(String nomeCidade) { this.nomeCidade = nomeCidade; }


}
