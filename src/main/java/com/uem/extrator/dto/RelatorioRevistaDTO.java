package com.uem.extrator.dto;

public class RelatorioRevistaDTO {
    private String nomeRevista;
    private String issn;
    private String qualis;
    private Long quantidadeArtigos;

    public RelatorioRevistaDTO(String nomeRevista, String issn, String qualis, Long quantidadeArtigos) {
        this.nomeRevista = nomeRevista;
        this.issn = issn;
        this.qualis = (qualis != null && !qualis.trim().isEmpty()) ? qualis : "S/N";
        this.quantidadeArtigos = quantidadeArtigos;
    }

    public String getNomeRevista() { return nomeRevista; }
    public String getIssn() { return issn; }
    public String getQualis() { return qualis; }
    public Long getQuantidadeArtigos() { return quantidadeArtigos; }

    public String getQualisCor() {
        if (qualis.equals("S/N")) return "badge bg-secondary";
        switch (qualis.toUpperCase()) {
            case "A1": case "A2": return "badge bg-success";
            case "B1": case "B2": return "badge bg-primary";
            case "B3": case "B4": return "badge bg-info text-dark";
            case "C": return "badge bg-warning text-dark";
            default: return "badge bg-secondary";
        }
    }
}
