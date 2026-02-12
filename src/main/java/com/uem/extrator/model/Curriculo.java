package com.uem.extrator.model;

import javax.persistence.*;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "CURRICULO", schema = "LATTESEXTRATOR", indexes = {
        @Index(name = "idx_curr_nome", columnList = "nm_completo"), // Acelera ordenação alfabética
        @Index(name = "idx_curr_lattes", columnList = "cd_cnpq")
})
public class Curriculo {

    @Id
    @Column(name = "cd_cnpq", length = 16, nullable = false)
    private String idLattes; // cd_cnpq no banco

    @Temporal(TemporalType.DATE)
    @Column(name = "dt_atualizacao")
    private Date dataAtualizacao;

    @Column(name = "nm_completo", length = 200)
    private String nomeCompleto;

    @Column(name = "nm_citacao", length = 1000)
    private String nomeCitacao;

    @Column(name = "ds_orcid", length = 50)
    private String orcid;

    @Lob
    @Column(name = "ds_resumo")
    private String resumo;

    // Relacionamento com Formação
    @OneToMany(mappedBy = "curriculo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<Formacao> formacoes = new ArrayList<>();

    // Relacionamento com Produção (mapear depois)
    @OneToMany(mappedBy = "curriculo", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<Producao> producoes = new ArrayList<>();

    @OneToMany(mappedBy = "curriculo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    private List<Atuacao> atuacoes = new ArrayList<>();

    public Curriculo() {}

    // Getters e Setters

    public String getIdLattes() {
        return idLattes;
    }

    public void setIdLattes(String idLattes) {
        this.idLattes = idLattes;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public String getResumo() {
        return resumo;
    }

    public void setResumo(String resumo) {
        this.resumo = resumo;
    }

    public Date getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(Date dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public List<Formacao> getFormacoes() {
        return formacoes;
    }
    public void setFormacoes(List<Formacao> formacoes) {
        this.formacoes = formacoes;
    }

    public String getNomeCitacao() { return nomeCitacao; }
    public void setNomeCitacao(String nomeCitacao) { this.nomeCitacao = nomeCitacao; }

    public String getOrcid() { return orcid; }
    public void setOrcid(String orcid) { this.orcid = orcid; }

    public List<Producao> getProducoes() { return producoes; }
    public void setProducoes(List<Producao> producoes) { this.producoes = producoes; }

    public List<Atuacao> getAtuacoes() { return atuacoes; }
    public void setAtuacoes(List<Atuacao> atuacoes) { this.atuacoes = atuacoes; }

    // Metodos auxiliares

    public void adicionarFormacao(Formacao formacao) {
        this.formacoes.add(formacao);  // Adiciona na lista
        formacao.setCurriculo(this);   // Define o Pai (Importante para a FK não ficar null)
    }

    public void adicionarProducao(Producao p) {
        p.setCurriculo(this);
        this.producoes.add(p);
    }

    public void adicionarAtuacao(Atuacao atuacao) {
        this.atuacoes.add(atuacao);
        atuacao.setCurriculo(this);
    }

    // --- Retorna o resumo cortado para exibição ---
    public String getResumoCompactado() {
        if (this.resumo == null) {
            return "";
        }
        if (this.resumo.length() <= 250) {
            return this.resumo;
        }
        return this.resumo.substring(0, 250) + "...";
    }

}
