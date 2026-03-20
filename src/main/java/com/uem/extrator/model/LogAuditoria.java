package com.uem.extrator.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "AUDITORIA_LOG", schema = "LATTESEXTRATOR")
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dataHora;

    @Column(length = 50)
    private String tipo;

    @Column(length = 50)
    private String usuario;

    @Column(length = 100)
    private String identificador;

    @Lob
    @Column(length = 32000)
    private String mensagem;

    public LogAuditoria() {}

    public LogAuditoria(Date dataHora, String tipo, String usuario, String identificador, String mensagem) {
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.usuario = usuario;
        this.identificador = identificador;
        this.mensagem = mensagem;
    }

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Date getDataHora() { return dataHora; }
    public void setDataHora(Date dataHora) { this.dataHora = dataHora; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
}
