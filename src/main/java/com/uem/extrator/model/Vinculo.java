package com.uem.extrator.model;

import javax.persistence.*;

@Entity
@Table(name = "SEL_VINCULO", indexes = {
        @Index(name = "idx_vinc_atuacao", columnList = "id_atuacao"),
        @Index(name = "idx_vinc_anos", columnList = "ai_vinculo, af_vinculo")
})
public class Vinculo {

    @Id
    @SequenceGenerator(name = "seqVinculo", sequenceName = "SEL.SEQ_VINCULO", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqVinculo")
    @Column(name = "id_vinculo")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_atuacao", nullable = false)
    private Atuacao atuacao;

    @Column(name = "tp_vinculo", length = 1000)
    private String tipoVinculo;

    @Column(name = "de_enquadramento", length = 4000)
    private String descEnquadramento;

    @Column(name = "ch_vinculo", length = 4000)
    private String descCargaHoraria;

    @Column(name = "fg_vinculo_empregaticio", length = 20)
    private String  flagVinculoEmpregaticio;

    @Column(name = "ai_vinculo")
    private Integer anoInicio;

    @Column(name = "me_inicio")
    private Integer mesInicio;

    @Column(name = "af_vinculo")
    private Integer anoFim;

    @Column(name = "me_fim")
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
