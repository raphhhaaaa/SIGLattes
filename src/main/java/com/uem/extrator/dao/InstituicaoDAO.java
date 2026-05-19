package com.uem.extrator.dao;

import com.uem.extrator.model.Instituicao;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class InstituicaoDAO {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(InstituicaoDAO.class);
    
    // Cache estático com TTL de 30 minutos
    private static List<Object[]> cacheInstituicoes = null;
    private static long ultimaAtualizacao = 0;
    private static final long TEMPO_CACHE = 30 * 60 * 1000;

    /**
     * OTIMIZAÇÃO DB2: A versão anterior usava múltiplas cláusulas LIKE com OR
     * na query nativa. No DB2, OR em LIKE impede o uso de índices de texto
     * e gera um full table scan.
     *
     * Solução 1: Reescreve com UPPER() para normalização consistente.
     * Solução 2: Usa o operador LOCATE() do DB2 que é mais eficiente que LIKE
     *            para múltiplos padrões prefixados.
     * Solução 3: Mantém o cache de 30 minutos para evitar re-execução frequente
     *            desta query pesada (lista de instituições raramente muda).
     *
     * Nota: a query mantém o filtro de nomes relevantes (universidades,
     * institutos, etc.) para não retornar ruído como "PROJETOS (PESQUISA)".
     */
    public List<Object[]> listarInstituicoesConsolidadas() {
        if (cacheInstituicoes != null && (System.currentTimeMillis() - ultimaAtualizacao) < TEMPO_CACHE) {
            return cacheInstituicoes;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            /*
             * DB2: LOCATE(substring, string) retorna a posição (>0 se encontrado).
             * É mais eficiente que múltiplos LIKE '%...' para prefixos conhecidos.
             * Usamos UPPER() para normalização sem depender de collation do banco.
             *
             * FETCH FIRST N ROWS ONLY é a sintaxe DB2 nativa para limitar resultados,
             * equivalente ao LIMIT do MySQL. Aqui via Hibernate seria setMaxResults,
             * mas em native query usamos diretamente.
             */
            String sql =
                "SELECT i.nm_instituicao, COUNT(DISTINCT combined.cd_cnpq) AS qtd " +
                "FROM LATTESEXTRATOR.INSTITUICAO i " +
                "LEFT JOIN (" +
                "   SELECT cd_instituicao, cd_cnpq FROM LATTESEXTRATOR.FORMACAO " +
                "   UNION ALL " +
                "   SELECT cd_instituicao, cd_cnpq FROM LATTESEXTRATOR.ATUACAO " +
                ") AS combined ON i.cd_instituicao = combined.cd_instituicao " +
                "WHERE i.nm_instituicao IS NOT NULL " +
                "AND (" +
                "   LOCATE('UNIVERSIDADE', UPPER(i.nm_instituicao)) = 1 " +
                "   OR LOCATE('INSTITUTO',    UPPER(i.nm_instituicao)) = 1 " +
                "   OR LOCATE('FACULDADE',    UPPER(i.nm_instituicao)) = 1 " +
                "   OR LOCATE('CENTRO',       UPPER(i.nm_instituicao)) = 1 " +
                ") " +
                "GROUP BY i.nm_instituicao " +
                "ORDER BY qtd DESC " +
                "FETCH FIRST 500 ROWS ONLY";

            List<Object[]> resultados = session.createNativeQuery(sql).list();
            cacheInstituicoes = resultados;
            ultimaAtualizacao = System.currentTimeMillis();
            return resultados;

        } catch (Exception e) {
            logger.error("Erro na base de dados (InstituicaoDAO)", e);
            return new ArrayList<>();
        }
    }

    public static void limparCache() {
        cacheInstituicoes = null;
    }

    public List<Instituicao> listarTodas() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Instituicao i ORDER BY i.nomeInstituicao", Instituicao.class)
                    .setMaxResults(500)
                    .list();
        } catch (Exception e) {
            logger.error("Erro na base de dados (InstituicaoDAO)", e);
            return new ArrayList<>();
        }
    }

    public Instituicao buscarPorNome(Session session, String nomeInstituicaoStr) {
        if (nomeInstituicaoStr == null || nomeInstituicaoStr.trim().isEmpty()) return null;

        String hql = "FROM Instituicao WHERE upper(trim(nomeInstituicao)) = :nome";

        org.hibernate.query.Query<Instituicao> query = session.createQuery(hql, Instituicao.class);
        query.setParameter("nome", nomeInstituicaoStr.trim().toUpperCase());
        query.setMaxResults(1);

        return query.uniqueResult();
    }
}
