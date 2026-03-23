package com.uem.extrator.util;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AutomacaoService;
import com.uem.extrator.dao.QualisDAO;
import com.uem.extrator.model.Qualis;
import org.hibernate.internal.util.xml.BufferedXMLEventReader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

            // inicia qualis
            inicializarQualis();

        } catch (Exception e) {
            System.err.println(">>> ERRO: Hibernate falhou ao iniciar.");
            e.printStackTrace();
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        try {
            // encerra e limpa as threads
            com.uem.extrator.viewmodel.ExtratorVM.encerrarThreads();

            // para automações
            AutomacaoService.getInstance().pararTudo();

            // encerra Hibernate
            HibernateUtil.shutdown();
        } catch (Exception e) {
            System.err.println("Erro ao fechar banco: " + e.getMessage());
        }
    }

    private void inicializarQualis() {
        com.uem.extrator.dao.QualisDAO qualisDAO = new com.uem.extrator.dao.QualisDAO();

        if (qualisDAO.contarTodos() > 0) {
            System.out.println(">>> [Qualis] Base já populada. Ignorando importação.");
            return;
        }

        System.out.println(">>> [Qualis] Iniciando a leitura do ficheiro CAPES...");
        java.util.Map<String, com.uem.extrator.model.Qualis> mapaQualis = new java.util.HashMap<>();

        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream("qualis.csv");
             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {

            if (is == null) return;

            String linha;
            boolean primeiraLinha = true;

            while ((linha = br.readLine()) != null) {
                if (primeiraLinha) { primeiraLinha = false; continue; }

                String[] colunas = linha.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                if (colunas.length >= 4) {
                    String issn = colunas[0].replace("\"", "").trim();
                    String nota = colunas[3].replace("\"", "").trim();
                    String nomeRevista = colunas[1].replace("\"", "").trim();

                    if (!issn.isEmpty()) {
                        com.uem.extrator.model.Qualis qAtual = mapaQualis.get(issn);

                        // Se não existe no Map, adiciona
                        if (qAtual == null) {
                            com.uem.extrator.model.Qualis qNovo = new com.uem.extrator.model.Qualis();
                            qNovo.setIssn(issn);
                            qNovo.setEstrato(nota);
                            qNovo.setNomeRevista(nomeRevista);
                            mapaQualis.put(issn, qNovo);
                        }
                        // Se já existe, substitui apenas se a NOVA nota for MAIOR que a antiga
                        else if (pesoNota(nota) > pesoNota(qAtual.getEstrato())) {
                            qAtual.setEstrato(nota);
                        }
                    }
                }
            }

            java.util.List<com.uem.extrator.model.Qualis> lote = new java.util.ArrayList<>(mapaQualis.values());
            System.out.println(">>> [Qualis] Inserindo " + lote.size() + " revistas...");
            qualisDAO.salvarEmLote(lote);
            System.out.println(">>> [Qualis] Importação concluída com sucesso!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Metodo auxiliar para saber qual nota vale mais
    private int pesoNota(String nota) {
        if (nota == null) return 0;
        switch (nota.toUpperCase()) {
            case "A1":
                return 8;
            case "A2":
                return 7;
            case "A3":
                return 6;
            case "A4":
                return 5;
            case "B1":
                return 4;
            case "B2":
                return 3;
            case "B3":
                return 2;
            case "B4":
                return 1;
            case "C":
                return 0;
            default:
                return -1;
        }
    }
}