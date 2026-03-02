package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "FORMACAO", schema = "LATTESEXTRATOR", indexes = {
        @Index(name = "idx_formacao_curriculo", columnList = "cd_cnpq"),
        @Index(name = "idx_formacao_tipo_ano", columnList = "tp_formacao, ano_conclusao")
})

public class Formacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_formacao")
    private Long id;

    @ManyToOne // relação com curriculo
    @JoinColumn(name = "cd_cnpq", nullable = false)
    private Curriculo curriculo;

    @Column(name = "tp_formacao", length =  50)
    private String tipoFormacao;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cd_curso")
    private Curso nomeCurso;

    // @Column(name = "nm_curso", length = 300)
    // private String nomeCurso;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cd_instituicao")
    private Instituicao nomeInstituicao;

    @Column(name = "ano_inicio")
    private Integer anoInicio;

    @Column(name = "ano_conclusao")
    private Integer anoConclusao;

    @Column(name = "ds_status", length = 50)
    private String status; // Ex: CONCLUIDO, EM_ANDAMENTO

    public Formacao() {}

    public Formacao(String tipoFormacao, Instituicao nomeInstituicao, Curso nomeCurso, Integer anoInicio, Integer anoConclusao,
                    String status) {
        this.tipoFormacao = tipoFormacao;
        this.nomeInstituicao = nomeInstituicao;
        this.nomeCurso = nomeCurso;
        this.anoInicio = anoInicio;
        this.anoConclusao = anoConclusao;
        this.status = status;
    }

    // Getters e Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo curriculo) { this.curriculo = curriculo; }

    public String getTipoFormacao() { return tipoFormacao; }
    public void setTipoFormacao(String tipoFormacao) { this.tipoFormacao = tipoFormacao; }

    public Curso getNomeCurso() { return nomeCurso; }
    public void setNomeCurso(Curso nomeCurso) { this.nomeCurso = nomeCurso; }

    // public String getNomeCurso() { return nomeCurso; }
    // public void setNomeCurso(String nomeCurso) { this.nomeCurso = nomeCurso; }

    public Instituicao getNomeInstituicao() { return nomeInstituicao; }
    public void setNomeInstituicao(Instituicao nomeInstituicao) { this.nomeInstituicao = nomeInstituicao; }

    public Integer getAnoInicio() { return anoInicio; }
    public void setAnoInicio(Integer anoInicio) { this.anoInicio = anoInicio; }

    public Integer getAnoConclusao() { return anoConclusao; }
    public void setAnoConclusao(Integer anoConclusao) { this.anoConclusao = anoConclusao; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
