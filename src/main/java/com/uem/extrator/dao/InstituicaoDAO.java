package com.uem.extrator.dao;

import com.uem.extrator.model.Instituicao;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import java.util.ArrayList;
import java.util.List;

public class InstituicaoDAO {

    // Cache estático para evitar consultas repetitivas ao banco
    private static List<Object[]> cacheInstituicoes = null;
    private static long ultimaAtualizacao = 0;
    // O cache expira a cada 30 minutos (ajuste se necessário)
    private static final long TEMPO_CACHE = 30 * 60 * 1000;

    public List<Object[]> listarInstituicoesConsolidadas() {
        if (cacheInstituicoes != null && (System.currentTimeMillis() - ultimaAtualizacao) < TEMPO_CACHE) {
            return cacheInstituicoes;
        }

        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();

            String sql = "SELECT nm_instituicao, COUNT(*) AS qtd " +
                    "FROM INSTITUICAO " +
                    "WHERE nm_instituicao IS NOT NULL " +
                    "AND (nm_instituicao LIKE 'UNIVERSIDADE%' " +
                    "     OR nm_instituicao LIKE 'INSTITUTO%' " +
                    "     OR nm_instituicao LIKE 'FACULDADE%' " +
                    "     OR nm_instituicao LIKE 'CENTRO%') " +
                    "GROUP BY nm_instituicao " +
                    "ORDER BY qtd DESC";

            List<Object[]> resultados = session.createNativeQuery(sql).list();

            cacheInstituicoes = resultados;
            ultimaAtualizacao = System.currentTimeMillis();

            return resultados;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null && session.isOpen()) session.close();
        }
    }

    // Método para limpar o cache manualmente (chame isso após uma nova extração massiva)
    public static void limparCache() {
        cacheInstituicoes = null;
    }

    public List<Instituicao> listarTodas() {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            return session.createQuery("FROM Instituicao i ORDER BY i.nomeInstituicao", Instituicao.class)
                    .setMaxResults(500) // Reduzi para 500 para ser mais leve
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null && session.isOpen()) session.close();
        }
    }
}