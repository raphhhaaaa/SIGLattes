package com.uem.extrator.viewmodel;

import com.mysql.cj.xdevapi.Client;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AutomacaoService;
import com.uem.extrator.service.EmailService;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.util.HibernateUtil;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.hibernate.Session;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import com.uem.extrator.service.AutomacaoService;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.awt.SystemColor.desktop;

public class ConfigVM {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(ConfigVM.class);

    // Usuário
    private Usuario usuarioLogado;

    // CNPq
    private String wsdlUrl;
    private Integer timeout;
    private Integer retryAttempts;

    // Estado da Conexão
    private String dbStatus = "Não verificado";
    private String dbStatusClass = "badge text-bg-secondary";
    private String dbStatusState = "question";
    private String poolStatus = "Carregando...";

    // Gerais
    private boolean verifyEnabled;
    private Integer verifyInterval;
    private boolean backupEnabled;
    private String backupTime;
    private String caminhoArquivo;

    // Notificações (SMTP)
    private String smtpHost;
    private String smtpPort;
    private String systemEmail;
    private String adminEmail;
    private boolean notifyOutdated;
    private boolean notifyWeekly;
    private boolean notifyConnection;
    private boolean notifyDisk;

    // Segurança
    private String authType;
    private boolean auditLogins;
    private boolean auditQueries;
    private boolean auditAdminActions;

    @Init
    public void init() {
        // pega Usuario
        usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");

        ConfigManager config = ConfigManager.getInstance();

        // carrega CNPq e gerais
        this.wsdlUrl = config.getWsdlUrl();
        this.timeout = config.getTimeout();
        this.retryAttempts = config.getRetryAttempts();
        this.verifyEnabled = config.isVerifyEnabled();
        this.verifyInterval = config.getVerifyInterval();
        this.backupEnabled = config.isBackupEnabled();
        this.backupTime = config.getBackupTime();
        this.caminhoArquivo = config.getConfigPath();

        // carrega notificações
        this.smtpHost = config.getSmtpHost();
        this.smtpPort = config.getSmtpPort();
        this.systemEmail = config.getSystemEmail();
        this.adminEmail = config.getAdminEmail();
        this.notifyOutdated = config.isNotifyOutdated();
        this.notifyWeekly = config.isNotifyWeekly();
        this.notifyConnection = config.isNotifyConnection();
        this.notifyDisk = config.isNotifyDisk();

        // carrega configs segurança
        this.authType = config.getAuthType();
        this.auditLogins = config.isAuditLogins();
        this.auditQueries = config.isAuditQueries();
        this.auditAdminActions = config.isAuditAdminActions();

        testarConexaoBanco();
    }

    @Command
    @NotifyChange({"dbStatus", "dbStatusClass", "dbStatusState", "poolStatus"})
    public void testarConexaoBanco() {
        this.dbStatus = "Testando...";
        this.dbStatusClass = "badge text-bg-warning";
        this.dbStatusState = "loading"; // Estado de carregamento

        try {
            Session session = HibernateUtil.getSessionFactory().openSession();
            session.createNativeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1").getSingleResult();
            session.close();

            this.dbStatus = "Conectado / Online";
            this.dbStatusClass = "badge text-bg-success";
            this.dbStatusState = "success"; // Estado de sucesso
        } catch (Exception e) {
            logger.error("Erro ao testar conexão do banco: ", e);
            this.dbStatus = "Falha na Conexão";
            this.dbStatusClass = "badge text-bg-danger";
            this.dbStatusState = "error"; // Estado de erro
        }

        atualizarStatusPool();
    }

    @Command
    @NotifyChange("poolStatus")
    public void atualizarStatusPool() {
        this.poolStatus = HibernateUtil.getPoolStatus();
    }

