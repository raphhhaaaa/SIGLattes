package com.uem.extrator.dao;

import com.uem.extrator.model.Qualis;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class QualisDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(QualisDAO.class);
    
    public long contarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery("SELECT COUNT(q) FROM Qualis q", Long.class);
            return query.uniqueResult() != null ? query.uniqueResult() : 0;
        } catch (Exception e) {
            logger.error("Erro na base de dados (QualisDAO)", e);
            return 0;
        }
    }

    public void salvarEmLote(List<Qualis> listaQualis) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                for (int i = 0; i < listaQualis.size(); i++) {
                    session.saveOrUpdate(listaQualis.get(i));
                    if (i > 0 && i % 50 == 0) {
                        session.flush();
                        session.clear();
                    }
                }
                tx.commit();
            } catch (Exception e) {
                if (tx != null && tx.isActive()) tx.rollback();
                logger.error("Erro na base de dados (QualisDAO)", e);
            }
        }
    }

    public void limparTudo() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.createNativeQuery("DELETE FROM QUALIS").executeUpdate();
                tx.commit();
            } catch (Exception e) {
                if (tx != null && tx.isActive()) tx.rollback();
                logger.error("Erro ao limpar tabela QUALIS: ", e);
            }
        }
    }

    /**
     * OTIMIZAÇÃO DB2: A versão anterior usava REPLACE(q.issn, '-', '') diretamente
     * na cláusula WHERE/JOIN, impedindo o uso do índice na coluna issn.
     *
     * Solução: normaliza os ISSNs no lado Java antes de enviar ao banco,
     * e busca por dois formatos (com e sem hífen) usando IN, permitindo que
     * o índice idx (coluna issn) seja utilizado.
     *
     * Antes:  WHERE REPLACE(q.issn, '-', '') IN :issns   → full scan na tabela QUALIS
     * Depois: WHERE q.issn IN :issnsNorm                 → index seek na coluna issn
     */
    public Map<String, Qualis> buscarPorIssns(Collection<String> issns) {
        if (issns == null || issns.isEmpty()) return Collections.emptyMap();

        // Gera dois conjuntos: com hífen (ex: 1234-5678) e sem (ex: 12345678)
        // Isso cobre qualquer formato que esteja gravado no banco
        Set<String> termosParaBusca = new HashSet<>();
        Map<String, String> normalizadoParaOriginal = new HashMap<>();

        for (String issn : issns) {
            if (issn == null) continue;
            String semHifen = issn.replace("-", "").trim();
            String comHifen = semHifen.length() == 8
                    ? semHifen.substring(0, 4) + "-" + semHifen.substring(4)
                    : issn.trim();

            termosParaBusca.add(semHifen);
            termosParaBusca.add(comHifen);
            // guarda o mapeamento normalizado→original para o resultado final
            normalizadoParaOriginal.put(semHifen, semHifen);
            normalizadoParaOriginal.put(comHifen, semHifen);
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // HQL puro sem funções na coluna — o índice de issn é aproveitado
            String hql = "FROM Qualis q WHERE q.issn IN :termos";
            List<Qualis> lista = session.createQuery(hql, Qualis.class)
                    .setParameter("termos", termosParaBusca)
                    .list();

            Map<String, Qualis> resultado = new HashMap<>();
            for (Qualis q : lista) {
                // chave normalizada (sem hífen) para facilitar o lookup no CurriculoDAO
                String chave = q.getIssn().replace("-", "");
                // em caso de duplicata de ISSN com estratos diferentes, mantém o de maior peso
                resultado.merge(chave, q, (existente, novo) -> pesoEstrato(novo.getEstrato()) > pesoEstrato(existente.getEstrato()) ? novo : existente);
            }
            return resultado;

        } catch (Exception e) {
            logger.error("Erro na base de dados (QualisDAO)", e);
            return Collections.emptyMap();
        }
    }

    public static int pesoEstrato(String estrato) {
        if (estrato == null) return -1;
        switch (estrato.toUpperCase().trim()) {
            case "A1": return 8;
            case "A2": return 7;
            case "A3": return 6;
            case "A4": return 5;
            case "B1": return 4;
            case "B2": return 3;
            case "B3": return 2;
            case "B4": return 1;
            case "C":  return 0;
            default:   return -1;
        }
    }
}
