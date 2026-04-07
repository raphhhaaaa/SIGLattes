package com.uem.extrator.dao;

import com.uem.extrator.model.Curso;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CursoDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(CursoDAO.class);
    
    public List<Curso> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Traz todos os cursos ordenados por nome
            return session.createQuery("from Curso order by nomeCurso", Curso.class).list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CursoDAO)", e);
            return null;
        }
    }

    public Curso buscarPorNome(Session session, String nomeCursoStr) {
        if (nomeCursoStr == null || nomeCursoStr.trim().isEmpty()) return null;

        String hql = "FROM Curso WHERE upper(trim(nomeCurso)) = :nome";

        Query<Curso> query = session.createQuery(hql, Curso.class);
        // normaliza para maiúsculo e remove espacos para comparar.
        query.setParameter("nome", nomeCursoStr.trim().toUpperCase());
        query.setMaxResults(1);

        return query.uniqueResult();
    }
}
