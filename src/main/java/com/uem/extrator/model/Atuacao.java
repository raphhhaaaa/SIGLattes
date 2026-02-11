package com.uem.extrator.model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Fetch;       // <--- IMPORTANTE
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "ATUACAO", schema = "LATTESEXTRATOR")
public class Atuacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_atuacao")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cd_cnpq", nullable = false)
    private Curriculo curriculo;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cd_instituicao", nullable = false)
    private Instituicao instituicao;

    @Column(name = "nr_sequencia")
    private Integer sequencia;

    // LISTA 1 : VINCULOS (CARGOS/DATAS)
    @OneToMany(mappedBy = "atuacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private List<Vinculo> vinculos = new ArrayList<>();
    
    // LISTA 2 : AS ATIVIDADES (PROJETOS)
    @OneToMany(mappedBy = "atuacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("anoInicio DESC, mesInicio DESC")
    private List<Atividade> atividades = new ArrayList<>();
    
    // construtor // 
    
    public Atuacao() {}
    
    public void adicionarVinculo(Vinculo v) {
        vinculos.add(v);
        v.setAtuacao(this);
    }
    
    public void adicionarAtividade(Atividade a) {
        atividades.add(a);
        a.setAtuacao(this);
    }

    // Getters e Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo curriculo) { this.curriculo = curriculo; }

    public Instituicao getInstituicao() { return instituicao; }
    public void setInstituicao(Instituicao instituicao) { this.instituicao = instituicao; }

    public Integer getSequencia() { return sequencia; }
    public void setSequencia(Integer sequencia) { this.sequencia = sequencia; }

    public List<Vinculo> getVinculos() { return vinculos; }
    public void setVinculos(List<Vinculo> vinculos) { this.vinculos = vinculos; }

    public List<Atividade> getAtividades() { return atividades; }
    public void setAtividades(List<Atividade> atividades) { this.atividades = atividades; }



}
