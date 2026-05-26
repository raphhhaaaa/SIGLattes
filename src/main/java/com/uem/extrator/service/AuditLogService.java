package com.uem.extrator.service;

import com.uem.extrator.model.LogAuditoria;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Calendar;

public class AuditLogService {

    // logger instancias
    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    private static final SimpleDateFormat SDF_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void log(String acao, String usuario, String detalhes) {
        new AuditLogService().registrarLogGeral(acao, usuario, detalhes);
    }

    public static void registrarExtracao(String tipoExtracao, String usuario, boolean sucesso,
                                         String idLattes, String nomePesquisador) {
        if (!ConfigManager.getInstance().isAuditQueries()) return;

        String acao;
        if (tipoExtracao.contains("PULADO") || tipoExtracao.contains("ERRO") || tipoExtracao.contains("NAO_ENCONTRADO")) {
            acao = tipoExtracao;
        } else if (!tipoExtracao.equals("LOTE") && !tipoExtracao.equals("LOTE_CPF")) {
            acao = tipoExtracao + (sucesso ? "_SUCESSO" : "_FALHA");
        } else {
            acao = sucesso ? "LOTE_SUCESSO" : "LOTE_FALHA";
        }

        String detalhes = String.format("Tipo: %s | ID: %s | Nome: %s",
                tipoExtracao,
                (idLattes != null ? idLattes : "N/A"),
                (nomePesquisador != null ? nomePesquisador : "N/A"));

        salvarNoBanco(acao, usuario, idLattes, detalhes);
    }

    public void registrarLogGeral(String acao, String usuario, String detalhes) {
        if (!isAuditoriaHabilitada(acao)) return;
        salvarNoBanco(acao, usuario, "N/A", detalhes);
    }

    private boolean isAuditoriaHabilitada(String acao) {
        ConfigManager config = ConfigManager.getInstance();
        if (acao.startsWith("LOGIN")) return config.isAuditLogins();
        if (acao.startsWith("EXTRACAO") || acao.startsWith("BUSCA") || acao.contains("LOTE")) return config.isAuditQueries();
        if (acao.contains("USUARIO") || acao.equals("SEGURANCA") || acao.equals("CONFIG")) return config.isAuditAdminActions();
        return true;
    }

    public void registrarProcessamento(String idLattes) {
        salvarNoBanco("PROCESSAMENTO", "SISTEMA", idLattes, "Processamento registrado");
    }

    /**
     * OTIMIZAÇÃO DB2: A versão anterior usava DATE(l.dataHora) = CURRENT_DATE.
     *
     * Problema: aplicar uma função (DATE()) sobre a coluna indexada (data_hora)
     * impede o uso do índice no DB2 — o banco é forçado a fazer full table scan
     * e aplicar DATE() em cada linha.
     *
     * Solução: usa um intervalo explícito (>= início do dia AND < início do próximo dia),
     * permitindo que o índice na coluna data_hora seja usado com range scan.
     *
     * Antes:  WHERE DATE(l.dataHora) = CURRENT_DATE           → full scan
     * Depois: WHERE l.dataHora >= :inicioHoje AND l.dataHora < :amanha → index range scan
     */
    public long contarProcessamentosHoje() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // Calcula o intervalo do dia atual no lado Java (sem funções no SQL)
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date inicioHoje = cal.getTime();

            cal.add(Calendar.DAY_OF_MONTH, 1);
            Date inicioAmanha = cal.getTime();

            return session.createQuery(
                    "SELECT COUNT(l) FROM LogAuditoria l " +
                    "WHERE l.dataHora >= :inicioHoje " +
                    "AND l.dataHora < :inicioAmanha " +
                    "AND (l.tipo LIKE '%CPF%' OR l.tipo LIKE '%ID%' OR l.tipo LIKE '%LOTE%')",
                    Long.class)
                    .setParameter("inicioHoje", inicioHoje)
                    .setParameter("inicioAmanha", inicioAmanha)
                    .uniqueResult();

        } catch (Exception e) {
            logger.error("Erro ao contar currículos processados: ", e);
            return 0L;
        }
    }

    /**
     * OTIMIZAÇÃO DB2: Leitura do log limitada aos 10000 mais recentes.
     * Garante que o ORDER BY DESC aproveite o índice em data_hora
     * (o DB2 pode fazer index scan reverso em vez de sort).
     */
    public List<String> lerLogCompleto() {
        List<String> linhas = new ArrayList<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<LogAuditoria> logs = session.createQuery(
                    "FROM LogAuditoria ORDER BY dataHora DESC", LogAuditoria.class)
                    .setMaxResults(10000)
                    .list();

            for (LogAuditoria log : logs) {
                String linha = String.format("%s | %-15s | %-15s | %s",
                        SDF_LOG.format(log.getDataHora()),
                        log.getTipo(),
                        log.getUsuario(),
                        log.getMensagem());
                linhas.add(linha);
            }
        } catch (Exception e) {
            linhas.add("Erro ao ler logs do banco: " + e.getMessage());
        }
        return linhas;
    }

    public String getCaminhoArquivo() {
        return "Armazenado com segurança no Banco de Dados";
    }

    public void apagarLog() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.createQuery("DELETE FROM LogAuditoria").executeUpdate();
                LogAuditoria logReset = new LogAuditoria(
                        new Date(), "SEGURANCA", "SISTEMA", "N/A",
                        "Todos os logs foram apagados do banco.");
                session.save(logReset);
                tx.commit();
            } catch (Exception e) {
                if (tx != null && tx.isActive()) tx.rollback();
                logger.error("Erro ao apagar log: ", e);
            }
        }
    }

    private static void salvarNoBanco(String tipo, String usuario, String identificador, String mensagem) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            LogAuditoria log = new LogAuditoria(
                    new Date(),
                    tipo,
                    (usuario != null && !usuario.isEmpty()) ? usuario : "SISTEMA",
                    (identificador != null && !identificador.isEmpty()) ? identificador : "N/A",
                    mensagem);
            session.save(log);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            logger.error("Erro crítico ao gravar log no banco de dados: ", e);
        }
    }
}
