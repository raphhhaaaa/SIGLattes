package com.uem.extrator.dao;

import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UsuarioDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(UsuarioDAO.class);
    
    public Usuario buscarPorLogin(String login) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Usuario u WHERE u.login = :login";
            Query<Usuario> query = session.createQuery(hql, Usuario.class);
            query.setParameter("login", login);
            return query.uniqueResult();
        } catch (Exception e) {
            logger.error("Erro na base de dados (UsuarioDAO)", e);
            return null;
        }
    }

    public boolean salvar(Usuario u) {

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.saveOrUpdate(u);
                tx.commit();
                return true;
            } catch (Exception e) {
                if (tx != null && tx.isActive()) tx.rollback();
                logger.error("Erro na base de dados (UsuarioDAO)", e);
                return false;
            }
        }
    }

    public List<Usuario> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Usuario ORDER BY nome", Usuario.class).list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (UsuarioDAO)", e);
            return new ArrayList<>();
        }
    }

    public boolean excluir(Usuario u) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                session.delete(u);
                tx.commit();
                return true;
            } catch (Exception e) {
                if (tx != null && tx.isActive()) tx.rollback();
                logger.error("Erro na base de dados (UsuarioDAO)", e);
                return false;
            }
        }
    }
}