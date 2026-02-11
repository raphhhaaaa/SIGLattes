package com.uem.extrator.dao;

import com.uem.extrator.model.Producao;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class ProducaoDAO {

    public void atualizarMetricas(Producao producao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            // atualiza apenas os dados da produção específica
            session.update(producao);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        } finally {
            if (session != null) session.close();
        }
    }

    public Long contarTotalProducoes() {
        Session session = HibernateUtil.getSessionFactory().openSession();

        try {
            // hql simples para contar as linhas na tabela Producao
            return session.createQuery("SELECT COUNT(p) FROM Producao p", Long.class).uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        } finally {
            if (session != null) session.close();
        }
    }
}
