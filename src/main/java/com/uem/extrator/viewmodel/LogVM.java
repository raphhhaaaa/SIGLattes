package com.uem.extrator.viewmodel;

import com.uem.extrator.service.AuditLogService;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;

public class LogVM {

    private String conteudoLog;
    private String caminhoArquivo;
    private AuditLogService auditService = new AuditLogService();

    @Init
    public void init() {
        carregarLogs();
        this.caminhoArquivo = auditService.getCaminhoArquivo();
    }

    @Command
    @NotifyChange("conteudoLog")
    public void carregarLogs() {
        StringBuilder sb = new StringBuilder();
        for (String linha : auditService.lerLogCompleto()) {
            sb.append(linha).append("\n");
        }

        if (sb.length() == 0) {
            this.conteudoLog = "Nenhum registro de log encontrado (Arquivo vazio).";
        } else {
            this.conteudoLog = sb.toString();
        }
    }


    // Getters //
    public String getCaminhoArquivo() { return caminhoArquivo; }
    public String getConteudoLog() { return conteudoLog; }
}
