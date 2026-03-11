package com.uem.extrator.service;

import java.io.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Scanner;
import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.ProducaoDAO;;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.service.LattesService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.zkoss.zk.ui.util.Clients;

public class AutomacaoService {

    private static AutomacaoService instance;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> tarefaBackup;
    private ScheduledFuture<?> tarefaVerificacao;

    // variavel pra evitar spam
    //false == estava online na ultima checagem. true == já avisou que caiu
    private boolean notificadoQueda = false;

    private AutomacaoService() {

        //  cria um pool de 2 threads (uma para ‘backup’ e uma para verificação automática)
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public static synchronized AutomacaoService getInstance() {
        if (instance == null) {
            instance = new AutomacaoService();
        }
        return instance;
    }

    public void iniciarAgendamento() {
        System.out.println(">>> AUTOMAÇÃO: Iniciando/Reiniciando agendamentos...");
        ConfigManager config = ConfigManager.getInstance();

        //  agendar verificação (intervalo em horas)
        if (tarefaVerificacao != null) tarefaVerificacao.cancel(false); // limpa anterior

        if (config.isVerifyEnabled()) {
            int intervaloHoras = config.getVerifyInterval();
            if (intervaloHoras < 1) {
                System.out.println(">>> [VERIFICAÇÃO] O intervalo mínimo para verificação é de 1 hora. Definindo para 1 hora."); // minimo 1 hora
                intervaloHoras = 1;
                config.setVerifyInterval(intervaloHoras);
            }
            System.out.println(">>> AUTOMAÇÃO: Verificação agendada a cada " + intervaloHoras + " horas.");

            // agenda para rodar a cada x horas
            tarefaVerificacao = scheduler.scheduleAtFixedRate(
                    this::rodarVerificacao,
                    intervaloHoras,
                    intervaloHoras,
                    TimeUnit.HOURS
            );

            // agenda para monitorar conexão com o CNPq
            scheduler.scheduleAtFixedRate(this::monitorarConexao, 1, 5, TimeUnit.MINUTES);
            System.out.println("[AUTOMAÇÃO] Monitoramento de conexão ativado (Check a cada 30min).");
        } else {
            System.out.println(">>> AUTOMAÇÃO: Verificação automática DESATIVADA.");
        }

        // agendar backup (horario fixo)
        if (tarefaBackup != null) tarefaBackup.cancel(false); // limpa anterior

        if (config.isBackupEnabled()) {
            String horaString = config.getBackupTime();
            agendarBackupDiario(horaString);
        } else {
            System.out.println(">>> AUTOMAÇÃO: Backup automático DESATIVADO.");
        }
    }

    private void agendarBackupDiario(String horaAlvo) {
        try {
            // calcula quanto tempo falta até o horário alvo do “backup”
            LocalTime agora = LocalTime.now();
            LocalTime alvo = LocalTime.parse(horaAlvo);

            long delayInicialSegundos;

            if (agora.isBefore(alvo)) {
                // se ainda não passou da hora
                delayInicialSegundos = Duration.between(agora, alvo).getSeconds();
            } else {
                // se já passou, agendar para o dia seguinte
                delayInicialSegundos = Duration.between(agora, alvo).plusHours(24).getSeconds();
            }

            if (delayInicialSegundos < 60) {
                System.out.println(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + (delayInicialSegundos) + " segundos).");
            } else if ((delayInicialSegundos/60) < 60) {
                System.out.println(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + (delayInicialSegundos / 60) + " minutos).");
            } else if ((delayInicialSegundos/60) > 60) {
                System.out.println(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + ((delayInicialSegundos / 60) / 60) + " horas).");
            }


            tarefaBackup = scheduler.scheduleAtFixedRate(
                    this::rodarBackup,
                    delayInicialSegundos,
                    24 * 60 * 60, // repete a cada 24 horas (em segundos)
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            if (horaAlvo == null || horaAlvo.trim().isEmpty()) {
                System.out.println(">>> ERRO: Formato de hora inválido para backup: NULO");
            } else {
                System.out.println(">>> ERRO: Formato de hora inválido para backup: " + horaAlvo);
            }
        }
    }

    // --- as tarefas

    private void rodarVerificacao() {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.isVerifyEnabled()) return;

        enviarRelatorioSemanal(); // verifica se é necessário enviar relatório semanal

        System.out.println("=== [AUTO] Iniciando ciclo de verificação... ===");

        // chama o service do lattes
        LattesService service = new LattesService();
        // recebe o relatório com as listas prenchidas (desatualizados, processados, erros...)
        LattesService.RelatorioProcessamento relatorio = service.verificarAtualizacao();

        // envia os emails baseados nas listas
        if (config.isNotifyOutdated() && !relatorio.atualizados.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("<h3>Sincronização automática</h3>");
            msg.append("<p>O sistema detectou e baixou atualizações recentes para os seguintes currículos de pesquisadores/docentes: </p>");
            msg.append(EmailService.getInstance().formatarLista(relatorio.atualizados));
            msg.append("<br><p>O banco de dados local agora está sincronizado com o CNPq.</p>");
            EmailService.getInstance().enviarAlerta("Relatório de Atualização", msg.toString());
        }

        // erros
        if (config.isNotifyConnection() && !relatorio.erros.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("<h3>Erros de Sincronização</h3>");
            msg.append("<p>Falha ao conectar ou processar os seguintes IDs:</p>");
            msg.append(EmailService.getInstance().formatarLista(relatorio.erros));

            EmailService.getInstance().enviarAlerta("Erros na Varredura Lattes", msg.toString());
        }

        // Disco (isso fica aq por que é responsabilidade do servidor, não do Lattes)
        if (config.isNotifyDisk()) {
            try {
                File root = new File("/");
                long freeSpaceGB = root.getFreeSpace() / (1024 * 1024 * 1024);
                if (freeSpaceGB < 5) { // menos que 5gb livres
                    EmailService.getInstance().enviarAlerta("Espaço em Disco Crítico", "Restam apenas " + freeSpaceGB + "GB disponíveis.");
                }
            } catch (Exception e) {
            }
        }
    }

    private void monitorarConexao() {
        ConfigManager config = ConfigManager.getInstance();
        LattesService service = new LattesService();

        if (!config.isNotifyConnection()) {
            notificadoQueda = false;
            return;
        }

        try {
            boolean online = service.testarConexaoCNPq();

            if (!online) {
                if (!notificadoQueda) {
                    System.err.println(">>> [MONITOR] ALERTA: Conexão com CNPq caiu!");

                    StringBuilder msg = new StringBuilder();
                    msg.append("<h3>⚠️ Alerta de Monitoramento</h3>");
                    msg.append("<p>O sistema detectou uma falha de comunicação com o webservice do <b>CNPq</b>.</p>");
                    msg.append("<p>Isso pode impedir novas extrações ou atualizações de currículos temporariamente.</p>");
                    msg.append("<hr>");
                    msg.append("<p><b>Status:</b> 🔴 SERVIÇO INDISPONÍVEL / INSTÁVEL</p>");
                    msg.append("<p><b>Horário:</b> " + new java.util.Date() + "</p>");

                    EmailService.getInstance().enviarAlerta("URGENTE: Falha na Conexão CNPq", msg.toString());

                    // marca que já avisamos, para não mandar outro email daqui 30 min se continuar off
                    notificadoQueda = true;
                }
            } else {
                if (notificadoQueda && service.testarConexaoCNPq()) {
                    // se estava marcado como queda significa que voltou
                    System.out.println(">>> [MONITOR] Conexão restabelecida.");

                    StringBuilder msg = new StringBuilder();
                    msg.append("<h3>✅ Serviço Restabelecido</h3>");
                    msg.append("<p>A comunicação com o CNPq foi normalizada.</p>");

                    EmailService.getInstance().enviarAlerta("Resolvido: Conexão CNPq Normalizada", msg.toString());

                    notificadoQueda = false; // reseta para monitorar futuras quedas
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enviarRelatorioSemanal() {
        ConfigManager config = ConfigManager.getInstance();

        // verifica se a opção está ativada
        if (!config.isNotifyWeekly()) return;

        // verifica se a data de hoje é SEGUNDA-FEIRA
        LocalDate hoje = LocalDate.now();
        if(hoje.getDayOfWeek() != DayOfWeek.MONDAY) {
            return;
        }

        // Controle de “SPAM” (verifica se já enviou hoje)
        // cria um arquivo oculto na pasta do usuario para marcar o dia
        File controle = new File(System.getProperty("user.home") + File.separator + ".lattes_weekly_report.dat");
        if (controle.exists()) {
            try (Scanner scanner = new Scanner(controle)) {
                if (scanner.hasNext()) {
                    String dataUltimoEnvio = scanner.next();
                    if (dataUltimoEnvio.equals(hoje.toString())) {
                        // já enviou hoje, então não faz nada
                        return;
                    }
                }
            } catch (Exception e) {
                System.err.println("[Automacao] Erro ao ler controle semanal: " + e.getMessage());
            }
        }

        System.out.println("[Automacao] Preparando Relatório Semanal...");

        // coleta os dados
        CurriculoDAO curriculoDAO = new CurriculoDAO();
        ProducaoDAO producaoDAO = new ProducaoDAO();

        long totalPesquisadores = curriculoDAO.contarTotalCurriculos();
        long totalProducoes = producaoDAO.contarTotalProducoes();

        // verifica se existe espaço em disco
        long freeSpaceGB = 0;
        try {
            freeSpaceGB = new File("/").getFreeSpace() / (1024 * 1024 * 1024);
        } catch (Exception ignored) {}

        // monta o email (html)
        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: Arial, sans-serif; color: #333; max-width: 600px; border: 1px solid #ddd; padding: 20px;'>");
        html.append("<h2 style='color: #0056b3; border-bottom: 2px solid #0056b3; padding-bottom: 10px;'>📊 Resumo Semanal</h2>");
        html.append("<p>Olá, aqui está o status atual do <strong>Extrator Lattes</strong>:</p>");

        html.append("<table style='width: 100%; border-collapse: collapse; margin-top: 20px;'>");

        html.append("<tr style='background-color: #f9f9f9;'>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd;'>👥 Pesquisadores Monitorados</td>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd; text-align: right; font-weight: bold;'>" + totalPesquisadores + "</td>");
        html.append("</tr>");

        html.append("<tr>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd;'>📚 Total de Produções</td>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd; text-align: right; font-weight: bold;'>" + totalProducoes + "</td>");
        html.append("</tr>");

        html.append("<tr style='background-color: #f9f9f9;'>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd;'>💾 Espaço em Disco Livre</td>");
        html.append("<td style='padding: 12px; border-bottom: 1px solid #ddd; text-align: right; font-weight: bold; color: " + (freeSpaceGB < 10 ? "red" : "green") + ";'>" + freeSpaceGB + " GB</td>");
        html.append("</tr>");

        html.append("</table>");
        html.append("<br><p style='font-size: 12px; color: #999;'>Mensagem automática enviada pelo sistema.</p>");
        html.append("</div>");

        // envia e marca como feito
        EmailService.getInstance().enviarAlerta("Relatório Semanal de Monitoramento", html.toString());

        try (FileWriter writer = new FileWriter(controle)) {
            writer.write(hoje.toString()); // salvar a data atual no arquivo
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void rodarBackup() {
        System.out.println("=== [AUTO] Iniciando Backup do Banco de Dados... ===");
        /**
         * Atenção: importante citar que este metodo é feito especificamente para o banco de dados local
         * MYSQL, quando for feita a migração para o banco da UEM (IBM DB2) essa lógica DEVERÁ ser
         * totalmente reavaliada / refatorada.
         */

        try {
            // 1. Ler configurações do Hibernate
            Properties dbProps = lerConfiguracoesDoHibernate();
            String user = dbProps.getProperty("user");
            String pass = dbProps.getProperty("password");
            String url = dbProps.getProperty("url");

            // Extrair nome do banco da URL
            String dbName = "lattesdb";
            Matcher m = Pattern.compile("3306/(.*?)\\?").matcher(url);
            if (m.find()) dbName = m.group(1);

            // 2. Definir pasta de destino
            String configPath = ConfigManager.getInstance().getConfigPath();
            File configFile = new File(configPath);
            Path backupDir = Paths.get(configFile.getParent(), "backups");
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
            String fileName = "backup_lattes_" + timestamp + ".sql";
            File backupFile = backupDir.resolve(fileName).toFile();

            System.out.println(">>> BACKUP: Gerando arquivo em: " + backupFile.getAbsolutePath());

            // 3. Executar mysqldump SEM o argumento --result-file
            // O mysqldump vai jogar o SQL no console (STDOUT) e o Java vai redirecionar para o arquivo
            ProcessBuilder pb = new ProcessBuilder(
                    "mysqldump",
                    "-u", user,
                    "--password=" + pass,
                    dbName
            );

            // Redireciona a saída do comando direto para o arquivo físico
            pb.redirectOutput(backupFile);
            pb.redirectErrorStream(false); // Mantém erros separados para podermos ler

            Process process = pb.start();

            // 4. Ler possíveis erros do mysqldump (stderr)
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Ignora o aviso chato de senha insegura
                    if (!line.contains("Using a password")) {
                        System.err.println("[mysqldump erro] " + line);
                    }
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("BACKUP CONCLUÍDO COM SUCESSO! Tamanho: " + (backupFile.length() / 1024) + " KB");
            } else {
                System.err.println("ERRO AO REALIZAR BACKUP. Código de saída: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("FALHA CRÍTICA NO BACKUP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // auxiliar: le hibernate.cfg.xml para descobrir credenciais
    private Properties lerConfiguracoesDoHibernate() throws Exception {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("hibernate.cfg.xml")) {
            if (input == null) throw new Exception("Arquivo hibernate.cfg.xml não existe.");

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // desabilita validação do DTD (para não tentar baixar da “internet” atraves da url no hibernate.cgf.xml
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (Exception e) { /* Ignora se o parser não suportar */ }
            factory.setValidating(false);

            DocumentBuilder builder = factory.newDocumentBuilder();

            // se ele tentar baixar algo, entrega um texto vazio
            builder.setEntityResolver(new org.xml.sax.EntityResolver() {
                @Override
                public org.xml.sax.InputSource resolveEntity(String publicId, String systemId) {
                    return new org.xml.sax.InputSource(new java.io.StringReader(""));
                }
            });

            Document doc = builder.parse(input);
            doc.getDocumentElement().normalize();

            NodeList propertyNodes = doc.getElementsByTagName("property");
            for (int i = 0; i < propertyNodes.getLength(); i++) {
                Element element = (Element) propertyNodes.item(i);
                String name = element.getAttribute("name");
                String value = element.getTextContent();

                if (name.contains("connection.username")) props.setProperty("user", value);
                if (name.contains("connection.password")) props.setProperty("password", value);
                if (name.contains("connection.url")) props.setProperty("url", value);
            }
        }
        return props;
    }

    // debug
    public void forcarExecucaoImediata() {
        System.out.println(">>> [Manual] Forçando verificação de atualizações...");
        rodarVerificacao();
    }

    public void pararTudo() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
