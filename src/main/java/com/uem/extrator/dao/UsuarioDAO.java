package com.uem.extrator.dao;

import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import javax.xml.crypto.dsig.TransformService;

public class UsuarioDAO {

    public Usuario buscarPorLogin(String login) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            String hql = "FROM Usuario u WHERE u.login = :login";
            Query<Usuario> query = session.createQuery(hql, Usuario.class);
            query.setParameter("login", login);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            session.close();
        }
    }

    public void salvar(Usuario u) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;

        try {
            tx = session.beginTransaction();
            session.saveOrUpdate(u);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            e.printStackTrace();
        } finally {
            session.close();
        }
    }
}
