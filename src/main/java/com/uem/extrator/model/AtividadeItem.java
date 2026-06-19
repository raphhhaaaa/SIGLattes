package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "SEL_ATIVIDADE_ITEM")
public class AtividadeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atividade_item")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_atividade", nullable = false)
    private Atividade atividade;

    @Column(name = "tp_item", length = 100)
    private String tipoItem;

    @Column(name = "de_item", length = 500)
    private String descricaoItem;

    @Lob
    @Column(name = "de_detalhe", length = 32000)
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
