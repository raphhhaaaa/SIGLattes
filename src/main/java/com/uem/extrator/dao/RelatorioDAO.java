package com.uem.extrator.dao;

import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelatorioDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(RelatorioDAO.class);
    
    /**
     * OTIMIZAÇÃO DB2: Queries de gráfico por ano.
     *
     * Problemas da versão anterior:
     * 1. JOIN ON com comparação de colunas (ano >= anoInicio AND ano <= anoFim)
     *    gerava Nested Loop sem índice eficiente no DB2.
     * 2. COUNT(DISTINCT p.hashTitulo) em conjunto com múltiplos JOINs causava
     *    sort temporário em disco (SORTHEAP).
     *
     * Solução: mantém a query mas remove o filtro de período no JOIN de vínculo
     * para as consultas gerais de produção por ano, pois o filtro temporal
     * já é implícito pelo próprio ano da produção.
     * Para filtro por instituição, usa subquery EXISTS que permite ao DB2
     * usar índices independentes em cada tabela.
     */
    public List<Object[]> gerarRelatorio(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            String termoBusca = resolverTermoBusca(tipoRelatorio);
            if (termoBusca == null) return new ArrayList<>();

            boolean filtrarInstituicao = nomeInstituicao != null && !"TODAS".equals(nomeInstituicao);
            boolean isFormacao = "DOUTORADO".equals(tipoRelatorio) || "MESTRADO".equals(tipoRelatorio);

            StringBuilder hql = new StringBuilder();

            if (isFormacao) {
                if (filtrarInstituicao) {
                    /*
                     * OTIMIZAÇÃO: usa EXISTS em vez de JOIN para que o DB2 possa
                     * fazer um "semi-join" com early termination usando o índice
                     * idx_atu_curr (curriculo) e idx_inst_nome (nomeInstituicao).
                     */
                    hql.append("SELECT f.anoConclusao, COUNT(f.id) ")
                       .append("FROM Formacao f ")
                       .append("WHERE f.tipoFormacao = :termo ")
                       .append("AND f.anoConclusao IS NOT NULL ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = f.curriculo ")
                       .append("  AND UPPER(i.nomeInstituicao) = :nomeInst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= f.anoConclusao) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= f.anoConclusao)")
                       .append(") ")
                       .append("GROUP BY f.anoConclusao ")
                       .append("ORDER BY f.anoConclusao DESC");
                } else {
                    hql.append("SELECT f.anoConclusao, COUNT(f.id) ")
                       .append("FROM Formacao f ")
                       .append("WHERE f.tipoFormacao = :termo ")
                       .append("AND f.anoConclusao IS NOT NULL ")
                       .append("GROUP BY f.anoConclusao ")
                       .append("ORDER BY f.anoConclusao DESC");
                }
            } else {
                if (filtrarInstituicao) {
                    /*
                     * OTIMIZAÇÃO: EXISTS em vez de JOIN + DISTINCT.
                     * COUNT(DISTINCT hashTitulo) com JOIN gerava sort extra no DB2.
                     * Com EXISTS, o DB2 executa um index lookup separado para cada
                     * produção, aproveitando idx_prod_curr e idx_atu_inst.
                     */
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) ")
                       .append("FROM Producao p ")
                       .append("WHERE p.tipo = :termo ")
                       .append("AND p.ano IS NOT NULL ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = p.curriculo ")
                       .append("  AND UPPER(i.nomeInstituicao) = :nomeInst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= p.ano) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= p.ano)")
                       .append(") ")
                       .append("GROUP BY p.ano ")
                       .append("ORDER BY p.ano DESC");
                } else {
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) ")
                       .append("FROM Producao p ")
                       .append("WHERE p.tipo = :termo ")
                       .append("AND p.ano IS NOT NULL ")
                       .append("GROUP BY p.ano ")
                       .append("ORDER BY p.ano DESC");
                }
            }

            Query<Object[]> query = session.createQuery(hql.toString(), Object[].class);
            query.setParameter("termo", termoBusca);
            if (filtrarInstituicao) {
                query.setParameter("nomeInst", nomeInstituicao.toUpperCase());
            }

            return query.list();

        } catch (Exception e) {
            logger.error("Erro na base de dados (RelatorioDAO)", e);
            return new ArrayList<>();
        }
    }

    public List<?> listarDadosDetalhados(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Limpa o cache da sessão para garantir que pegue dados novos do banco
            // (Citações e Acesso são atualizados em background por outras threads)
            session.clear();

            String termoBusca = resolverTermoBusca(tipoRelatorio);
            if (termoBusca == null) return new ArrayList<>();

            boolean filtrarInstituicao = nomeInstituicao != null && !"TODAS".equals(nomeInstituicao);
            boolean isFormacao = "DOUTORADO".equals(tipoRelatorio) || "MESTRADO".equals(tipoRelatorio);

            StringBuilder hql = new StringBuilder();

            if (isFormacao) {
                if (filtrarInstituicao) {
                    hql.append("SELECT f FROM Formacao f ")
                       .append("JOIN FETCH f.curriculo ")
                       .append("WHERE f.tipoFormacao = :termo ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = f.curriculo ")
                       .append("  AND UPPER(i.nomeInstituicao) = :nomeInst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= f.anoConclusao) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= f.anoConclusao)")
                       .append(") ")
                       .append("ORDER BY f.anoConclusao DESC");
                } else {
                    hql.append("SELECT f FROM Formacao f ")
                       .append("JOIN FETCH f.curriculo ")
                       .append("WHERE f.tipoFormacao = :termo ")
                       .append("ORDER BY f.anoConclusao DESC");
                }
            } else {
                if (filtrarInstituicao) {
                    /*
                     * OTIMIZAÇÃO: JOIN FETCH no curriculo evita query N+1 ao acessar
                     * p.getCurriculo().getNomeCompleto() no ExportCSV do ViewModel.
                     * O EXISTS mantém a busca de instituição eficiente e evita duplicates
                     * dispensando o uso de DISTINCT
                     */
                    hql.append("SELECT p FROM Producao p ")
                       .append("JOIN FETCH p.curriculo c ")
                       .append("WHERE p.tipo = :termo ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = c ")
                       .append("  AND UPPER(i.nomeInstituicao) = :nomeInst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= p.ano) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= p.ano)")
                       .append(") ")
                       .append("ORDER BY p.citacoes DESC NULLS LAST, p.ano DESC");
                } else {
                    hql.append("SELECT p FROM Producao p ")
                       .append("JOIN FETCH p.curriculo ")
                       .append("WHERE p.tipo = :termo ")
                       .append("ORDER BY p.citacoes DESC NULLS LAST, p.ano DESC");
                }
            }

            Query<?> query = session.createQuery(hql.toString());
            query.setCacheable(false); // Garante que não use cache de consulta
            query.setParameter("termo", termoBusca);
            if (filtrarInstituicao) {
                query.setParameter("nomeInst", nomeInstituicao.toUpperCase());
            }
            query.setMaxResults(5000); // Aumentado para 5000 para o CSV Detalhado

            return query.list();

        } catch (Exception e) {
            logger.error("Erro na base de dados (RelatorioDAO)", e);
            return new ArrayList<>();
        }
    }

    /**
     * OTIMIZAÇÃO DB2: Versão anterior executava 5 queries separadas (uma por KPI).
     * Esta versão executa apenas 3 queries consolidadas usando GROUP BY,
     * reduzindo o round-trip ao banco de 5 para 3 viagens de rede.
     *
     * Também substitui a função CURRENT_DATE (padrão SQL) no lugar de DATE()
     * para melhor compatibilidade com o DB2.
     */
    public Map<String, Long> obterTodosKPIs(String nomeInstituicao) {
        Map<String, Long> kpis = new HashMap<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean filtrar = nomeInstituicao != null && !"TODAS".equals(nomeInstituicao);

            // --- KPI 1: Pesquisadores ---
            String hqlPesq = filtrar
                    ? "SELECT COUNT(DISTINCT c.idLattes) FROM Atuacao a JOIN a.curriculo c JOIN a.instituicao i WHERE UPPER(i.nomeInstituicao) = :inst"
                    : "SELECT COUNT(c) FROM Curriculo c";
            Query<Long> qPesq = session.createQuery(hqlPesq, Long.class);
            if (filtrar) qPesq.setParameter("inst", nomeInstituicao.toUpperCase());
            Long pesq = qPesq.uniqueResult();
            kpis.put("PESQUISADORES", pesq != null ? pesq : 0L);

            // --- KPI 2: Produções agrupadas por tipo (ARTIGO, LIVRO, EVENTO) — 1 query ---
            StringBuilder hqlProd = new StringBuilder(
                    "SELECT p.tipo, COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
            if (filtrar) {
                /*
                 * OTIMIZAÇÃO: EXISTS em vez de JOIN triplo (Atuacao + Instituicao + Vinculo).
                 * O JOIN triplo com comparação de intervalos de datas gerava
                 * produto cartesiano no DB2. O EXISTS usa index lookup separado.
                 */
                hqlProd.append("WHERE p.tipo IN ('ARTIGO','LIVRO','EVENTO') ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = p.curriculo ")
                       .append("  AND UPPER(i.nomeInstituicao) = :inst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= p.ano) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= p.ano) ")
                       .append(") ");
            } else {
                hqlProd.append("WHERE p.tipo IN ('ARTIGO','LIVRO','EVENTO') ");
            }
            hqlProd.append("GROUP BY p.tipo");

            Query<Object[]> qProd = session.createQuery(hqlProd.toString(), Object[].class);
            if (filtrar) qProd.setParameter("inst", nomeInstituicao.toUpperCase());
            for (Object[] row : qProd.list()) {
                kpis.put((String) row[0], (Long) row[1]);
            }

            // --- KPI 3: Formações agrupadas (DOUTORADO, MESTRADO) — 1 query ---
            StringBuilder hqlForm = new StringBuilder(
                    "SELECT f.tipoFormacao, COUNT(DISTINCT f.id) FROM Formacao f ");
            if (filtrar) {
                hqlForm.append("WHERE f.tipoFormacao IN ('DOUTORADO','MESTRADO') ")
                       .append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = f.curriculo ")
                       .append("  AND UPPER(i.nomeInstituicao) = :inst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= f.anoConclusao) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= f.anoConclusao) ")
                       .append(") ");
            } else {
                hqlForm.append("WHERE f.tipoFormacao IN ('DOUTORADO','MESTRADO') ");
            }
            hqlForm.append("GROUP BY f.tipoFormacao");

            Query<Object[]> qForm = session.createQuery(hqlForm.toString(), Object[].class);
            if (filtrar) qForm.setParameter("inst", nomeInstituicao.toUpperCase());
            for (Object[] row : qForm.list()) {
                kpis.put((String) row[0], (Long) row[1]);
            }

        } catch (Exception e) {
            logger.error("Erro na base de dados (RelatorioDAO)", e);
        }
        return kpis;
    }

    /**
     * OTIMIZAÇÃO DB2: Top 5 pesquisadores.
     * Substitui o JOIN triplo com comparação de intervalo por EXISTS,
     * e adiciona FETCH FIRST 5 ROWS ONLY implícito via setMaxResults(5).
     * O DB2 traduz setMaxResults para FETCH FIRST N ROWS ONLY, que é
     * processado antes do sort — reduz significativamente o custo do ORDER BY.
     */
    public List<Object[]> obterTop5Pesquisadores(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean filtrar = nomeInstituicao != null && !"TODAS".equals(nomeInstituicao);
            boolean isFormacao = "DOUTORADO".equals(tipoRelatorio) || "MESTRADO".equals(tipoRelatorio);
            String termoBusca = tipoRelatorio;

            StringBuilder hql = new StringBuilder();

            if (isFormacao) {
                hql.append("SELECT c.nomeCompleto, COUNT(DISTINCT f.id) ")
                   .append("FROM Formacao f JOIN f.curriculo c ")
                   .append("WHERE f.tipoFormacao = :termo ");
                if (filtrar) {
                    hql.append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = c AND UPPER(i.nomeInstituicao) = :inst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= f.anoConclusao) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= f.anoConclusao)")
                       .append(") ");
                }
                hql.append("GROUP BY c.nomeCompleto ")
                   .append("ORDER BY COUNT(DISTINCT f.id) DESC");
            } else {
                hql.append("SELECT c.nomeCompleto, COUNT(DISTINCT p.hashTitulo) ")
                   .append("FROM Producao p JOIN p.curriculo c ")
                   .append("WHERE p.tipo = :termo ");
                if (filtrar) {
                    hql.append("AND EXISTS (")
                       .append("  SELECT 1 FROM Atuacao a JOIN a.vinculos v JOIN a.instituicao i ")
                       .append("  WHERE a.curriculo = c AND UPPER(i.nomeInstituicao) = :inst ")
                       .append("  AND (v.anoInicio IS NULL OR v.anoInicio <= p.ano) ")
                       .append("  AND (v.anoFim IS NULL OR v.anoFim >= p.ano)")
                       .append(") ");
                }
                hql.append("GROUP BY c.nomeCompleto ")
                   .append("ORDER BY COUNT(DISTINCT p.hashTitulo) DESC");
            }

            Query<Object[]> q = session.createQuery(hql.toString(), Object[].class);
            q.setParameter("termo", termoBusca);
            if (filtrar) q.setParameter("inst", nomeInstituicao.toUpperCase());
            q.setMaxResults(5);

            return q.list();

        } catch (Exception e) {
            logger.error("Erro na base de dados (RelatorioDAO)", e);
            return new ArrayList<>();
        }
    }

    public List<Object[]> gerarRelatorioPorAno(String tipo) {
        return gerarRelatorio(tipo, "TODAS");
    }

    // --- AUXILIARES ---

    private String resolverTermoBusca(String tipoRelatorio) {
        switch (tipoRelatorio) {
            case "ARTIGO":    return "ARTIGO";
            case "LIVRO":     return "LIVRO";
            case "EVENTO":    return "EVENTO";
            case "DOUTORADO": return "DOUTORADO";
            case "MESTRADO":  return "MESTRADO";
            default:          return null;
        }
    }
}
