package com.uem.extrator.model;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ATIVIDADE")
public class Atividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atividade")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_atuacao", nullable = false)
    private Atuacao atuacao;

    @Column(name = "tp_atividade", length = 200)
    private String tipoAtividades;

    @Column(name = "me_inicio")
    private Integer mesInicio;

    @Column(name = "ai_atividade")
    private Integer anoInicio;

    @Column(name = "me_fim")
    private Integer mesFim;

    @Column(name = "af_atividade")
    private Integer anoFim;

    @Column(name = "nm_grande_area", length = 500)
    private String nomeGrandeArea;

    @Column(name = "nm_area", length = 500)
    private String nomeArea;

    @OneToMany(mappedBy = "atividade", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    private List<AtividadeItem> itens = new ArrayList<>();

    // Construtores //

    public Atividade() {
    }

    // ------------ //

    // Getters & Setters //

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public Atuacao getAtuacao() {
        return atuacao;
    }
    public void setAtuacao(Atuacao atuacao) {
        this.atuacao = atuacao;
    }

    public String getTipoAtividades() {
        return tipoAtividades;
    }
    public void setTipoAtividades(String tipoAtividades) {
        this.tipoAtividades = tipoAtividades;
    }

    public Integer getMesInicio() {
        return mesInicio;
    }
    public void setMesInicio(Integer mesInicio) {
        this.mesInicio = mesInicio;
    }

    public Integer getAnoInicio() {
        return anoInicio;
    }
    public void setAnoInicio(Integer anoInicio) {
        this.anoInicio = anoInicio;
    }

    public Integer getMesFim() {
        return mesFim;
    }
    public void setMesFim(Integer mesFim) {
        this.mesFim = mesFim;
    }

    public Integer getAnoFim() {
        return anoFim;
    }
    public void setAnoFim(Integer anoFim) {
        this.anoFim = anoFim;
    }

    public String getNomeGrandeArea() {
        return nomeGrandeArea;
    }
    public void setNomeGrandeArea(String nomeGrandeArea) {
        this.nomeGrandeArea = nomeGrandeArea;
    }

    public String getNomeArea() {
        return nomeArea;
    }
    public void setNomeArea(String nomeArea) {
        this.nomeArea = nomeArea;
    }

    public List<AtividadeItem> getItens() { return itens; }
    public void setItens(List<AtividadeItem> itens) { this.itens = itens; }

    // auxiliar para o front
    @Transient
    public String getNomePrincipal() {
        if (itens == null || itens.isEmpty()) return "---";

        String titulo = null;
        String candidato = null;

        for (AtividadeItem item : itens) {
            String tipo = item.getTipoItem();

            // 1. Projetos
            if ("NOME_PROJETO".equals(tipo)) return item.getDescricaoItem();

            // 2. Orientações
            if ("TITULO_ORIENTACAO".equals(tipo)) return item.getDescricaoItem();

            // 3. Bancas
            if ("TITULO_BANCA".equals(tipo)) {
                titulo = item.getDescricaoItem();
            }
            // Captura o candidato para exibir junto, se houver
            if ("CANDIDATO".equals(tipo) || "ALUNO_ORIENTADO".equals(tipo)) {
                candidato = item.getDescricaoItem();
            }
        }

        // Se achou título de banca
        if (titulo != null) {
            if (candidato != null) {
                return titulo + " (Candidato: " + candidato + ")";
            }
            return titulo;
        }

        // Se não tem título mas tem aluno (fallback)
        if (candidato != null) {
            return "Candidato: " + candidato;
        }

        return "--- Detalhes não identificados ---";
    }

    @Transient
    public String getTipoFormatado() {
        if (tipoAtividades == null) return "";

        switch (tipoAtividades) {
            case "PESQUISA": return "Projeto de Pesquisa";
            case "EXTENSAO": return "Projeto de Extensão";
            case "ORIENTACAO_MESTRADO": return "Orientação (Mestrado)";
            case "ORIENTACAO_DOUTORADO": return "Orientação (Doutorado)";
            case "ORIENTACAO_OUTRA": return "Outras Orientações";
            case "BANCA_MESTRADO": return "Banca (Mestrado)";
            case "BANCA_DOUTORADO": return "Banca (Doutorado)";
            case "BANCA_QUALIFICACAO": return "Banca (Qualificação)";
            case "BANCA_ESPECIALIZACAO": return "Banca (Especialização)";
            case "BANCA_GRADUACAO": return "Banca (Graduação)";
            case "CANDIDATO": return "Candidato";
            case "TITULO_BANCA": return "Título da Banca";
            default: return tipoAtividades.replace("_", " "); // Fallback genérico
        }
    }

    // 2. ÍCONES
    @Transient
    public String getIconeClass() {
        if (tipoAtividades == null) return "z-icon-file";

        if (tipoAtividades.contains("PESQUISA")) return "z-icon-flask"; // 🧪
        if (tipoAtividades.contains("EXTENSAO")) return "z-icon-group"; // 🤝
        if (tipoAtividades.contains("ORIENTACAO")) return "z-icon-graduation-cap"; // 🎓
        if (tipoAtividades.contains("BANCA")) return "z-icon-legal"; // ⚖️

        return "z-icon-file-text";
    }

    // 3. COR DA BADGE (Define a cor de fundo do label no ZUL)
    @Transient
    public String getBadgeClass() {
        if (tipoAtividades == null) return "badge badge-secondary";

        if (tipoAtividades.contains("PESQUISA")) return "badge badge-info";    // Azul
        if (tipoAtividades.contains("EXTENSAO")) return "badge badge-primary"; // Azul escuro
        if (tipoAtividades.contains("ORIENTACAO")) return "badge badge-success";// Verde
        if (tipoAtividades.contains("BANCA")) return "badge badge-warning";    // Amarelo

        return "badge badge-secondary"; // Cinza
    }

}
