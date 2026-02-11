package com.uem.extrator.dao;

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
                         "GROUP BY i.nomeInstituicao " +
                         "ORDER BY COUNT(i) DESC";

            return session.createQuery(hql, Object[].class).list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null) session.close();
        }
    }
}
