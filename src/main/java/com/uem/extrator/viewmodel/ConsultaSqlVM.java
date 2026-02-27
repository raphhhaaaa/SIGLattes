package com.uem.extrator.viewmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.Sessions;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AuditLogService;
import com.mysql.cj.xdevapi.Client;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ConsultaSqlVM {

    private static final Logger log = LoggerFactory.getLogger(ConsultaSqlVM.class);
    private String sqlQuery;
    private List<String> colunas = new ArrayList<>();
    private List<List<Object>> linhas = new ArrayList<>();
    private boolean executando = false;


    @Init
    public void init() {
        Usuario usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");

        // se não houver utilizador ou não for administrador, chuta para fora da página
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
        if (!sqlQuery.trim().toUpperCase().startsWith("SELECT")) {
            Clients.showNotification("Apenas consultas de leitura (SELECT) são permitidas.", "error", null, null, 3000);
            return;
        }

        this.executando = true;
        this.colunas.clear();
        this.linhas.clear();

        Session session = null;

        try {
            Usuario usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");

            String loginUsuario = (usuarioLogado != null) ? usuarioLogado.getLogin() : "SISTEMA";

            AuditLogService logService = new AuditLogService();

            session = HibernateUtil.getSessionFactory().openSession();

            // JDBC puro dentro do Hibernate para conseguir extrair o nome das colunas dinamicamente
            session.doWork(new org.hibernate.jdbc.Work() {
                @Override
                public void execute(Connection connection) throws SQLException {
                    try (Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(sqlQuery)) {

                        ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();

                        // extrai o nome das colunas
                        for (int i = 1; i <= columnCount; i++) {
                            colunas.add(metaData.getColumnName(i));
                        }

                        // extrair os dados linha a linha (limite padrão em 500 para não travar o navegador)
                        int limite = 10000;
                        int count = 0;
                        while (rs.next() && count < limite) {
                            List<Object> linha = new ArrayList<>();
                            for (int i = 1; i <= columnCount; i++) {
                                Object valor = rs.getObject(i);
                                linha.add(valor != null ? valor.toString() : "NULL");
                            }
                            linhas.add(linha);
                            count++;
                        }

                        if (count == limite) {
                            Clients.showNotification("Resultado limitado às primeiras 500 linhas.", "info", null, null, 4000);
                        }
                    }
                }
            });

            logService.registrarLogGeral("CONSOLE_SQL", loginUsuario, "O usuário " + loginUsuario + " executou a query: " + this.sqlQuery + " | " + linhas.size() + " linhas retornadas");
            Clients.showNotification("Consulta executada com sucesso. Linhas retornadas: " + linhas.size(), "info", null, null, 3000);

        } catch (Exception e) {
            e.printStackTrace();
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
            Clients.showNotification("Não há dados para exportar. Execute uma consulta primeiro.", "warning", null, null, 3000);
            return;
        }

        StringBuilder sb = new StringBuilder();

        // 1. Montar os Cabeçalhos
        for (int i = 0; i < colunas.size(); i++) {
            sb.append(colunas.get(i));
            if (i < colunas.size() - 1) sb.append(";");
        }
        sb.append("\n");

        // 2. Montar as Linhas de Dados
        for (List<Object> linha : linhas) {
            for (int i = 0; i < linha.size(); i++) {
                String valor = linha.get(i) != null ? linha.get(i).toString() : "";

                // Limpeza básica para não "quebrar" o CSV (troca ; por , e remove quebras de linha)
                valor = valor.replace("\n", " ").replace("\r", " ").replace(";", ",");

                sb.append(valor);
                if (i < linha.size() - 1) sb.append(";");
            }
            sb.append("\n");
        }

        // 3. Adicionar o BOM (Byte Order Mark) para o Excel reconhecer os acentos (UTF-8) corretamente
        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] dados = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + dados.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(dados, 0, finalBytes, bom.length, dados.length);

        // 4. Disparar o download
        String nomeArquivo = "Consulta_SQL_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv";
        Filedownload.save(finalBytes, "text/csv", nomeArquivo);
    }

    // Getters e Setters

    public String getSqlQuery() { return sqlQuery; }
    public void setSqlQuery(String sqlQuery) { this.sqlQuery = sqlQuery; }
    public List<String> getColunas() { return colunas; }
    public List<List<Object>> getLinhas() {
        return linhas;
    }
    public boolean isExecutando() { return executando; }
}
