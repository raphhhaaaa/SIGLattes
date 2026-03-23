package com.uem.extrator.dao;

import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelatorioDAO {

    public List<Object[]> gerarRelatorio(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            String termoBusca = "";
            boolean filtrarInstituicao = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");

            switch (tipoRelatorio) {
                case "ARTIGO": termoBusca = "ARTIGO"; break;
                case "LIVRO": termoBusca = "LIVRO"; break;
                case "EVENTO": termoBusca = "EVENTO"; break;
                case "DOUTORADO": termoBusca = "DOUTORADO"; break;
                case "MESTRADO": termoBusca = "MESTRADO"; break;
                default: return new ArrayList<>();
            }

            if (filtrarInstituicao) {
                // FIM DO PRODUTO CARTESIANO: Usando JOIN ON para forçar a ligação exata pelos índices
                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    hql.append("SELECT p.anoConclusao, COUNT(DISTINCT p.id) FROM Formacao p ");
                    hql.append("JOIN Atuacao a ON a.curriculo = p.curriculo ");
                    hql.append("JOIN a.instituicao i ");
                    hql.append("JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND p.tipoFormacao = :termo ");
                    hql.append("AND p.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                    hql.append("GROUP BY p.anoConclusao ORDER BY p.anoConclusao DESC");
                } else {
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
                    hql.append("JOIN Atuacao a ON a.curriculo = p.curriculo ");
                    hql.append("JOIN a.instituicao i ");
                    hql.append("JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND p.tipo = :termo ");
                    hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                    hql.append("GROUP BY p.ano ORDER BY p.ano DESC");
                }
            } else {
                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    hql.append("SELECT f.anoConclusao, COUNT(f.id) FROM Formacao f WHERE f.tipoFormacao = :termo GROUP BY f.anoConclusao ORDER BY f.anoConclusao DESC");
                } else {
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
                    hql.append("WHERE p.tipo = :termo ");
                    hql.append("GROUP BY p.ano ORDER BY p.ano DESC");
                }
            }

            Query<Object[]> query = session.createQuery(hql.toString(), Object[].class);
            query.setParameter("termo", termoBusca);

            if (filtrarInstituicao) {
                query.setParameter("nomeInst", nomeInstituicao.toUpperCase());
            }

            return query.list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<?> listarDadosDetalhados(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            StringBuilder hql = new StringBuilder();
            String termoBusca = "";
            boolean filtrarInstituicao = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");
            boolean isFormacao = tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO");

            switch (tipoRelatorio) {
                case "ARTIGO": termoBusca = "ARTIGO"; break;
                case "LIVRO": termoBusca = "LIVRO"; break;
                case "EVENTO": termoBusca = "EVENTO"; break;
                case "DOUTORADO": termoBusca = "DOUTORADO"; break;
                case "MESTRADO" : termoBusca = "MESTRADO"; break;
                default: return new ArrayList<>();
            }

            if (filtrarInstituicao) {
                if (isFormacao) {
                    hql.append("SELECT DISTINCT p FROM Formacao p ");
                    hql.append("JOIN Atuacao a ON a.curriculo = p.curriculo ");
                    hql.append("JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND p.tipoFormacao = :termo ");
                    hql.append("AND p.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                    hql.append("ORDER BY p.anoConclusao DESC");
                } else {
                    hql.append("SELECT DISTINCT p FROM Producao p ");
                    hql.append("JOIN Atuacao a ON a.curriculo = p.curriculo ");
                    hql.append("JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND p.tipo = :termo ");
                    hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                    hql.append("ORDER BY p.citacoes DESC, p.ano DESC");
                }
            } else {
                if (isFormacao) {
                    hql.append("SELECT p FROM Formacao p WHERE p.tipoFormacao = :termo ORDER BY p.anoConclusao DESC");
                } else {
                    hql.append("SELECT p FROM Producao p WHERE p.tipo = :termo ORDER BY p.citacoes DESC, p.ano DESC");
                }
            }

            Query query = session.createQuery(hql.toString());
            query.setParameter("termo", termoBusca);
            if (filtrarInstituicao) query.setParameter("nomeInst", nomeInstituicao.toUpperCase());

            query.setMaxResults(100);

            return query.list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Object[]> gerarRelatorioPorAno(String tipo) {
        return gerarRelatorio(tipo, "TODAS");
    }

    public long contarTotalPesquisadores(String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            if (nomeInstituicao == null || nomeInstituicao.equals("TODAS")) {
                return ((Number) session.createNativeQuery("SELECT COUNT(*) FROM CURRICULO").getSingleResult()).longValue();
            }

            StringBuilder hql = new StringBuilder("SELECT COUNT(DISTINCT c.idLattes) FROM Atuacao a JOIN a.curriculo c JOIN a.instituicao i ");
            hql.append("WHERE i.nomeInstituicao = :nomeInst ");

            Query<Long> query = session.createQuery(hql.toString(), Long.class);
            query.setParameter("nomeInst", nomeInstituicao.toUpperCase());

            Long total = query.uniqueResult();
            return total != null ? total : 0L;

        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public long contarTotalProducao(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean filtrarInstituicao = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");
            String termoBusca = "";
            boolean isFormacao = false;

            switch (tipoRelatorio) {
                case "ARTIGO": termoBusca = "ARTIGO"; break;
                case "LIVRO": termoBusca = "LIVRO"; break;
                case "EVENTO": termoBusca = "EVENTO"; break;
                case "DOUTORADO": termoBusca = "DOUTORADO"; isFormacao = true; break;
                case "MESTRADO": termoBusca = "MESTRADO"; isFormacao = true; break;
                default: return 0L;
            }

            StringBuilder hql = new StringBuilder();

            if (filtrarInstituicao) {
                if (isFormacao) {
                    hql.append("SELECT COUNT(DISTINCT f.id) FROM Formacao f ");
                    hql.append("JOIN Atuacao a ON a.curriculo = f.curriculo ");
                    hql.append("JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND f.tipoFormacao = :termo ");
                    hql.append("AND f.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR f.anoConclusao <= v.anoFim)");
                } else {
                    hql.append("SELECT COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
                    hql.append("JOIN Atuacao a ON a.curriculo = p.curriculo ");
                    hql.append("JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :nomeInst AND p.tipo = :termo ");
                    hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim)");
                }
            } else {
                if (isFormacao) {
                    hql.append("SELECT COUNT(f.id) FROM Formacao f WHERE f.tipoFormacao = :termo");
                } else {
                    hql.append("SELECT COUNT(DISTINCT p.hashTitulo) FROM Producao p WHERE p.tipo = :termo");
                }
            }

            Query<Long> query = session.createQuery(hql.toString(), Long.class);
            query.setParameter("termo", termoBusca);

            if (filtrarInstituicao) {
                query.setParameter("nomeInst", nomeInstituicao.toUpperCase());
            }

            Long total = query.uniqueResult();
            return total != null ? total : 0L;

        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public Map<String, Long> obterTodosKPIs(String nomeInstituicao) {
        Map<String, Long> kpis = new HashMap<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean filtrar = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");

            // 1. Pesquisadores
            String hqlPesq = filtrar ?
                    "SELECT COUNT(DISTINCT c.idLattes) FROM Atuacao a JOIN a.curriculo c JOIN a.instituicao i WHERE i.nomeInstituicao = :inst" :
                    "SELECT COUNT(c) FROM Curriculo c";
            Query<Long> qPesq = session.createQuery(hqlPesq, Long.class);
            if (filtrar) qPesq.setParameter("inst", nomeInstituicao.toUpperCase());
            kpis.put("PESQUISADORES", qPesq.uniqueResult() != null ? qPesq.uniqueResult() : 0L);

            // 2. Produção (Junta Artigo, Livro e Evento de UMA VEZ)
            StringBuilder hqlProd = new StringBuilder("SELECT p.tipo, COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
            if (filtrar) {
                hqlProd.append("JOIN Atuacao a ON a.curriculo = p.curriculo JOIN a.instituicao i JOIN a.vinculos v ");
                hqlProd.append("WHERE i.nomeInstituicao = :inst ");
                hqlProd.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
            }
            hqlProd.append("GROUP BY p.tipo");

            Query<Object[]> qProd = session.createQuery(hqlProd.toString(), Object[].class);
            if (filtrar) qProd.setParameter("inst", nomeInstituicao.toUpperCase());
            for (Object[] row : qProd.list()) { kpis.put((String) row[0], (Long) row[1]); }

            // 3. Formação (Junta Mestrado e Doutorado de UMA VEZ)
            StringBuilder hqlForm = new StringBuilder("SELECT f.tipoFormacao, COUNT(DISTINCT f.id) FROM Formacao f ");
            if (filtrar) {
                hqlForm.append("JOIN Atuacao a ON a.curriculo = f.curriculo JOIN a.instituicao i JOIN a.vinculos v ");
                hqlForm.append("WHERE i.nomeInstituicao = :inst ");
                hqlForm.append("AND f.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR f.anoConclusao <= v.anoFim) ");
            }
            hqlForm.append("GROUP BY f.tipoFormacao");

            Query<Object[]> qForm = session.createQuery(hqlForm.toString(), Object[].class);
            if (filtrar) qForm.setParameter("inst", nomeInstituicao.toUpperCase());
            for (Object[] row : qForm.list()) { kpis.put((String) row[0], (Long) row[1]); }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return kpis;
    }

    public List<Object[]> obterTop5Pesquisadores(String tipoRelatorio, String nomeInstituicao) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean filtrar = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");
            boolean isFormacao = tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO");
            String termoBusca = tipoRelatorio; // ARTIGO, LIVRO, etc.

            StringBuilder hql = new StringBuilder();

            if (isFormacao) {
                hql.append("SELECT c.nomeCompleto, COUNT(DISTINCT f.id) FROM Formacao f JOIN f.curriculo c ");
                if (filtrar) {
                    hql.append("JOIN Atuacao a ON a.curriculo = c JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :inst AND f.tipoFormacao = :termo ");
                    hql.append("AND f.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR f.anoConclusao <= v.anoFim) ");
                } else {
                    hql.append("WHERE f.tipoFormacao = :termo ");
                }
                hql.append("GROUP BY c.nomeCompleto ORDER BY COUNT(DISTINCT f.id) DESC");
            } else {
                hql.append("SELECT c.nomeCompleto, COUNT(DISTINCT p.hashTitulo) FROM Producao p JOIN p.curriculo c ");
                if (filtrar) {
                    hql.append("JOIN Atuacao a ON a.curriculo = c JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("WHERE i.nomeInstituicao = :inst AND p.tipo = :termo ");
                    hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                } else {
                    hql.append("WHERE p.tipo = :termo ");
                }
                hql.append("GROUP BY c.nomeCompleto ORDER BY COUNT(DISTINCT p.hashTitulo) DESC");
            }

            Query<Object[]> q = session.createQuery(hql.toString(), Object[].class);
            q.setParameter("termo", termoBusca);
            if (filtrar) q.setParameter("inst", nomeInstituicao.toUpperCase());

            q.setMaxResults(5); // Traz APENAS os 5 primeiros direto do banco
            return q.list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}