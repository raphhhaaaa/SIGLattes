package com.uem.extrator.viewmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Sessions;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.ArrayList;
import java.util.List;

public class ConsultaSqlVM {

    private static final Logger logger = LoggerFactory.getLogger(ConsultaSqlVM.class);

    private Usuario usuarioLogado;
    private String sqlQuery;
    private List<String> colunas = new ArrayList<>();
    private List<List<Object>> linhas = new ArrayList<>();
    private boolean executando = false;

    @Init
    public void init() {
        usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");

        if (usuarioLogado == null || !usuarioLogado.isAdmin()) {
            Executions.sendRedirect("/index.zul");
        }
    }

    @Command
    @NotifyChange({"colunas", "linhas", "executando"})
    public void executarQuery() {

        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            Clients.showNotification("Digite uma consulta SQL.", "warning", null, null, 3000);
            return;
        }

        // trava de segurança: permite apenas comandos que comecem com SELECT (apenas leitura)
        String queryNormalizada = sqlQuery.trim().toUpperCase();
        if (!queryNormalizada.startsWith("SELECT")) {
            Clients.showNotification("Apenas consultas de leitura (SELECT) são permitidas.", "error", null, null, 3000);
            return;
        }

        // trava de segurança: bloqueia múltiplos comandos em uma única string (previne chaining)
        if (sqlQuery.contains(";")) {
            if (sqlQuery.trim().indexOf(";") != sqlQuery.trim().length() - 1) {
                Clients.showNotification("Execução de múltiplos comandos (;) não é permitida por segurança.", "error", null, null, 3000);
                return;
            }
        }

        // trava de segurança: bloqueia comandos potencialmente destrutivos
        if (queryNormalizada.matches("(?s).*\\b(DROP|DELETE|UPDATE|INSERT|ALTER|TRUNCATE|EXEC|GRANT|REVOKE|REPLACE|CREATE|COMMIT|ROLLBACK)\\b.*")) {
            Clients.showNotification("ERRO DE SEGURANÇA: A sua consulta contém comandos não permitidos.", "error", null, null, 5000);
            return;
        }

        // Remove ponto e vírgula do final da query para evitar falha silenciosa no JDBC do DB2
        final String queryExecucao;
        String temp = sqlQuery.trim();
        if (temp.endsWith(";")) {
            queryExecucao = temp.substring(0, temp.length() - 1);
        } else {
            queryExecucao = temp;
        }

        this.executando = true;
        this.colunas.clear();
        this.linhas.clear();

        Session session = null;

        try {
            String loginUsuario = (usuarioLogado != null) ? usuarioLogado.getLogin() : "SISTEMA";
            AuditLogService logService = new AuditLogService();

            session = HibernateUtil.getSessionFactory().openSession();

            session.doWork(new org.hibernate.jdbc.Work() {
                @Override
                public void execute(Connection connection) throws SQLException {
                    try (Statement stmt = connection.createStatement();
                         ResultSet rs = stmt.executeQuery(queryExecucao)) {

                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        for (int i = 1; i <= columnCount; i++) {
                            colunas.add(metaData.getColumnName(i));
                        }

                        int limite = 10000;
                        int count = 0;
                        while (rs.next() && count < limite) {
                            List<Object> linha = new ArrayList<>();
                            for (int i = 1; i <= columnCount; i++) {
                                Object valor = rs.getObject(i);
                                if (valor == null) {
                                    linha.add("NULL");
                                } else if (valor instanceof Clob) {
                                    Clob clob = (Clob) valor;
                                    String texto = clob.getSubString(1, (int) Math.min(clob.length(), 200));
                                    linha.add(texto + "...");
                                } else {
                                    linha.add(valor.toString());
                                }
                            }
                            linhas.add(linha);
                            count++;
                        }

                        if (count == limite) {
                            Clients.showNotification("Resultado limitado às primeiras " + limite + " linhas.", "info", null, null, 4000);
                        }
                    }
                }
            });

            logService.registrarLogGeral("CONSOLE_SQL", loginUsuario, "Executou a query: " + this.sqlQuery + " | " + linhas.size() + " linhas retornadas");
            Clients.showNotification("Consulta executada com sucesso.", "info", null, null, 3000);

        } catch (Exception e) {
            logger.error("Erro ao executar query SQL: ", e);
            Clients.showNotification("Erro SQL: " + e.getMessage(), "error", null, null, 5000);
        } finally {
            if (session != null) session.close();
            this.executando = false;
        }
    }

    @Command
    @NotifyChange({"sqlQuery", "colunas", "linhas"})
    public void limpar() {
        this.sqlQuery = "";
        this.colunas.clear();
        this.linhas.clear();
    }

    @Command
    public void exportarCsv() {
        if (linhas == null || linhas.isEmpty()) {
            Clients.showNotification("Não há dados para exportar.", "warning", null, null, 3000);
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < colunas.size(); i++) {
            sb.append(colunas.get(i));
            if (i < colunas.size() - 1) sb.append(";");
        }
        sb.append("\n");

        for (List<Object> linha : linhas) {
            for (int i = 0; i < linha.size(); i++) {
                String valor = linha.get(i) != null ? linha.get(i).toString() : "";
                valor = valor.replace("\n", " ").replace("\r", " ");
                if (valor.contains(";") || valor.contains("\"")) {
                    valor = "\"" + valor.replace("\"", "\"\"") + "\"";
                }
                sb.append(valor);
                if (i < linha.size() - 1) sb.append(";");
            }
            sb.append("\n");
        }

        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] dados = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + dados.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(dados, 0, finalBytes, bom.length, dados.length);

        String nomeArquivo = "Consulta_SQL_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv";
        Filedownload.save(finalBytes, "text/csv", nomeArquivo);
    }

    public String getSqlQuery() { return sqlQuery; }
    public void setSqlQuery(String sqlQuery) { this.sqlQuery = sqlQuery; }
    public List<String> getColunas() { return colunas; }
    public List<List<Object>> getLinhas() { return linhas; }
    public boolean isExecutando() { return executando; }
    public Usuario getUsuarioLogado() { return usuarioLogado; }
}
