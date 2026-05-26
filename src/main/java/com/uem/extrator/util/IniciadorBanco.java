package com.uem.extrator.util;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AutomacaoService;
import com.uem.extrator.dao.QualisDAO;
import com.uem.extrator.model.Qualis;
import org.hibernate.internal.util.xml.BufferedXMLEventReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(IniciadorBanco.class);

    public void contextInitialized(ServletContextEvent sce) {
        logger.info(">>> TOMCAT INICIOU: Iniciando o Hibernate...");

        try {
            // Força o carregamento do HibernateUtil agora
            HibernateUtil.getSessionFactory();
            logger.info(">>> HIBERNATE: Conectado e pronto para uso!");

            // cria usuario admin se ainda nao existir (se for a primeira vez subindo sistema)
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            if (usuarioDAO.buscarPorLogin("admin") == null) {
                logger.info(">>> Criando usuário admin padrão...");
                Usuario admin = new Usuario("admin", "admin", "Administrador UEM", true);
                usuarioDAO.salvar(admin);
            }

            // inicia automações
            AutomacaoService.getInstance().iniciarAgendamento();

            // inicia qualis
            inicializarQualis();

        } catch (Exception e) {
            logger.error(">>> ERRO: Hibernate falhou ao iniciar: ", e);
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
            logger.error("Erro ao fechar banco: ", e);
        }
    }

    private void inicializarQualis() {
        QualisDAO qualisDAO = new QualisDAO();

        if (qualisDAO.contarTodos() > 0) {
            logger.info(">>> [QUALIS] Base já populada. Ignorando importação.");
            return;
        }

        logger.info(">>> [QUALIS] Iniciando a leitura do ficheiro CAPES...");
        Map<String, Qualis> mapaQualis = new HashMap<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("qualis.csv");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

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

                    // Sanitização Defensiva para o Banco de Dados (DB2)
                    if (issn != null && issn.length() > 20) issn = issn.substring(0, 20);
                    if (nomeRevista != null && nomeRevista.length() > 500) nomeRevista = nomeRevista.substring(0, 500);
                    if (nota != null && nota.length() > 5) nota = nota.substring(0, 5);

                    if (!issn.isEmpty()) {
                        Qualis qAtual = mapaQualis.get(issn);

                        // Se não existe no Map, adiciona
                        if (qAtual == null) {
                            Qualis qNovo = new Qualis();
                            qNovo.setIssn(issn);
                            qNovo.setEstrato(nota);
                            qNovo.setNomeRevista(nomeRevista);
                            mapaQualis.put(issn, qNovo);
                        }
                        // Se já existe, substitui apenas se a NOVA nota for MAIOR que a antiga
                        else if (QualisDAO.pesoEstrato(nota) > QualisDAO.pesoEstrato(qAtual.getEstrato())) {
                            qAtual.setEstrato(nota);
                        }
                    }
                }
            }

            List<Qualis> lote = new ArrayList<>(mapaQualis.values());
            logger.info(">>> [QUALIS] Inserindo {} revistas...", lote.size());
            qualisDAO.salvarEmLote(lote);
            logger.info(">>> [QUALIS] Importação concluída com sucesso!");

        } catch (Exception e) {
            logger.error(">>> [QUALIS] Erro ao popular Qualis: ", e);
        }
    }

}