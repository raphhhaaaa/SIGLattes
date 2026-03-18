package com.uem.extrator.dao;

import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Curso;
import com.uem.extrator.model.Formacao;
import com.uem.extrator.model.Producao;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurriculoDAO {

    private AuditLogService auditLogService = new AuditLogService();
    private CursoDAO cursoDAO = new CursoDAO();

    public void salvar(Curriculo curriculo) {
        synchronized (CurriculoDAO.class) {
        Session session = HibernateUtil.getSessionFactory().openSession();
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
                    // Se a API externa falhar, garantimos que os dados do banco NÃO são zerados!
                    // ===============================================================

                    // 1. Resgata o Índice H antigo se a API retornou Nulo/Zero
                    if (curriculo.getIndiceH() == null || curriculo.getIndiceH() == 0) {
                        curriculo.setIndiceH(curriculoExistente.getIndiceH());
                    }

                    // 2. Mapeia as Produções antigas para resgatar as Citações e Status de Acesso
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
                    // ===============================================================

                    session.evict(curriculoExistente);

                    // Continua com a deleção segura e normal
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
            } finally {
            if (session != null) session.close();
            }
        }
    }

    public long getConsultasHoje() { return auditLogService.contarProcessamentosHoje(); }

    public List<Curriculo> listarTodos() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            return session.createQuery("from Curriculo order by nomeCompleto", Curriculo.class).list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            if (session != null) session.close();
        }
    }

    public Long contarTotalCurriculos() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            return session.createQuery("SELECT COUNT(c) FROM Curriculo c", Long.class).uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        } finally {
            if (session != null) session.close();
        }
    }

    public List<Object[]> listarResumoParaVerificacao() {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            String hql = "SELECT c.idLattes, c.dataAtualizacao, c.nomeCompleto FROM Curriculo c";
            Query<Object[]> query = session.createQuery(hql, Object[].class);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            session.close();
        }
    }

    public Curriculo buscarComDetalhes(String idLattes) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
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
            }
            return c;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            session.close();
        }
    }

    public boolean existe(String idLattes) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            Long count = session.createQuery("SELECT COUNT(c) FROM Curriculo c WHERE c.idLattes = :id", Long.class)
                    .setParameter("id", idLattes)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            session.close();
        }
    }
}