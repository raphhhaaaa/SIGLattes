package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "VINCULO", schema = "LATTESEXTRATOR", indexes = {
        @Index(name = "idx_vinc_atuacao", columnList = "cd_atuacao"),
        @Index(name = "idx_vinc_anos", columnList = "ano_inicio, ano_fim")
})
public class Vinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_vinculo")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cd_atuacao", nullable = false)
    private Atuacao atuacao;

    @Column(name = "tp_vinculo", length = 100)
    private String tipoVinculo;

    @Column(name = "ds_enquadramento", length = 2000)
    private String descEnquadramento;

    @Column(name = "ds_carga_horaria")
    private String descCargaHoraria;

    @Column(name = "fl_vinculo_empregaticio", length = 1)
    private String  flagVinculoEmpregaticio;

    @Column(name = "ano_inicio")
    private Integer anoInicio;

    @Column(name = "mes_inicio")
    private Integer mesInicio;

    @Column(name = "ano_fim")
    private Integer anoFim;

    @Column(name = "mes_fim")
    private Integer mesFim;

    // construtores //

    public Vinculo() {}

    // ----------- //

    // Getters & Setters //

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Atuacao getAtuacao() { return atuacao; }
    public void setAtuacao(Atuacao atuacao) { this.atuacao = atuacao; }

    public String getTipoVinculo() { return tipoVinculo; }
    public void setTipoVinculo(String tipoVinculo) { this.tipoVinculo = tipoVinculo; }

    public String getDescEnquadramento() { return descEnquadramento; }
    public void setDescEnquadramento(String descEnquadramento) { this.descEnquadramento = descEnquadramento; }

    public String getDescCargaHoraria() { return descCargaHoraria; }
    public void setDescCargaHoraria(String descCargaHoraria) { this.descCargaHoraria = descCargaHoraria; }

    public String getFlagVinculoEmpregaticio() { return flagVinculoEmpregaticio; }
    public void setFlagVinculoEmpregaticio(String flagVinculoEmpregaticio) { this.flagVinculoEmpregaticio = flagVinculoEmpregaticio; }

    public Integer getAnoInicio() { return anoInicio; }
    public void setAnoInicio(Integer anoInicio) { this.anoInicio = anoInicio; }

    public Integer getMesInicio() {return mesInicio; }
    public void setMesInicio(Integer mesInicio) { this.mesInicio = mesInicio; }

    public Integer getAnoFim() { return anoFim; }
    public void setAnoFim(Integer anoFim) { this.anoFim = anoFim; }

    public Integer getMesFim() { return  mesFim; }
    public void setMesFim(Integer mesFim) { this.mesFim = mesFim; }
}
