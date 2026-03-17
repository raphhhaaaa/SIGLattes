package com.uem.extrator.dto;

public class RelatorioProdutividadeDTO {

    private String curriculoId; // Corrigido para String (idLattes)
    private String nomePesquisador;
    private Integer indiceH;
    private Integer totalArtigos;
    private Integer totalCitacoes;
    private Double taxaAcessoAberto;

    public RelatorioProdutividadeDTO(String curriculoId, String nomePesquisador, Integer indiceH,
                                     Integer totalArtigos, Integer totalCitacoes, Double taxaAcessoAberto) {
        this.curriculoId = curriculoId;
        this.nomePesquisador = nomePesquisador;
        this.indiceH = indiceH != null ? indiceH : 0;
        this.totalArtigos = totalArtigos != null ? totalArtigos : 0;
        this.totalCitacoes = totalCitacoes != null ? totalCitacoes : 0;
        this.taxaAcessoAberto = taxaAcessoAberto != null ? taxaAcessoAberto : 0.0;
    }

    public String getCurriculoId() { return curriculoId; }
    public String getNomePesquisador() { return nomePesquisador; }
    public Integer getIndiceH() { return indiceH; }
    public Integer getTotalArtigos() { return totalArtigos; }
    public Integer getTotalCitacoes() { return totalCitacoes; }
    public Double getTaxaAcessoAberto() { return taxaAcessoAberto; }

    public String getTaxaAcessoAbertoFormatada() {
        return String.format("%.1f%%", taxaAcessoAberto);
    }
}