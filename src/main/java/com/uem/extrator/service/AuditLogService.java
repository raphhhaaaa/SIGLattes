package com.uem.extrator.service;

import com.uem.extrator.model.LogAuditoria;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuditLogService {

    // Mantemos o formatador de data antigo para a interface não perceber a mudança
    private static final SimpleDateFormat SDF_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 1. Assinatura mantida idêntica
    public static void log(String acao, String usuario, String detalhes) {
        new AuditLogService().registrarLogGeral(acao, usuario, detalhes);
    }

    // 2. Assinatura mantida idêntica (usado lá no ExtratorVM)
    public static void registrarExtracao(String tipoExtracao, String usuario, boolean sucesso, String idLattes, String nomePesquisador) {
        if (!ConfigManager.getInstance().isAuditQueries()) { return; }

        // Mantém a regra de status para sabermos se foi pulado, erro, sucesso, etc.
        String acao = sucesso ? "EXTRACAO_SUCESSO" : "EXTRACAO_FALHA";
        if (tipoExtracao.contains("PULADO") || tipoExtracao.contains("ERRO") || tipoExtracao.contains("NAO_ENCONTRADO")) {
            acao = tipoExtracao;
        } else if (!tipoExtracao.equals("LOTE") && !tipoExtracao.equals("LOTE_CPF")) {
            acao = tipoExtracao + (sucesso ? "_SUCESSO" : "_FALHA");
        }

        String detalhes = String.format("Tipo: %s | ID: %s | Nome: %s",
                tipoExtracao,
                (idLattes != null ? idLattes : "N/A"),
                (nomePesquisador != null ? nomePesquisador : "N/A"));

        salvarNoBanco(acao, usuario, idLattes, detalhes);
    }

    // 3. REMOVIDO O SYNCHRONIZED - O gargalo foi eliminado!
    public void registrarLogGeral(String acao, String usuario, String detalhes) {
        if (!isAuditoriaHabilitada(acao)) {
            return;
        }
        salvarNoBanco(acao, usuario, "N/A", detalhes);
    }

    // 4. Lógica original de verificação mantida
    private boolean isAuditoriaHabilitada(String acao) {
        ConfigManager config = ConfigManager.getInstance();
        if (acao.startsWith("LOGIN")) return config.isAuditLogins();
        if (acao.startsWith("EXTRACAO") || acao.startsWith("BUSCA") || acao.contains("LOTE")) return config.isAuditQueries();
        if (acao.contains("USUARIO") || acao.equals("SEGURANCA") || acao.equals("CONFIG")) return config.isAuditAdminActions();
        return true;
    }

    // 5. REMOVIDO O SYNCHRONIZED - Antigo método de processamento agora salva no BD
    public void registrarProcessamento(String idLattes) {
        salvarNoBanco("PROCESSAMENTO", "SISTEMA", idLattes, "Processamento registrado");
    }

    // 6. Contagem para o Dashboard: Assinatura mantida (long), mas agora faz um SELECT COUNT instantâneo
    public long contarProcessamentosHoje() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT COUNT(l) FROM LogAuditoria l WHERE DATE(l.dataHora) = CURRENT_DATE AND (l.tipo LIKE '%SUCESSO%' OR l.tipo = 'PROCESSAMENTO')", Long.class)
                    .uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    // 7. O PULO DO GATO: Retorna List<String> para o LogVM antigo não quebrar!
    public List<String> lerLogCompleto() {
        List<String> linhas = new ArrayList<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Traz apenas os 500 mais recentes para proteger a memória RAM
            List<LogAuditoria> logs = session.createQuery("FROM LogAuditoria ORDER BY dataHora DESC", LogAuditoria.class)
                    .setMaxResults(500)
                    .list();

            for (LogAuditoria log : logs) {
                // Reconstrói a string idêntica ao que o arquivo de texto gerava
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

    // 8. Assinatura mantida - Retorna um texto simbólico para a tela não dar erro
    public String getCaminhoArquivo() {
        return "Armazenado com segurança no Banco de Dados (MySQL)";
    }

    // 9. Assinatura mantida - Agora faz um DELETE FROM na tabela
    public void apagarLog() {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.createQuery("DELETE FROM LogAuditoria").executeUpdate();

            // Cria um registro para auditar quem apagou a tabela
            LogAuditoria logReset = new LogAuditoria(new Date(), "SEGURANCA", "SISTEMA", "N/A", "Todos os logs foram apagados do banco.");
            session.save(logReset);

            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        }
    }

    // --- MÉTODO PRIVADO NOVO --- Responsável por encapsular o Hibernate de forma assíncrona e rápida
    private static void salvarNoBanco(String tipo, String usuario, String identificador, String mensagem) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            LogAuditoria log = new LogAuditoria(
                    new Date(),
                    tipo,
                    (usuario != null && !usuario.isEmpty()) ? usuario : "SISTEMA",
                    (identificador != null && !identificador.isEmpty()) ? identificador : "N/A",
                    mensagem
            );

            session.save(log);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            System.err.println("Erro crítico ao gravar log na base de dados: " + e.getMessage());
        }
    }
}