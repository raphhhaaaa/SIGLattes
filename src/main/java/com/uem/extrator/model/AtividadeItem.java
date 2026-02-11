package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "ATIVIDADE_ITEM", schema = "LATTESEXTRATOR")
public class AtividadeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_atividade_item")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cd_atividade", nullable = false)
    private Atividade atividade;

    @Column(name = "tp_item", length = 100)
    private String tipoItem;

    @Column(name = "ds_item", length = 500)
    private String descricaoItem;

    @Lob
    @Column(name = "ds_detalhe")
    private String detalhe;

    // Construtor //

    public AtividadeItem() {}

    // --------- //

    // Getters & Setters //

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Atividade getAtividade() { return atividade; }
    public void setAtividade(Atividade atividade) { this.atividade = atividade; }

    public String getTipoItem() { return tipoItem; }
    public void setTipoItem(String tipoItem) { this.tipoItem = tipoItem; }

    public String getDescricaoItem() { return descricaoItem; }
    public void setDescricaoItem(String descricaoItem) {
        if (descricaoItem != null && descricaoItem.length() > 500) {
            this.descricaoItem = descricaoItem.substring(0, 500);

        } else {
            this.descricaoItem = descricaoItem;
        }
    }

    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }

}
