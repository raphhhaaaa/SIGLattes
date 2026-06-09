package com.uem.extrator.dao;

import com.uem.extrator.dto.RelatorioRevistaDTO;
import com.uem.extrator.model.*;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.FiltroSimilaridade;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class CurriculoDAO {

    // instancia logger
    private static final Logger logger = LoggerFactory.getLogger(CurriculoDAO.class);

    private AuditLogService auditLogService = new AuditLogService();
    private CursoDAO cursoDAO = new CursoDAO();

    public void salvar(Curriculo curriculo) {
        synchronized (CurriculoDAO.class) {
            try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                try {
                    session.beginTransaction();

                    Curriculo curriculoExistente = session.createQuery(
                                    "FROM Curriculo c LEFT JOIN FETCH c.producoes WHERE c.idLattes = :idLattes",
                                    Curriculo.class)
                            .setParameter("idLattes", curriculo.getIdLattes())
                            .uniqueResult();

                    if (curriculoExistente != null) {
                        curriculo.setIdLattes(curriculoExistente.getIdLattes());
                        String idInterno = curriculoExistente.getIdLattes();

                        // Preserva dados bibliométricos se a API externa não os retornou
                        if (curriculo.getIndiceH() == null || curriculo.getIndiceH() == 0) {
                            curriculo.setIndiceH(curriculoExistente.getIndiceH());
                        }

                        // Mapa de produções antigas para reaproveitar métricas
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

                        /*
                         * ABORDAGEM DB2: Buscar IDs antes de apagar para evitar LOCK ESCALATION.
                         * Nunca usar DELETE com múltiplos SELECTs aninhados em bancos corporativos.
                         */
                        List<Long> idsAtuacoes = session.createQuery(
                                        "SELECT a.id FROM Atuacao a WHERE a.curriculo.idLattes = :id", Long.class)
                                .setParameter("id", idInterno).getResultList();

                        if (idsAtuacoes != null && !idsAtuacoes.isEmpty()) {

                            List<Long> idsAtividades = session.createQuery(
                                            "SELECT atv.id FROM Atividade atv WHERE atv.atuacao.id IN (:ids)", Long.class)
                                    .setParameterList("ids", idsAtuacoes).getResultList();

                            if (idsAtividades != null && !idsAtividades.isEmpty()) {
                                session.createQuery("DELETE FROM AtividadeItem ai WHERE ai.atividade.id IN (:ids)")
                                        .setParameterList("ids", idsAtividades).executeUpdate();
                            }

                            session.createQuery("DELETE FROM Atividade atv WHERE atv.atuacao.id IN (:ids)")
                                    .setParameterList("ids", idsAtuacoes).executeUpdate();

                            session.createQuery("DELETE FROM Vinculo v WHERE v.atuacao.id IN (:ids)")
                                    .setParameterList("ids", idsAtuacoes).executeUpdate();
                        }

                        session.createQuery("DELETE FROM Atuacao a WHERE a.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Formacao f WHERE f.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();

                        session.createQuery("DELETE FROM Producao p WHERE p.curriculo.idLattes = :id")
                                .setParameter("id", idInterno).executeUpdate();
                    }

                    // Resolução de cursos: busca em lote para evitar N+1
                    Map<String, Curso> cursosProcessadosNestaTransacao = new HashMap<>();
                    Map<String, Instituicao> instituicoesProcessadasNestaTransacao = new HashMap<>();
                    InstituicaoDAO instituicaoDAO = new InstituicaoDAO();
                    
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
                                        session.save(cursoCandidato);
                                        cursosProcessadosNestaTransacao.put(nomeNormalizado, cursoCandidato);
                                    }
                                }
                            }
                            
                            // Reaproveitamento de Instituicao na Formacao
                            Instituicao instCandidata = formacao.getNomeInstituicao();
                            if (instCandidata != null && instCandidata.getNomeInstituicao() != null) {
                                String nomeInstNormalizado = instCandidata.getNomeInstituicao().trim().toUpperCase();
                                if (instituicoesProcessadasNestaTransacao.containsKey(nomeInstNormalizado)) {
                                    formacao.setNomeInstituicao(instituicoesProcessadasNestaTransacao.get(nomeInstNormalizado));
                                } else {
                                    Instituicao instNoBanco = instituicaoDAO.buscarPorSimilaridade(session, instCandidata.getNomeInstituicao());
                                    if (instNoBanco != null) {
                                        formacao.setNomeInstituicao(instNoBanco);
                                        instituicoesProcessadasNestaTransacao.put(nomeInstNormalizado, instNoBanco);
                                    } else {
                                        session.save(instCandidata);
                                        instituicoesProcessadasNestaTransacao.put(nomeInstNormalizado, instCandidata);
                                    }
                                }
                            }
                        }
                    }

                    // Reaproveitamento de Instituicao na Atuacao
                    if (curriculo.getAtuacoes() != null) {
                        for (Atuacao atuacao : curriculo.getAtuacoes()) {
                            Instituicao instCandidata = atuacao.getInstituicao();
                            if (instCandidata != null && instCandidata.getNomeInstituicao() != null) {
                                String nomeInstNormalizado = instCandidata.getNomeInstituicao().trim().toUpperCase();
                                if (instituicoesProcessadasNestaTransacao.containsKey(nomeInstNormalizado)) {
                                    atuacao.setInstituicao(instituicoesProcessadasNestaTransacao.get(nomeInstNormalizado));
                                } else {
                                    Instituicao instNoBanco = instituicaoDAO.buscarPorSimilaridade(session, instCandidata.getNomeInstituicao());
                                    if (instNoBanco != null) {
                                        atuacao.setInstituicao(instNoBanco);
                                        instituicoesProcessadasNestaTransacao.put(nomeInstNormalizado, instNoBanco);
                                    } else {
                                        session.save(instCandidata);
                                        atuacao.setInstituicao(instCandidata);
                                        instituicoesProcessadasNestaTransacao.put(nomeInstNormalizado, instCandidata);
                                    }
                                }
                            }
                        }
                    }

                    curriculo = (Curriculo) session.merge(curriculo);
                    session.getTransaction().commit();

                    // Invalida o cache de veículos para que o próximo parse
                    // recarregue os nomes canônicos atualizados do banco
                    FiltroSimilaridade.invalidarCacheVeiculos();

                } catch (Exception e) {
                    if (session.getTransaction().isActive()) session.getTransaction().rollback();
                    logger.error("Erro na base de dados (CurriculoDAO)", e);
                    throw e;
                }
            }
        }
    }

    public long getConsultasHoje() {
        return auditLogService.contarProcessamentosHoje();
    }

    public List<Curriculo> listarTodos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Curriculo ORDER BY nomeCompleto", Curriculo.class).list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return new ArrayList<>();
        }
    }

    public Long contarTotalCurriculos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT COUNT(c) FROM Curriculo c", Long.class).uniqueResult();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return 0L;
        }
    }

    public Long contarPesquisadoresUem() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("SELECT COUNT(DISTINCT c.idLattes) FROM Atuacao a JOIN a.curriculo c JOIN a.instituicao i WHERE UPPER(i.nomeInstituicao) = 'UNIVERSIDADE ESTADUAL DE MARINGÁ'", Long.class).uniqueResult();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return 0L;
        }
    }

    /**
     * OTIMIZAÇÃO DB2: Busca leve para verificação de atualizações.
     * Seleciona apenas as 3 colunas necessárias em vez de carregar
     * o objeto Curriculo inteiro com todas as suas coleções lazy.
     * Isso reduz drasticamente o tráfego de rede em bases com muitos currículos.
     */
    public List<Object[]> listarResumoParaVerificacao() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT c.idLattes, c.dataAtualizacao, c.nomeCompleto FROM Curriculo c",
                    Object[].class).list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return new ArrayList<>();
        }
    }

    /**
     * OTIMIZAÇÃO DB2: Versão anterior usava múltiplos Hibernate.initialize() separados,
     * gerando até 5 queries adicionais por curriculo (N+1 problem):
     *   1 query para o curriculo + formacoes
     *   1 query para producoes
     *   1 query para atuacoes
     *   N queries para vinculos de cada atuacao
     *   N queries para atividades de cada atuacao
     * Solução: uma query principal com JOIN FETCH para tudo que é sempre necessário
     * (formacoes), seguida de queries de inicialização por coleção em lote.
     * O DB2 processa melhor múltiplas queries simples com IN do que Cartesian Products
     * gerados por múltiplos JOIN FETCH simultâneos.
     */
    public Curriculo buscarComDetalhes(String idLattes) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH para Formações, garantindo que vêm juntas com o currículo
            String hql = "FROM Curriculo c " +
                    "LEFT JOIN FETCH c.formacoes " +
                    "WHERE c.idLattes = :id";

            Curriculo c = session.createQuery(hql, Curriculo.class)
                    .setParameter("id", idLattes)
                    .uniqueResult();

            if (c != null) {
                // Inicialização forçada das coleções principais enquanto a sessão está aberta
                org.hibernate.Hibernate.initialize(c.getProducoes());
                org.hibernate.Hibernate.initialize(c.getAtuacoes());

                // Loop para inicializar as sub-coleções de cada atuação
                for (com.uem.extrator.model.Atuacao atuacao : c.getAtuacoes()) {
                    org.hibernate.Hibernate.initialize(atuacao.getVinculos());
                    org.hibernate.Hibernate.initialize(atuacao.getAtividades());
                }

                // Enriquecimento do Qualis
                enriquecerProducoesComQualis(c.getProducoes());
            }
            return c;
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return null;
        }
    }

    /**
     * OTIMIZAÇÃO DB2: Enriquece todas as produções com Qualis em uma única query
     * usando buscarPorIssns (que evita REPLACE() na coluna indexada).
     */
    private void enriquecerProducoesComQualis(List<Producao> producoes) {
        if (producoes == null || producoes.isEmpty()) return;

        Set<String> issns = producoes.stream()
                .filter(p -> "ARTIGO".equalsIgnoreCase(p.getTipo()))
                .map(Producao::getIsbnIssn)
                .filter(Objects::nonNull)
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toSet());

        if (issns.isEmpty()) return;

        QualisDAO qualisDAO = new QualisDAO();
        Map<String, Qualis> mapaQualis = qualisDAO.buscarPorIssns(issns);

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
            Long count = session.createQuery(
                    "SELECT COUNT(c) FROM Curriculo c WHERE c.idLattes = :id", Long.class)
                    .setParameter("id", idLattes)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return false;
        }
    }

    private String resolverCor(String nota) {
        if (nota == null) return "badge bg-secondary";
        switch (nota.toUpperCase().trim()) {
            case "A1": case "A2": case "A3": case "A4": return "badge bg-success";
            case "B1": case "B2": case "B3": case "B4": return "badge bg-warning";
            case "C": return "badge bg-danger";
            default: return "badge bg-secondary";
        }
    }

    // MÉTODOS DE PAGINAÇÃO //

    public List<Curriculo> listarPaginado(int offset, int limit, String busca) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean temBusca = (busca != null && !busca.trim().isEmpty());
            String hql = temBusca
                    ? "FROM Curriculo c WHERE lower(c.nomeCompleto) LIKE :busca OR c.idLattes LIKE :busca ORDER BY c.nomeCompleto"
                    : "FROM Curriculo c ORDER BY c.nomeCompleto";

            Query<Curriculo> query = session.createQuery(hql, Curriculo.class);
            if (temBusca) {
                query.setParameter("busca", "%" + busca.toLowerCase() + "%");
            }
            query.setFirstResult(offset); // a partir de qual registro ex: página 2 A PARTIR do registro 10)
            query.setMaxResults(limit); // quantos registros trazer (ex: 10)

            return query.list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return new ArrayList<>();
        }
    }

    public Long contarTotalPaginado(String busca) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            boolean temBusca = (busca != null && !busca.trim().isEmpty());
            String hql = temBusca
                    ? "SELECT COUNT(c) FROM Curriculo c WHERE lower(c.nomeCompleto) LIKE :busca OR c.idLattes LIKE :busca"
                    : "SELECT COUNT(c) FROM Curriculo c";

            Query<Long> query = session.createQuery(hql, Long.class);
            if (temBusca) {
                query.setParameter("busca", "%" + busca.toLowerCase() + "%");
            }
            return query.uniqueResult();
        } catch (Exception e) {
            logger.error("Erro na base de dados (CurriculoDAO)", e);
            return 0L;
        }
    }
}