    @Command
    public void salvar() {
        if (wsdlUrl == null || wsdlUrl.trim().isEmpty()) {
            Clients.showNotification("URL vazia!", "warning", null, null, 3000);
            return;
        }

        if (this.verifyInterval == null && isVerifyEnabled()) {
            Clients.showNotification("O intervalo de verificação não pode ser NULO. Por favor insira um valor ou desative a opção.", "error", null, "before_end", 5000);
            return;
        }

        if ((this.backupTime == null || this.backupTime.trim().isEmpty()) && isBackupEnabled()){
            Clients.showNotification("O intervalo de tempo de backup não pode ser NULO. Por favor insira um valor ou desative a opção", "error", null, "before_end", 5000);
            return;
        }

        if (((this.backupTime == null || this.backupTime.trim().isEmpty()) && isBackupEnabled()) && (this.verifyInterval == null && isVerifyEnabled())){
            Clients.showNotification("O intervalo de tempo de backup e de verificação não pode ser NULO. Por favor insira um valor ou desative as opções", "error", null, "before_end", 5000);
            return;
        }

        if (isVerifyEnabled() && this.verifyInterval < 1) {
            Clients.showNotification("O intervalo de verificação deve ser no mínimo 1 hora. Definindo para 1 hora automaticamente.", "warning", null, "before_end", 5000);
            this.verifyInterval = 1; // Auto-corrige na tela
        }

        try {
            ConfigManager config = ConfigManager.getInstance();

            // salva CNPq e gerais
            config.setWsdlUrl(this.wsdlUrl.trim());
            config.setTimeout(this.timeout != null ? this.timeout : 20);
            config.setRetryAttempts(this.retryAttempts != null ? this.retryAttempts : 3);
            config.setVerifyEnabled(this.verifyEnabled);
            config.setVerifyInterval(this.verifyInterval);
            config.setBackupEnabled(this.backupEnabled);
            config.setBackupTime(this.backupTime != null ? this.backupTime : "00:00");

            // salva notificações
            config.setSmtpHost(this.smtpHost);
            config.setSmtpPort(this.smtpPort);
            config.setSystemEmail(this.systemEmail);
            config.setAdminEmail(this.adminEmail);
            config.setNotifyOutdated(this.notifyOutdated);
            config.setNotifyWeekly(this.notifyWeekly);
            config.setNotifyConnection(this.notifyConnection);
            config.setNotifyDisk(this.notifyDisk);

            // salva segurança
            config.setAuthType(this.authType);
            config.setAuditLogins(this.auditLogins);
            config.setAuditQueries(this.auditQueries);
            config.setAuditAdminActions(this.auditAdminActions);

            config.salvar();
            AutomacaoService.getInstance().iniciarAgendamento();

            Clients.showNotification("Salvo com sucesso!", "info", null, null, 3000);
        } catch (Exception e) {
            logger.error("Erro ao salvar configurações: ", e);
            Clients.showNotification("Erro ao salvar: " + e.getMessage(), "error", null, null, 5000);
        }
    }
    // debug
    @Command
    public void testarEnvioEmail() {
        // salva as configurações atuais antes de testar para garantir
        salvar();

        // tenta enviar
        try {
            logger.info("Iniciando teste de envio de e-mail...");
            EmailService.getInstance().enviarAlerta("Teste de Configuração - Extrator Lattes",
                    "<h1>Olá!</h1><p>Se você recebeu este e-mail, a configuração SMTP do Extrator Lattes está <strong>funcionando corretamente</strong>.</p>");

            Clients.showNotification("E-mail de teste enviado! Verifique sua caixa de entrada", "info", null, null, 3000);

        } catch (Exception e) {
            logger.error("Erro ao enviar e-mail: ", e);
            Clients.showNotification("Erro ao enviar :" + e.getMessage(), "error", null, null, 5000);
        }
    }

