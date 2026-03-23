package com.uem.extrator.dao;

import com.uem.extrator.model.*;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.*;
import java.util.stream.Collectors;

public class CurriculoDAO {

    private AuditLogService auditLogService = new AuditLogService();
    private CursoDAO cursoDAO = new CursoDAO();

    public void salvar(Curriculo curriculo) {

        synchronized (CurriculoDAO.class) {


            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                try {
                    session.beginTransaction();

                    Curriculo curriculoExistente = session.createQuery(
                                    "FROM Curriculo c LEFT JOIN FETCH c.producoes WHERE c.idLattes = :idLattes ", Curriculo.class)
                            .setParameter("idLattes", curriculo.getIdLattes())
                            .uniqueResult();

                    if (curriculoExistente != null) {
                        curriculo.setIdLattes(curriculoExistente.getIdLattes());
                        String idInterno = curriculoExistente.getIdLattes();

                        // ===============================================================
                        // REDE DE SEGURANÇA (FALLBACK BIBLIOMÉTRICO)
                        // Se a API externa falhar, garante que os dados do banco NÃO são zerados!
                        // ===============================================================

                        if (curriculo.getIndiceH() == null || curriculo.getIndiceH() == 0) {
                            curriculo.setIndiceH(curriculoExistente.getIndiceH());
                        }

                        Map<String, Producao> mapProducoesAntigas = new HashMap<>();
                        if (curriculoExistente.getProducoes() != null) {
                            for (Producao pAntiga : curriculoExistente.getProducoes()) {
                                if (pAntiga.getDoi() != null && !pAntiga.getDoi().trim().isEmpty()) {
                                    mapProducoesAntigas.put(pAntiga.getDoi().trim(), pAntiga);
                                } else if (pAntiga.getTitulo() != null) {
                                    mapProducoesAntigas.put(pAntiga.getTitulo().trim().toLowerCase(), pAntiga);
                                }
                            }
                        }

                        // Transfere os dados das produções antigas para as novas se necessário
                        if (curriculo.getProducoes() != null) {
                            for (Producao pNova : curriculo.getProducoes()) {
                                if (pNova.getCitacoes() == null || pNova.getCitacoes() == 0) {
                                    Producao pAntiga = null;
                                    if (pNova.getDoi() != null && !pNova.getDoi().trim().isEmpty()) {
                                        pAntiga = mapProducoesAntigas.get(pNova.getDoi().trim());
                                    } else if (pNova.getTitulo() != null) {
                                        pAntiga = mapProducoesAntigas.get(pNova.getTitulo().trim().toLowerCase());
                                    }

                                    if (pAntiga != null && pAntiga.getCitacoes() != null && pAntiga.getCitacoes() > 0) {
                                        pNova.setCitacoes(pAntiga.getCitacoes());
                                        pNova.setStatusAcesso(pAntiga.getStatusAcesso());
                                    }
                                }
                            }
                        }

                        session.evict(curriculoExistente);

                        session.createQuery("DELETE FROM Vinculo v WHERE v.atuacao.id IN (SELECT a.id FROM Atuacao a WHERE a.curriculo.idLattes = :id)")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM AtividadeItem ai WHERE ai.atividade.id IN (SELECT atv.id FROM Atividade atv WHERE atv.atuacao.id IN (SELECT a.id FROM Atuacao a WHERE a.curriculo.idLattes = :id))")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Atividade atv WHERE atv.atuacao.id IN (SELECT a.id FROM Atuacao a WHERE a.curriculo.idLattes = :id)")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Atuacao a WHERE a.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Formacao f WHERE f.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Producao p WHERE p.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();
                    }

                    Map<String, Curso> cursosProcessadosNestaTransacao = new HashMap<>();

                    if (curriculo.getFormacoes() != null) {
                        for (Formacao formacao : curriculo.getFormacoes()) {
                            Curso cursoCandidato = formacao.getNomeCurso();

                            if (cursoCandidato != null && cursoCandidato.getNomeCurso() != null) {
                                String nomeNormalizado = cursoCandidato.getNomeCurso().trim().toUpperCase();

                                if (cursosProcessadosNestaTransacao.containsKey(nomeNormalizado)) {
                                    formacao.setNomeCurso(cursosProcessadosNestaTransacao.get(nomeNormalizado));
                                } else {
                                    Curso cursoNoBanco = cursoDAO.buscarPorNome(session, cursoCandidato.getNomeCurso());
                                    if (cursoNoBanco != null) {
                                        formacao.setNomeCurso(cursoNoBanco);
                                        cursosProcessadosNestaTransacao.put(nomeNormalizado, cursoNoBanco);
                                    } else {
                                        cursosProcessadosNestaTransacao.put(nomeNormalizado, cursoCandidato);
                                    }
                                }
                            }
                        }
                    }

                    session.saveOrUpdate(curriculo);
                    session.getTransaction().commit();

                } catch (Exception e) {
                    if (session.getTransaction().isActive()) {
                        session.getTransaction().rollback();
                    }
                    e.printStackTrace();
                    throw e;
                }
            }
        }
    }
    public long getConsultasHoje() { return auditLogService.contarProcessamentosHoje(); }

    public List<Curriculo> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("from Curriculo order by nomeCompleto", Curriculo.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Long contarTotalCurriculos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("SELECT COUNT(c) FROM Curriculo c", Long.class).uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public List<Object[]> listarResumoParaVerificacao() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT c.idLattes, c.dataAtualizacao, c.nomeCompleto FROM Curriculo c";
            Query<Object[]> query = session.createQuery(hql, Object[].class);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Curriculo buscarComDetalhes(String idLattes) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Curriculo c " +
                    "LEFT JOIN FETCH c.formacoes " +
                    "WHERE c.idLattes = :id";
            Curriculo c = session.createQuery(hql, Curriculo.class)
                    .setParameter("id", idLattes)
                    .uniqueResult();

            if (c != null) {
                org.hibernate.Hibernate.initialize(c.getProducoes());
                org.hibernate.Hibernate.initialize(c.getAtuacoes());

                for (com.uem.extrator.model.Atuacao atuacao : c.getAtuacoes()) {
                    org.hibernate.Hibernate.initialize(atuacao.getVinculos());
                    org.hibernate.Hibernate.initialize(atuacao.getAtividades());
                }
                enriquecerProducoesComQualis(c.getProducoes());
            }
            return c;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void enriquecerProducoesComQualis(List<Producao> producoes) {
        if (producoes == null || producoes.isEmpty()) return;

        // Coleta apenas ISSNs de artigos
        Set<String> issns = producoes.stream()
                .filter(p -> "ARTIGO".equalsIgnoreCase(p.getTipo()))
                .map(Producao::getIsbnIssn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (issns.isEmpty()) return;

        // Uma única query para todos os ISSNs
        QualisDAO qualisDAO = new QualisDAO();
        Map<String, Qualis> mapaQualis = qualisDAO.buscarPorIssns(issns);

        // Popula o cache em memória de cada produção
        for (Producao p : producoes) {
            if (!"ARTIGO".equalsIgnoreCase(p.getTipo()) || p.getIsbnIssn() == null) continue;

            String issnNorm = p.getIsbnIssn().replace("-", "");
            Qualis q = mapaQualis.get(issnNorm);
            String estrato = (q != null && q.getEstrato() != null) ? q.getEstrato() : "S/N";

            p.setQualisDescricaoCache(estrato);
            p.setQualisCorCache(resolverCor(estrato));
        }
    }

    public boolean existe(String idLattes) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery("SELECT COUNT(c) FROM Curriculo c WHERE c.idLattes = :id", Long.class)
                    .setParameter("id", idLattes)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String resolverCor(String nota) {
        if (nota == null) return "badge bg-secondary";
        switch (nota.toUpperCase().trim()) {
            case "A1": case "A2": return "badge bg-success";
            case "B1": case "B2": return "badge bg-primary";
            case "B3": case "B4": return "badge bg-info text-dark";
            case "C":              return "badge bg-warning text-dark";
            default:               return "badge bg-secondary";
        }
    }
}