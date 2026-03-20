package com.uem.extrator.dao;

import com.uem.extrator.model.Producao;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.List;

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
            if (session != null && session.isOpen()) session.close();
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
            if (session != null && session.isOpen()) session.close();
        }
    }
    /**
     * Busca OTIMIZADA: Traz apenas as produções do tipo selecionado,
     * já trazendo junto o nome do pesquisador (FETCH) para não dar query N+1.
     */
    public List<Producao> listarPorTipo(String tipoProducao, String nomeInstituicao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            StringBuilder hql = new StringBuilder();

            // SELECT DISTINCT é essencial porque um pesquisador pode ter
            // múltiplas atuações na mesma instituição
            hql.append("SELECT DISTINCT p FROM Producao p ");
            hql.append("JOIN FETCH p.curriculo c "); // Traz o dono da produção

            if (nomeInstituicao != null && !"TODAS".equals(nomeInstituicao)) {
                // CORREÇÃO DO CAMINHO: Curriculo -> Atuacoes -> Vinculos
                hql.append("JOIN c.atuacoes a ");
                hql.append("JOIN a.instituicao i ");
                hql.append("WHERE p.tipo = :tipo ");
                hql.append("AND i.nomeInstituicao = :nomeInst ");
            } else {
                hql.append("WHERE p.tipo = :tipo ");
            }

            hql.append("ORDER BY p.ano DESC, c.nomeCompleto ASC");

            Query<Producao> query = session.createQuery(hql.toString(), Producao.class);
            query.setParameter("tipo", tipoProducao);

            if (nomeInstituicao != null && !"TODAS".equals(nomeInstituicao)) {
                query.setParameter("nomeInst", nomeInstituicao);
            }

            query.setMaxResults(100); // Limite para manter a performance

            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null && session.isOpen()) session.close();
        }
    }
}
