package com.uem.extrator.model;

import javax.persistence.*;
import java.io.Serializable;


@Entity
@Table(name = "SEL_QUALIS")
public class Qualis {

    @Id
    @SequenceGenerator(name = "seqQualis", sequenceName = "SEL.SEQ_QUALIS", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqQualis")
    @Column(name = "id_qualis")
    private Long id;

    @Column(name = "cd_issn", length = 20, unique = true, nullable = false)
    private String issn;

    @Column(name = "nm_revista", length = 500)
    private String nomeRevista;

    @Column(name = "sg_estrato", length = 5)
    private String estrato;

    public Qualis() {}

    public Qualis(String issn, String nomeRevista, String estrato) {
        this.issn = issn;
        this.nomeRevista = nomeRevista;
        this.estrato = estrato;
    }

    // Getters e Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIssn() { return issn; }
    public void setIssn(String issn) { this.issn = issn; }

    public String getNomeRevista() { return nomeRevista; }
    public void setNomeRevista(String nomeRevista) { this.nomeRevista = nomeRevista; }

    public String getEstrato() { return estrato; }
    public void setEstrato(String estrato) { this.estrato = estrato; }
}
