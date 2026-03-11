package com.uem.extrator.dao;

import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import java.util.ArrayList;
import java.util.List;

public class RelatorioDAO {

    public List<Object[]> gerarRelatorio(String tipoRelatorio, String nomeInstituicao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
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
                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    hql.append("SELECT p.anoConclusao, COUNT(p) FROM Formacao p ");
                    hql.append("WHERE p.tipoFormacao = :termo ");
                    hql.append("AND EXISTS ( ");
                    hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("  WHERE a.curriculo = p.curriculo AND i.nomeInstituicao = :nomeInst ");
                    hql.append("  AND p.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                    hql.append(") ");
                    hql.append("GROUP BY p.anoConclusao ORDER BY p.anoConclusao DESC");
                } else {
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
                    hql.append("WHERE p.tipo = :termo ");
                    hql.append("AND EXISTS ( ");
                    hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("  WHERE a.curriculo = p.curriculo AND i.nomeInstituicao = :nomeInst ");
                    hql.append("  AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                    hql.append(") ");
                    hql.append("GROUP BY p.ano ORDER BY p.ano DESC");
                }
            } else {
                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    hql.append("SELECT f.anoConclusao, COUNT(f) FROM Formacao f WHERE f.tipoFormacao = :termo GROUP BY f.anoConclusao ORDER BY f.anoConclusao DESC");
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
        } finally {
            if (session != null) session.close();
        }
    }

    public List<?> listarDadosDetalhados(String tipoRelatorio, String nomeInstituicao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
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

            if (isFormacao) {
                hql.append("SELECT p FROM Formacao p WHERE p.tipoFormacao = :termo ");
            } else {
                hql.append("SELECT p FROM Producao p WHERE p.tipo = :termo ");
            }

            if (filtrarInstituicao) {
                hql.append("AND EXISTS ( ");
                hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i JOIN a.vinculos v ");
                hql.append("  WHERE a.curriculo = p.curriculo AND i.nomeInstituicao = :nomeInst ");
                if (isFormacao) {
                    hql.append("  AND p.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                } else {
                    hql.append("  AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                }
                hql.append(") ");
            }

            if (isFormacao) {
                hql.append("ORDER BY p.anoConclusao DESC");
            } else {
                hql.append("ORDER BY p.citacoes DESC, p.ano DESC");
            }

            Query query = session.createQuery(hql.toString());
            query.setParameter("termo", termoBusca);
            if (filtrarInstituicao) query.setParameter("nomeInst", nomeInstituicao.toUpperCase());

            query.setMaxResults(100);

            return query.list();

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null) session.close();
        }
    }

    // Mantém compatibilidade se necessário, mas redireciona
    public List<Object[]> gerarRelatorioPorAno(String tipo) {
        return gerarRelatorio(tipo, "TODAS");
    }

    public long contarTotalPesquisadores(String nomeInstituicao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            if (nomeInstituicao == null || nomeInstituicao.equals("TODAS")) {
                return ((Number) session.createNativeQuery("SELECT COUNT(*) FROM CURRICULO").getSingleResult()).longValue();
            }

            StringBuilder hql = new StringBuilder("SELECT COUNT(c.idLattes) FROM Curriculo c ");
            hql.append("WHERE EXISTS ( ");
            hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i ");
            hql.append("  WHERE a.curriculo = c AND i.nomeInstituicao = :nomeInst ");
            hql.append(") ");

            Query<Long> query = session.createQuery(hql.toString(), Long.class);
            query.setParameter("nomeInst", nomeInstituicao.toUpperCase());

            Long total = query.uniqueResult();
            return total != null ? total : 0L;

        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        } finally {
            if (session != null) session.close();
        }
    }

    public long contarTotalProducao(String tipoRelatorio, String nomeInstituicao) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
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

            // Se for Mestrado ou Doutorado, tem de pesquisar na tabela Formacao
            if (isFormacao) {
                hql.append("SELECT COUNT(f.id) FROM Formacao f WHERE f.tipoFormacao = :termo ");
                if (filtrarInstituicao) {
                    hql.append("AND EXISTS ( ");
                    hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("  WHERE a.curriculo = f.curriculo AND i.nomeInstituicao = :nomeInst ");
                    hql.append("  AND f.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR f.anoConclusao <= v.anoFim) ");
                    hql.append(") ");
                }
            } else {
                // Se for Artigo, Livro ou Evento, pesquisa na tabela Producao
                hql.append("SELECT COUNT(DISTINCT p.hashTitulo) FROM Producao p WHERE p.tipo = :termo ");
                if (filtrarInstituicao) {
                    hql.append("AND EXISTS ( ");
                    hql.append("  SELECT 1 FROM Atuacao a JOIN a.instituicao i JOIN a.vinculos v ");
                    hql.append("  WHERE a.curriculo = p.curriculo AND i.nomeInstituicao = :nomeInst ");
                    hql.append("  AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                    hql.append(") ");
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
        } finally {
            if (session != null) session.close();
        }
    }
}
