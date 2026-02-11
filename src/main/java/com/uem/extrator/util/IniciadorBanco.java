package com.uem.extrator.util;

import com.uem.extrator.service.AutomacaoService;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class IniciadorBanco implements ServletContextListener {

    public void contextInitialized(ServletContextEvent sce) {
        System.out.println(">>> TOMCAT INICIOU: Acordando o Hibernate...");

        try {
            // Força o carregamento do HibernateUtil agora
            HibernateUtil.getSessionFactory();
            System.out.println(">>> HIBERNATE: Conectado e pronto para uso!");

            // inicia automações
            AutomacaoService.getInstance().iniciarAgendamento();

        } catch (Exception e) {
            System.err.println(">>> ERRO: Hibernate falhou ao iniciar.");
            e.printStackTrace();
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        try {

            // para automações
            AutomacaoService.getInstance().pararTudo();

            HibernateUtil.shutdown();
        } catch (Exception e) {
            System.err.println("Erro ao fechar banco: " + e.getMessage());
        }
    }
}