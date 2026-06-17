package com.uem.extrator.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigManager {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static ConfigManager instance;
    private Properties properties;
    private final String configPath;


    // !! ESSAS VARIÁVEIS NÃO SÃO ALTERAS PELA TELA DE CONFIGURAÇÕES !! //
    // APIs Externas
    public static final String URL_CNPQ_WSDL = "http://servicosweb.cnpq.br/srvcurriculo/WSCurriculo?wsdl";
    public static final String URL_SEMANTIC_SCHOLAR_SEARCH = "https://api.semanticscholar.org/graph/v1/paper/search";
    public static final String URL_SEMANTIC_SCHOLAR_PAPER = "https://api.semanticscholar.org/graph/v1/paper/";

    // Motor de Concorrência e Rate Limiting
    public static final int MAX_THREADS_EXTRACAO = 30;       // Quantidade de threads simultâneas (Lattes) — reduzido para evitar throttling/ban do CNPq em extrações em lote
    public static final int SEMAFORO_SEMANTIC_SCHOLAR = 1;   // Conexões simultâneas permitidas (Rate Limit 1 RPS)
    public static final int TEMPO_ESPERA_API_RATE_LIMIT_MS = 1000; // Espera para respeitar 1 req/s

    // Throttling de Interface (Maestro UI)
    public static final int TEMPO_ATUALIZACAO_UI_MS = 1500;  // Ritmo de atualização da barra (1.5s)
    public static final int MAX_CARACTERES_LOG_TELA = 30000; // Prevenção de OutOfMemory no navegador


    // valores padrão
    private static final String DEFAULT_WSDL = "http://servicosweb.cnpq.br/srvcurriculo/WSCurriculo?wsdl";
    private static final String DEFAULT_TIMEOUT = "20"; // 20 segundos
    private static final String DEFAULT_RETRIES = "3"; // 3 tentativas
    private static final String DEFAULT_VERIFY_ENABLED = "false";
    private static final String DEFAULT_VERIFY_INTERVAL = "24"; // horas
    private static final String DEFAULT_BACKUP_ENABLED = "false";
    private static final String DEFAULT_BACKUP_TIME = "02:00"; // horário de backup
    private static final String DEFAULT_BACKUP_CONTAINER = "db2_server";
    private static final String DEFAULT_BACKUP_COMMAND = "su - db2inst1 -c 'db2 backup database LATTES online to /database/data include logs'";

    // SMPT
    private static final String DEFAULT_SMTP_HOST = "smtp.gmail.com";
    private static final String DEFAULT_SMTP_PORT = "587";
    private static final String DEFAULT_SMTP_PASSWORD = "";
    private static final String DEFAULT_SYSTEM_EMAIL = "";
    private static final String DEFAULT_ADMIN_EMAIL = "";
    private static final String DEFAULT_SEMANTIC_SCHOLAR_API_KEY = "";
    private static final String DEFAULT_NOTIFY_OUTDATED = "false";
    private static final String DEFAULT_NOTIFY_WEEKLY = "false";
    private static final String DEFAULT_NOTIFY_CONNECTION = "true";
    private static final String DEFAULT_NOTIFY_DISK = "true";

    // SEGURANÇA
    private static final String DEFAULT_AUTH_TYPE = "LOCAL";
    private static final String DEFAULT_AUDIT_LOGINS = "true";
    private static final String DEFAULT_AUDIT_QUERIES = "true";
    private static final String DEFAULT_AUDIT_ADMIN_ACTIONS = "true";

    private ConfigManager() {
        // Tenta salvar no diretorio do Tomcat onde temos certeza de permissão de escrita
        String catBase = System.getProperty("catalina.base");
        if (catBase != null) {
            File confDir = new File(catBase, "conf");
            if (confDir.exists() && confDir.canWrite()) {
                this.configPath = confDir.getAbsolutePath() + File.separator + "lattes_extrator_config.properties";
            } else {
                this.configPath = catBase + File.separator + "logs" + File.separator + "lattes_extrator_config.properties";
            }
        } else {
            this.configPath = System.getProperty("user.home") + File.separator + "lattes_extrator_config.properties";
        }
        
        this.properties = new Properties();
        carregar();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void carregar() {
        File file = new File(configPath);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            } catch (IOException e) {
                logger.error("Erro de I/O nas configurações", e);
            }
        } else {
            // se não existir, cria com padrão
            properties.setProperty("wsdl_url", DEFAULT_WSDL);
            properties.setProperty("timeout", DEFAULT_TIMEOUT);
            properties.setProperty("retries", DEFAULT_RETRIES);
            properties.setProperty("verify_enabled", DEFAULT_VERIFY_ENABLED);
            properties.setProperty("verify_interval", DEFAULT_VERIFY_INTERVAL);
            properties.setProperty("backup_enabled", DEFAULT_BACKUP_ENABLED);
            properties.setProperty("backup_time", DEFAULT_BACKUP_TIME);
            properties.setProperty("backup_container", DEFAULT_BACKUP_CONTAINER);
            properties.setProperty("backup_command", DEFAULT_BACKUP_COMMAND);
            properties.setProperty("smtp_host", DEFAULT_SMTP_HOST);
            properties.setProperty("smtp_port", DEFAULT_SMTP_PORT);
            properties.setProperty("smtp_password", DEFAULT_SMTP_PASSWORD);
            properties.setProperty("system_email", DEFAULT_SYSTEM_EMAIL);
            properties.setProperty("admin_email", DEFAULT_ADMIN_EMAIL);
            properties.setProperty("semantic_scholar_api_key", DEFAULT_SEMANTIC_SCHOLAR_API_KEY);
            properties.setProperty("notify_outdated", DEFAULT_NOTIFY_OUTDATED);
            properties.setProperty("notify_weekly", DEFAULT_NOTIFY_WEEKLY);
            properties.setProperty("notify_connection", DEFAULT_NOTIFY_CONNECTION);
            properties.setProperty("notify_disk", DEFAULT_NOTIFY_DISK);
            properties.setProperty("auth_type", DEFAULT_AUTH_TYPE);
            properties.setProperty("audit_logins", DEFAULT_AUDIT_LOGINS);
            properties.setProperty("audit_queries", DEFAULT_AUDIT_QUERIES);
            properties.setProperty("audit_admin_actions", DEFAULT_AUDIT_ADMIN_ACTIONS);
            salvar();
        }
    }

    public void salvar() {
        try (FileOutputStream fos = new FileOutputStream(configPath)) {
            // garente que chaves nulas não quebrem o store
            if (!properties.containsKey("wsdl_url")) properties.setProperty("wsdl_url", DEFAULT_WSDL);
            properties.store(fos, "Configurações do Extrator Lattes");
        } catch (IOException e) {
            logger.error("Erro de I/O nas configurações", e);
        }
    }

    // Getters e Setters (CNPq)

    public String getWsdlUrl() {
        return properties.getProperty("wsdl_url", DEFAULT_WSDL);
    }
    public void setWsdlUrl(String url) {
        properties.setProperty("wsdl_url", url);
    }

    public int getTimeout() {
        try { return Integer.parseInt(properties.getProperty("timeout", DEFAULT_TIMEOUT)); }
        catch (NumberFormatException e) { return 20; }
    }
    public void setTimeout(int timeout) { properties.setProperty("timeout", String.valueOf(timeout)); }

    public int getRetryAttempts() {
        try { return Integer.parseInt(properties.getProperty("retries", DEFAULT_RETRIES)); }
        catch (NumberFormatException e) { return 3; }
    }

    public void setRetryAttempts(int retries) { properties.setProperty("retries", String.valueOf(retries)); }

    // Getters e Setters (GERAIS)

    public boolean isVerifyEnabled() { return Boolean.parseBoolean(properties.getProperty("verify_enabled", DEFAULT_VERIFY_ENABLED)); }
    public void setVerifyEnabled(boolean b) { properties.setProperty("verify_enabled", String.valueOf(b)); }

    public int getVerifyInterval() {
        try { return Integer.parseInt(properties.getProperty("verify_interval", DEFAULT_VERIFY_INTERVAL)); }
        catch (NumberFormatException e) { return 24; }
    }
    public void setVerifyInterval(int h) { properties.setProperty("verify_interval", String.valueOf(h)); }

    // Backup
    public boolean isBackupEnabled() { return Boolean.parseBoolean(properties.getProperty("backup_enabled", DEFAULT_BACKUP_ENABLED)); }
    public void setBackupEnabled(boolean b) { properties.setProperty("backup_enabled", String.valueOf(b)); }

    public String getBackupTime() { return properties.getProperty("backup_time", DEFAULT_BACKUP_TIME); }
    public void setBackupTime(String t) { properties.setProperty("backup_time", t); }

    public String getBackupContainer() { return properties.getProperty("backup_container", DEFAULT_BACKUP_CONTAINER); }
    public void setBackupContainer(String c) { properties.setProperty("backup_container", c); }

    public String getBackupCommand() { return properties.getProperty("backup_command", DEFAULT_BACKUP_COMMAND); }
    public void setBackupCommand(String c) { properties.setProperty("backup_command", c); }

    public String getConfigPath() {
        return this.configPath;
    }

    // Getters e Setters (Notificações SMTP)
    public String getSmtpHost() { return properties.getProperty("smtp_host", DEFAULT_SMTP_HOST); }
    public void setSmtpHost(String v) { properties.setProperty("smtp_host", v); }

    public String getSmtpPort() { return properties.getProperty("smtp_port", DEFAULT_SMTP_PORT); }
    public void setSmtpPort(String v) { properties.setProperty("smtp_port", v); }

    public String getSmtpPassword() { return properties.getProperty("smtp_password", DEFAULT_SMTP_PASSWORD); }
    public void setSmtpPassword(String v) { properties.setProperty("smtp_password", v); }

    public String getSystemEmail() { return properties.getProperty("system_email", DEFAULT_SYSTEM_EMAIL); }
    public void setSystemEmail(String v) { properties.setProperty("system_email", v); }

    public String getAdminEmail() { return properties.getProperty("admin_email", DEFAULT_ADMIN_EMAIL); }
    public void setAdminEmail(String v) { properties.setProperty("admin_email", v); }

    public String getSemanticScholarApiKey() { return properties.getProperty("semantic_scholar_api_key", DEFAULT_SEMANTIC_SCHOLAR_API_KEY); }
    public void setSemanticScholarApiKey(String v) { properties.setProperty("semantic_scholar_api_key", v); }

    public boolean isNotifyOutdated() { return Boolean.parseBoolean(properties.getProperty("notify_outdated", DEFAULT_NOTIFY_OUTDATED)); }
    public void setNotifyOutdated(boolean v) { properties.setProperty("notify_outdated", String.valueOf(v)); }

    public boolean isNotifyWeekly() { return Boolean.parseBoolean(properties.getProperty("notify_weekly", DEFAULT_NOTIFY_WEEKLY)); }
    public void setNotifyWeekly(boolean v) { properties.setProperty("notify_weekly", String.valueOf(v)); }

    public boolean isNotifyConnection() { return Boolean.parseBoolean(properties.getProperty("notify_connection", DEFAULT_NOTIFY_CONNECTION)); }
    public void setNotifyConnection(boolean v) { properties.setProperty("notify_connection", String.valueOf(v)); }

    public boolean isNotifyDisk() { return Boolean.parseBoolean(properties.getProperty("notify_disk", DEFAULT_NOTIFY_DISK)); }
    public void setNotifyDisk(boolean v) { properties.setProperty("notify_disk", String.valueOf(v)); }

    public String getAuthType() {
        return properties.getProperty("auth_type", "LOCAL");
    }
    public void setAuthType(String authType) { properties.setProperty("auth_type", authType); }

    public boolean isAuditLogins() { return Boolean.parseBoolean(properties.getProperty("audit_logins", "true")); }
    public void setAuditLogins(boolean auditLogins) {
        properties.setProperty("audit_logins", String.valueOf(auditLogins));
    }

    public boolean isAuditQueries() {
        return Boolean.parseBoolean(properties.getProperty("audit_queries", DEFAULT_AUDIT_QUERIES));
    }
    public void setAuditQueries(boolean auditQueries) {
        properties.setProperty("audit_queries", String.valueOf(auditQueries));
    }

    public boolean isAuditAdminActions() {
        return Boolean.parseBoolean(properties.getProperty("audit_admin", "true"));
    }
    public void setAuditAdminActions(boolean auditAdminActions) {
        properties.setProperty("audit_admin", String.valueOf(auditAdminActions));
    }
}