    // debug
    @Command
    public void forcarVerificacao() {
        salvar();

        Desktop desktop = Executions.getCurrent().getDesktop();

        if (!desktop.isServerPushEnabled()) {
            desktop.enableServerPush(true);
        }

        Clients.showBusy("Rodando verificação de Lattes e Notificações...");

        new Thread(() -> {
            try {
                AutomacaoService.getInstance().forcarExecucaoImediata();

                Executions.schedule(desktop, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) {
                        Clients.clearBusy();
                        Clients.showNotification("Verificação concluída! Verifique o log/email.", "info", null, null, 3000);
                    }
                }, new Event("onNotify"));

            } catch (Exception e) {
                logger.error("Erro ao verificar: ",e);
                Executions.schedule(desktop, new EventListener<Event>() {
                    @Override
                    public void onEvent(Event event) {
                        Clients.clearBusy();
                        Clients.showNotification("Erro: " + e.getMessage(), "error", null, null, 3000);
                    }
                }, new Event("onError"));
            }
        }).start();
    }

    @Command
    @NotifyChange("*")
    public void restaurarPadrao() {
        this.wsdlUrl = "http://localhost:8888/srvcurriculo/WSCurriculo?wsdl";
        this.timeout = 20;
        this.retryAttempts = 3;
        this.verifyEnabled = false;
        this.verifyInterval = 24;
        this.backupEnabled = false;
        this.backupTime = "02:00";
        this.smtpHost = "smtp.gmail.com";
        this.smtpPort = "587";
        this.systemEmail = "";
        this.adminEmail = "";
        this.notifyOutdated = false;
        this.notifyWeekly = false;
        this.notifyConnection = true;
        this.notifyDisk = true;
    }

    // Getters e Setters

    // wsdl e banco de dados
    public String getWsdlUrl() { return wsdlUrl; }
    public void setWsdlUrl(String wsdlUrl) { this.wsdlUrl = wsdlUrl; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(Integer retryAttempts) { this.retryAttempts = retryAttempts; }
    public String getDbStatus() { return dbStatus; }
    public String getDbStatusClass() { return dbStatusClass; }
    public String getDbStatusState() { return dbStatusState; }

    // gerais
    public boolean isVerifyEnabled() { return verifyEnabled; }
    public void setVerifyEnabled(boolean verifyEnabled) { this.verifyEnabled = verifyEnabled; }
    public Integer getVerifyInterval() { return verifyInterval; }
    public void setVerifyInterval(Integer verifyInterval) { this.verifyInterval = verifyInterval; }
    public boolean isBackupEnabled() { return backupEnabled; }
    public void setBackupEnabled(boolean backupEnabled) { this.backupEnabled = backupEnabled; }
    public String getBackupTime() { return backupTime; }
    public void setBackupTime(String backupTime) { this.backupTime = backupTime; }
    public String getCaminhoArquivo() { return caminhoArquivo; }
    public String getPoolStatus() { return poolStatus; }
    public Usuario getUsuarioLogado() {
        return usuarioLogado;
    }

    // notificações
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public String getSmtpPort() { return smtpPort; }
    public void setSmtpPort(String smtpPort) { this.smtpPort = smtpPort; }
    public String getSystemEmail() { return systemEmail; }
    public void setSystemEmail(String systemEmail) { this.systemEmail = systemEmail; }
    public String getAdminEmail() { return adminEmail; }
    public void setAdminEmail(String adminEmail) { this.adminEmail = adminEmail; }
    public boolean isNotifyOutdated() { return notifyOutdated; }
    public void setNotifyOutdated(boolean notifyOutdated) { this.notifyOutdated = notifyOutdated; }
    public boolean isNotifyWeekly() { return notifyWeekly; }
    public void setNotifyWeekly(boolean notifyWeekly) { this.notifyWeekly = notifyWeekly; }
    public boolean isNotifyConnection() { return notifyConnection; }
    public void setNotifyConnection(boolean notifyConnection) { this.notifyConnection = notifyConnection; }
    public boolean isNotifyDisk() { return notifyDisk; }
    public void setNotifyDisk(boolean notifyDisk) { this.notifyDisk = notifyDisk; }

    // segurança
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public boolean isAuditLogins() { return auditLogins; }
    public void setAuditLogins(boolean auditLogins) { this.auditLogins = auditLogins; }
    public boolean isAuditQueries() { return auditQueries; }
    public void setAuditQueries(boolean auditQueries) { this.auditQueries = auditQueries; }
    public boolean isAuditAdminActions() { return auditAdminActions; }
    public void setAuditAdminActions(boolean auditAdminActions) { this.auditAdminActions = auditAdminActions; }

}