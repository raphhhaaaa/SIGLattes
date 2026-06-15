package com.uem.extrator.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "SEL_AUDITORIA_LOG")
public class LogAuditoria {

    @Id
    @SequenceGenerator(name = "seqLogAuditoria", sequenceName = "SEL.SEQ_AUDITORIA_LOG", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqLogAuditoria")
    @Column(name = "id_log")
    private Long id;

    @Column(name = "dt_hora", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dataHora;

    @Column(name = "tp_log", length = 50)
    private String tipo;

    @Column(name = "nm_usuario", length = 50)
    private String usuario;

    @Column(name = "cd_identificador", length = 100)
    private String identificador;

    @Lob
    @Column(name = "mg_log", length = 32000)
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
