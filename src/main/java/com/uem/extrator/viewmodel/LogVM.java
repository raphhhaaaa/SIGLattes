package com.uem.extrator.viewmodel;

import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.SecurityFilter;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Messagebox;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.bind.BindUtils;
import com.uem.extrator.model.Usuario;

import javax.mail.Message;
import java.util.Arrays;
import java.util.List;

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

    @Command
    @NotifyChange({"conteudoLog"})
    public void apagarLog() {
        // evita apagar se já estiver vazio
        if (conteudoLog == null || conteudoLog.startsWith("Nenhum registro")) {
            Clients.showNotification("O log já está vazio.", "warning", null, null, 2000);
            return;
        }

        // caixa de confirmação
        Messagebox.show("Tem certeza que deseja APAGAR TODOS os logs? Está ação não pode ser desfeita. (Backup recomendado)",
                "Confirmação de Segurança", Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION,
                new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) throws Exception {
                        if (Messagebox.ON_YES.equals(event.getName())) {
                            // conta as linhas
                            int qtndLinhas = conteudoLog.split("\n").length;

                            // apaga o arquivo físico
                            auditService.apagarLog();

                            // registra no novo log quem apagou o antigo
                            Usuario usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
                            String login = (usuarioLogado != null) ? usuarioLogado.getLogin() : "SISTEMA";
                            AuditLogService.log("SEGURANCA_ALERTA", login, "O arquivo de log de auditoria anterior foi RESETADO. Histórico anterior de " + qtndLinhas + " linhas foi destruído.");

                            // atualiza os dados
                            carregarLogs();

                            // atualiza a tela
                            BindUtils.postNotifyChange(null, null, LogVM.this, "conteudoLog");

                            Clients.showNotification("Log excluído com sucesso!", "info", null, null, 4000);
                        }
                    }
                });
    }


    // Getters //
    public String getCaminhoArquivo() { return caminhoArquivo; }
    public String getConteudoLog() { return conteudoLog; }
}
