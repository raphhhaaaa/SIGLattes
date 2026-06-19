package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "SEL_CURSO")

public class Curso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_curso")
    private Long id;

    @Column(name = "nm_curso", length = 500)
    private String nomeCurso;

    @Column(name = "nm_grande_area", length = 100)
    private String nomeGrandeArea;

    @Column(name = "nm_area", length = 100)
    private String nomeArea;

    @Column(name = "nm_subarea", length = 100)
    private String nomeSubArea;

    @Column(name = "nm_especialidade", length = 100)
    private String nomeEspecialidade;

    // Construtores //

    public Curso() {}

    public Curso(String nomeCurso) {
        this.nomeCurso = nomeCurso;
    }

    // ----------- //

    // Getters e Setters //

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNomeCurso() { return nomeCurso; }
    public void setNomeCurso(String nomeCurso) { this.nomeCurso = nomeCurso; }

    public String getNomeGrandeArea() { return nomeGrandeArea; }
    public void setNomeGrandeArea(String nomeGrandeArea) { this.nomeGrandeArea = nomeGrandeArea; }

    public String getNomeArea() { return nomeArea; }
    public void setNomeArea(String nomeArea) { this.nomeArea = nomeArea; }

    public String getNomeSubArea() { return nomeSubArea; }
    public void setNomeSubArea(String nomeSubArea) { this.nomeSubArea = nomeSubArea; }

    public String getNomeEspecialidade() { return nomeEspecialidade; }
    public void setNomeEspecialidade(String nomeEspecialidade) { this.nomeEspecialidade = nomeEspecialidade; }
}
