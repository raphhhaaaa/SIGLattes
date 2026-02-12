package com.uem.extrator.dao;

import com.uem.extrator.model.Instituicao;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import java.util.ArrayList;
import java.util.List;

/**
 * Retorna uma lista de Arrays, onde:
 * [0] = Nome da Instituição (String)
 * [1] = Quantidade de Ocorrências (Long)
 */

public class InstituicaoDAO {

    public List<Object[]> listarInstituicoesConsolidadas() {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();

            String hql = "SELECT i.nomeInstituicao, COUNT(i) " +
                    "FROM Instituicao i " +
                    "WHERE i.nomeInstituicao IS NOT NULL " +
                    "AND trim(i.nomeInstituicao) <> '' " +
                    "AND (lower(i.nomeInstituicao) LIKE '%universidade%' " +
                    "     OR lower(i.nomeInstituicao) LIKE '%instituto%' " +
                    "     OR lower(i.nomeInstituicao) LIKE '%centro%' " +
                    "     OR lower(i.nomeInstituicao) LIKE '%faculdade%') " +
                    "GROUP BY i.nomeInstituicao " +
                    "ORDER BY COUNT(i) DESC";

            return session.createQuery(hql, Object[].class).setReadOnly(true).list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null) session.close();
        }
    }

    // Metodo extra caso precise listar TODAS sem filtro de palavra-chave
    public List<Instituicao> listarTodas() {
        Session session = null;
        try {
            session = HibernateUtil.getSessionFactory().openSession();
            // Traz até 1000 instituições ordenadas por nome
            return session.createQuery("FROM Instituicao i ORDER BY i.nomeInstituicao", Instituicao.class)
                    .setMaxResults(1000)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null) session.close();
        }
    }

}
