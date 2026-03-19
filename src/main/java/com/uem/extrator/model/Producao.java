package com.uem.extrator.model;

import javax.persistence.*;
import javax.transaction.TransactionScoped;
import java.util.Date;

@Entity
@Table(name = "PRODUCAO", schema = "LATTESEXTRATOR", indexes = {
        @Index(name = "idx_prod_tipo", columnList = "tp_producao"), // Acelera filtro por Artigo/Evento
        @Index(name = "idx_prod_ano", columnList = "ano_producao"),   // Acelera ordenação por ano
        @Index(name = "idx_prod_curr", columnList = "curriculo_id"), // por id
        @Index(name = "idx_prod_hash", columnList = "hash_titulo")
})

public class Producao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "curriculo_id", nullable = false)
    private Curriculo curriculo;

    // campos gerais
    @Column(name = "tp_producao", length = 50)
    private String tipo; // "ARTIGO", "LIVRO", "EVENTO"

    @Column(name = "ds_titulo", length = 1000)
    private String titulo;

    @Column(name = "hash_titulo", length = 64)
    private String hashTitulo;

    @Column(name = "ano_producao")
    private Integer ano;

    @Column(name = "nm_pais", length = 100)
    private String pais;

    @Column(name = "ds_idioma", length = 50)
    private String idioma;

    @Column(name = "ds_doi", length = 200)
    private String doi;

    @Column(name = "nr_citacoes")
    private Integer citacoes;

    @Column(name = "ds_acesso", length = 20)
    private String statusAcesso = "-";

    @Column(name = "dt_atualizacao_metricas")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dataAtualizacaoMetricas;

    // campos específicos (revista, editora, evento, etc)

    @Column(name = "nm_veiculo", length = 500)
    private String nomeVeiculo;         // Nome da revista ou editora

    @Column(name = "nm_evento", length = 500)
    private String nomeEvento;      // Nome do congresso (se for evento)

    @Column(name = "cd_isbn_issn", length = 50)
    private String isbnIssn;        // Código de identificação internacional de livros (isbn) e artigos (issn).

    @Column(name = "ds_vol_paginas", length = 100)
    private String volumePaginas;

    @Column(name = "ds_natureza", length = 50)
    private String natureza;        // "COMPLETO", "RESUMO"

    @Transient
    private String qualisDescricaoCache;

    @Transient
    private String qualisCorCache;

    public Producao() {}

    // Getters e Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo curriculo) { this.curriculo = curriculo; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getHashTitulo() { return hashTitulo; }
    public void setHashTitulo(String hashTitulo) { this.hashTitulo = hashTitulo; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }

    public String getIdioma() { return idioma;}
    public void setIdioma(String idioma) { this.idioma = idioma; }

    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }

    public String getNomeVeiculo() { return nomeVeiculo; }
    public void setNomeVeiculo(String nomeVeiculo) { this.nomeVeiculo = nomeVeiculo; }

    public String getNomeEvento() { return nomeEvento; }
    public void setNomeEvento(String nomeEvento) { this.nomeEvento = nomeEvento; }

    public String getIsbnIssn() { return isbnIssn; }
    public void setIsbnIssn(String isbnIssn) { this.isbnIssn = isbnIssn; }

    public String getVolumePaginas() { return volumePaginas; }
    public void setVolumePaginas(String volumePaginas) { this.volumePaginas = volumePaginas; }

    public String getNatureza() { return natureza; }
    public void setNatureza(String natureza) { this.natureza = natureza; }

    public Integer getCitacoes() { return citacoes; }
    public void setCitacoes(Integer citacoes) { this.citacoes = citacoes; }

    public String getStatusAcesso() { return statusAcesso; }
    public void setStatusAcesso(String statusAcesso) { this.statusAcesso = statusAcesso; }

    public Date getDataAtualizacaoMetricas() { return dataAtualizacaoMetricas; }
    public void setDataAtualizacaoMetricas(Date dataAtualizacaoMetricas) { this.dataAtualizacaoMetricas = dataAtualizacaoMetricas; }

    public void setQualisDescricaoCache(String qualisDescricaoCache) { this.qualisDescricaoCache = qualisDescricaoCache; }
    public void setQualisCorCache(String qualisCorCache) { this.qualisCorCache = qualisCorCache; }

    @Transient
    public String getCorAcesso() {
        if ("ABERTO".equals(this.statusAcesso)) return "success"; // verde
        if ("FECHADO".equals(this.statusAcesso)) return "danger"; // vermelho
        return "secondary"; // cinza
    }

    public void setCorAcesso(String corAcesso) {}

    public String getQualisDescricao() {
        return qualisDescricaoCache != null ? qualisDescricaoCache : "S/N";
    }

    public String getQualisCor() {
        return qualisCorCache != null ? qualisCorCache : "badge bg-secondary";
    }
}

