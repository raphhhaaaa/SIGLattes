package com.uem.extrator.util;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
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

            // cria usuario admin se ainda nao existir (se for a primeira vez subindo sistema)
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            if (usuarioDAO.buscarPorLogin("admin") == null) {
                System.out.println(">>> Criando usuário admin padrão...");
                Usuario admin = new Usuario("admin", "admin", "Administrador UEM", true);
                usuarioDAO.salvar(admin);
            }

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