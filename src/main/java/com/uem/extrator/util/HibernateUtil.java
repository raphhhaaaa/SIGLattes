package com.uem.extrator.util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

// IMPORTS NOVOS DO C3P0
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.PooledDataSource;
import com.mchange.v2.c3p0.PoolBackedDataSource;
import com.mchange.v2.c3p0.WrapperConnectionPoolDataSource;

import java.util.Set;

public class HibernateUtil {

    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            System.out.println("------ TENTANDO INICIAR HIBERNATE ------");
            return new Configuration().configure().buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("❌ FALHA CRÍTICA NO HIBERNATE: " + ex.getMessage());
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    public static String getPoolStatus() {
        try {
            Set<?> sources = C3P0Registry.getPooledDataSources();

            if (sources != null && !sources.isEmpty()) {
                Object dataSource = sources.iterator().next();

                int ocupadas = 0;
                int totalAbertas = 0;
                String maximoStr = "?";

                // CASO 1: ComboPooledDataSource
                if (dataSource instanceof ComboPooledDataSource) {
                    ComboPooledDataSource cpds = (ComboPooledDataSource) dataSource;
                    ocupadas = cpds.getNumBusyConnectionsDefaultUser();
                    totalAbertas = cpds.getNumConnectionsDefaultUser();
                    maximoStr = String.valueOf(cpds.getMaxPoolSize());

                    // CASO 2: PoolBackedDataSource
                } else if (dataSource instanceof PoolBackedDataSource) {
                    PoolBackedDataSource pbds = (PoolBackedDataSource) dataSource;
                    ocupadas = pbds.getNumBusyConnectionsDefaultUser();
                    totalAbertas = pbds.getNumConnectionsDefaultUser();

                    if (pbds.getConnectionPoolDataSource() instanceof WrapperConnectionPoolDataSource) {
                        WrapperConnectionPoolDataSource wcpds = (WrapperConnectionPoolDataSource) pbds.getConnectionPoolDataSource();
                        maximoStr = String.valueOf(wcpds.getMaxPoolSize());
                    }

                } else if (dataSource instanceof PooledDataSource) {
                    // Fallback Genérico
                    PooledDataSource pds = (PooledDataSource) dataSource;
                    ocupadas = pds.getNumBusyConnectionsDefaultUser();
                    totalAbertas = pds.getNumConnectionsDefaultUser();
                }

                return String.format("%d/%s (Ativas: %d)", totalAbertas, maximoStr, ocupadas);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Erro: " + e.getMessage();
        }
        return "Sem Pool";
    }
}