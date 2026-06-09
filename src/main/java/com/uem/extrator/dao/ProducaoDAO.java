package com.uem.extrator.dao;

import com.uem.extrator.model.Producao;
import com.uem.extrator.util.FiltroSimilaridade;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProducaoDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(ProducaoDAO.class);
    
    public void atualizarMetricas(Producao producao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                // atualiza apenas os dados da produção específica
                session.update(producao);
                tx.commit();
            } catch (Exception e) {
                if (tx != null) tx.rollback();
                logger.error("Erro na base de dados (ProducaoDAO)", e);
            }
        }
    }
    /**
     * Conta produções únicas a partir de uma lista de pares [titulo, doi],
     * aplicando deduplicação em dois níveis:
     *
     *  1. DOI  — identificador global único; deduplicação exata O(1) por hash set.
     *  2. Levenshtein — aplicado APENAS aos artigos sem DOI (mitigação de performance);
     *     agrupa títulos com ≥ 90% de similaridade como sendo a mesma produção.
     *
     * @param producoes lista de Object[] onde [0] = titulo (String) e [1] = doi (String)
     * @return número de produções únicas estimado
     */
    public static int contarProducoesUnicas(List<Object[]> producoes) {
        Set<String> doisVistos       = new HashSet<>();
        List<String> titulosSemDoi  = new ArrayList<>();

        for (Object[] p : producoes) {
            String titulo = p[0] != null ? p[0].toString() : "";
            String doi    = p[1] != null ? p[1].toString().trim().toUpperCase() : "";

            if (!doi.isEmpty()) {
                doisVistos.add(doi); // DOI é único: deduplicação exata e barata
            } else {
                titulosSemDoi.add(titulo); // sem DOI: vai para Levenshtein
            }
        }

        // Levenshtein apenas para artigos sem DOI (grupo reduzido ~43% do total)
        List<String> canonicos = new ArrayList<>();
        for (String titulo : titulosSemDoi) {
            boolean jaExiste = false;
            for (String canonico : canonicos) {
                if (FiltroSimilaridade.isMesmaProducao(titulo, canonico)) {
                    jaExiste = true;
                    break;
                }
            }
            if (!jaExiste) canonicos.add(titulo);
        }

        return doisVistos.size() + canonicos.size();
    }

    public Long contarTotalProducoes() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // hql simples para contar as linhas na tabela Producao
            return session.createQuery("SELECT COUNT(p) FROM Producao p", Long.class).uniqueResult();
        } catch (Exception e) {
            logger.error("Erro na base de dados (ProducaoDAO)", e);
            return 0L;
        }
    }
    /**
     * Busca OTIMIZADA: Traz apenas as produções do tipo selecionado,
     * já trazendo junto o nome do pesquisador (FETCH) para não dar query N+1.
     */
    public List<Producao> listarPorTipo(String tipoProducao, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
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
            logger.error("Erro na base de dados (ProducaoDAO)", e);
            return new ArrayList<>();
        }
    }
}
