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

            // Define o termo de busca base
            switch (tipoRelatorio) {
                case "ARTIGO": termoBusca = "ARTIGO"; break;
                case "LIVRO": termoBusca = "LIVRO"; break;
                case "EVENTO": termoBusca = "EVENTO"; break;
                case "DOUTORADO": termoBusca = "DOUTORADO"; break;
                case "MESTRADO": termoBusca = "MESTRADO"; break;
                default: return new ArrayList<>();
            }

            // --- CONSTRUÇÃO DA QUERY DINÂMICA ---
            if (filtrarInstituicao) {
                // QUERY COMPLEXA (Com Filtro de Instituição e Vínculo Temporal)

                // O SELECT precisa ser específico para cada tipo de entidade
                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    // Para Formacao: usa anoConclusao e conta os registros (p)
                    hql.append("SELECT p.anoConclusao, COUNT(p) ");
                    hql.append("FROM Formacao p ");
                    hql.append("JOIN p.curriculo c ");
                } else {
                    // Para Producao: usa ano e conta titulos distintos
                    hql.append("SELECT p.ano, COUNT(DISTINCT p.hashTitulo) ");
                    hql.append("FROM Producao p ");
                }

                hql.append("JOIN p.curriculo c ")
                        .append("JOIN c.atuacoes a ")
                        .append("JOIN a.instituicao i ")
                        .append("JOIN a.vinculos v ")
                        .append("WHERE upper(i.nomeInstituicao) = :nomeInst ");

                if (tipoRelatorio.equals("DOUTORADO") || tipoRelatorio.equals("MESTRADO")) {
                    hql.append("AND p.tipoFormacao = :termo ");
                    // Para formação, usamos o ano de conclusão
                    hql.append("AND p.anoConclusao >= v.anoInicio ")
                            .append("AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                    hql.append("GROUP BY p.anoConclusao ORDER BY p.anoConclusao DESC");
                } else {
                    hql.append("AND p.tipo = :termo ");
                    // Para produção, usamos o ano da produção
                    hql.append("AND p.ano >= v.anoInicio ")
                            .append("AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                    hql.append("GROUP BY p.ano ORDER BY p.ano DESC");
                }

            } else {
                // QUERY SIMPLES (Todas as Instituições / Sem Filtro)
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

            // 2. Monta o SELECT (Agora buscamos o Objeto 'p', não o COUNT)
            if (isFormacao) {
                hql.append("SELECT DISTINCT p FROM Formacao p JOIN p.curriculo c ");
            } else {
                hql.append("SELECT DISTINCT p FROM Producao p JOIN p.curriculo c ");
            }

            // 3. Adiciona Joins se tiver filtro de Instituição
            if (filtrarInstituicao) {
                hql.append("JOIN c.atuacoes a JOIN a.instituicao i JOIN a.vinculos v WHERE upper(i.nomeInstituicao) = :nomeInst ");
            } else {
                hql.append("WHERE 1=1 "); // Truque para facilitar o append de ANDs depois
            }

            // 4. Filtros de Tipo
            if (isFormacao) {
                hql.append("AND p.tipoFormacao = :termo ");
            } else {
                hql.append("AND p.tipo = :termo ");
            }

            // 5. Filtro de Data (Vínculo) - Apenas se estiver filtrando por instituição
            if (filtrarInstituicao) {
                if (isFormacao) {
                    hql.append("AND p.anoConclusao >= v.anoInicio AND (v.anoFim IS NULL OR p.anoConclusao <= v.anoFim) ");
                } else {
                    hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
                }
            }

            // 6. Ordenação Especial: Mais citados primeiro!
            if (isFormacao) {
                hql.append("ORDER BY p.anoConclusao DESC");
            } else {
                // Aqui está o pulo do gato: Ordena por Citações (maior para menor), depois por Ano
                hql.append("ORDER BY p.citacoes DESC, p.ano DESC");
            }

            Query query = session.createQuery(hql.toString());

            if (!termoBusca.equals("IGNORE")) query.setParameter("termo", termoBusca);
            if (filtrarInstituicao) query.setParameter("nomeInst", nomeInstituicao.toUpperCase());

            query.setMaxResults(100); //  limite para nao travar excel

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

            boolean filtrarInstituicao = nomeInstituicao != null && !nomeInstituicao.equals("TODAS");

            if (!filtrarInstituicao) {
                // Se não tem filtro, peça ao banco apenas o número total de linhas.
                // É 1000x mais rápido que fazer DISTINCT.
                return (Long) session.createQuery("SELECT COUNT(c.id) FROM Curriculo c").uniqueResult();
            }

            // Se tiver filtro, mantenha a lógica que você já tem, mas certifique-se que usa o COUNT(c.id)
            StringBuilder hql = new StringBuilder("SELECT COUNT(DISTINCT c.id) FROM Curriculo c ");
            hql.append("JOIN c.atuacoes a JOIN a.instituicao i JOIN a.vinculos v ");
            hql.append("WHERE upper(i.nomeInstituicao) = :nomeInst");

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

            StringBuilder hql = new StringBuilder("SELECT COUNT(DISTINCT p.hashTitulo) FROM Producao p ");
            String termoBusca = "";

            switch (tipoRelatorio) {
                case "ARTIGO": termoBusca = "ARTIGO"; break;
                case "LIVRO": termoBusca = "LIVRO"; break;
                case "EVENTO": termoBusca = "EVENTO"; break;
                default: return 0L;
            }

            if (filtrarInstituicao) {
                hql.append("JOIN p.curriculo c JOIN c.atuacoes a JOIN a.instituicao i JOIN a.vinculos v WHERE upper(i.nomeInstituicao) = :nomeInst ");
                hql.append("AND p.tipo = :termo ");
                hql.append("AND p.ano >= v.anoInicio AND (v.anoFim IS NULL OR p.ano <= v.anoFim) ");
            } else {
                hql.append("WHERE p.tipo = :termo ");
            }

            Query<Long> query = session.createQuery(hql.toString(), Long.class);

            // Injeta o parâmetro de forma obrigatória e livre de IFs para todos os tipos!
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
