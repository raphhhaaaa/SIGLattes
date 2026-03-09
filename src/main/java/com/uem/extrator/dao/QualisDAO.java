package com.uem.extrator.dao;

import com.uem.extrator.model.Producao;
import com.uem.extrator.model.Qualis;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;

public class QualisDAO {

    public long contarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Long> query = session.createQuery("SELECT COUNT(q) FROM Qualis q", Long.class);
            return query.uniqueResult() != null ? query.uniqueResult() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public Qualis buscarPorIssn(String issn) {
        if (issn == null || issn.trim().isEmpty()) return null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // remove traços
            String hql = "FROM Qualis q WHERE REPLACE(q.issn, '-', '') = REPLACE(:issn, '-', '')";
            Query<Qualis> query = session.createQuery(hql, Qualis.class);
            query.setParameter("issn", issn);
            query.setMaxResults(1);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void salvarEmLote(List<Qualis> listaQualis)  {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            for (int i = 0; i < listaQualis.size(); i++) {
                session.saveOrUpdate(listaQualis.get(i));

                // a cada 50 registros, descarrega para a base de dados e limpa a RAM
                if (i > 0 && i % 50 == 0) {
                    session.flush();
                    session.clear();
                }
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }
}

