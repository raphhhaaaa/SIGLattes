package com.uem.extrator.dao;

import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Curso;
import com.uem.extrator.model.Formacao;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CurriculoDAO {

    private AuditLogService auditLogService = new AuditLogService();
    private CursoDAO cursoDAO = new CursoDAO(); // Instância do novo DAO

    public void salvar(Curriculo curriculo) {
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            session.beginTransaction();

            // CACHE LOCAL: Evita duplicar cursos iguais dentro do MESMO currículo
            // Ex: Se o cara tem Graduação em 'História' e Mestrado em 'História',
            // garante que usamos a mesma instância de objeto Curso.
            Map<String, Curso> cursosProcessadosNestaTransacao = new HashMap<>();

            if (curriculo.getFormacoes() != null) {
                for (Formacao formacao : curriculo.getFormacoes()) {
                    Curso cursoCandidato = formacao.getNomeCurso();

                    if (cursoCandidato != null && cursoCandidato.getNomeCurso() != null) {
                        String nomeNormalizado = cursoCandidato.getNomeCurso().trim().toUpperCase();

                        // 1. Verifica se já processamos esse curso NESTE currículo (Cache Local)
                        if (cursosProcessadosNestaTransacao.containsKey(nomeNormalizado)) {
                            // Se sim, reaproveita a instância que já decidimos usar
                            formacao.setNomeCurso(cursosProcessadosNestaTransacao.get(nomeNormalizado));
                        } else {
                            // 2. Se não, busca no Banco de Dados (Cache Persistente)
                            Curso cursoNoBanco = cursoDAO.buscarPorNome(session, cursoCandidato.getNomeCurso());

                            if (cursoNoBanco != null) {
                                // Achou no banco: usa ele e guarda no cache local
                                formacao.setNomeCurso(cursoNoBanco);
                                cursosProcessadosNestaTransacao.put(nomeNormalizado, cursoNoBanco);
                            } else {
                                // Não achou nem no banco nem no cache local:
                                // Vai ser um curso NOVO. Guardamos essa nova instância no cache
                                // para que se aparecer de novo no loop, usemos ELA mesma.
                                // Nota: O Hibernate vai salvar via CascadeType.ALL
                                cursosProcessadosNestaTransacao.put(nomeNormalizado, cursoCandidato);
                            }
                        }
                    }
                }
            }

            session.saveOrUpdate(curriculo);
            session.getTransaction().commit();
            auditLogService.registrarProcessamento(curriculo.getIdLattes());

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

    public long getConsultasHoje() {
        return auditLogService.contarProcessamentosHoje();
    }

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
}