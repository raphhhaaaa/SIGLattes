package com.uem.extrator.service;

import java.io.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Scanner;
import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.ProducaoDAO;;
import com.uem.extrator.model.Usuario;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Date;

public class AutomacaoService {

    private static final Logger logger = LoggerFactory.getLogger(AutomacaoService.class);

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
        logger.info(">>> AUTOMAÇÂO: Iniciando/Reiniciando agendamentos...");
        ConfigManager config = ConfigManager.getInstance();

        //  agendar verificação (intervalo em horas)
        if (tarefaVerificacao != null) tarefaVerificacao.cancel(false); // limpa anterior

        if (config.isVerifyEnabled()) {
            int intervaloHoras = config.getVerifyInterval();
            if (intervaloHoras < 1) {
                logger.info(">>> [VERIFICAÇÃO] O intervalo mínimo para verificação é de 1 hora. Definindo para 1 hora."); // minimo 1 hora
                config.setVerifyInterval(intervaloHoras);
            }
            logger.info(">>> [AUTOMAÇÂO] Verificação agendada a cada {} horas", intervaloHoras);
            // agenda para rodar a cada x horas
            tarefaVerificacao = scheduler.scheduleAtFixedRate(
                    this::rodarVerificacao,
                    intervaloHoras,
                    intervaloHoras,
                    TimeUnit.HOURS
            );

            // agenda para monitorar conexão com o CNPq
            if (config.isNotifyConnection()) {
                scheduler.scheduleAtFixedRate(this::monitorarConexao, 1, 5, TimeUnit.MINUTES);
                logger.info("[AUTOMAÇÃO] Monitoramento de conexão ativado (Check a cada 30min).");
            } else {
                logger.info("[AUTOMAÇÃO] Monitoramento de conexão desativado.");
            }
        } else {
            logger.info(">>> [AUTOMAÇÃO] Verificação automática DESATIVADA.");
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
                logger.info(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + (delayInicialSegundos) + " segundos).");
            } else if ((delayInicialSegundos/60) < 60) {
                logger.info(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + (delayInicialSegundos / 60) + " minutos).");
            } else if ((delayInicialSegundos/60) > 60) {
                logger.info(">>> AUTOMAÇÃO: Backup agendado para " + horaAlvo + " (em " + ((delayInicialSegundos / 60) / 60) + " horas).");
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

        logger.info("=== [AUTO] Iniciando ciclo de verificação... ===");
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
                    logger.error(">>> [MONITOR] ALERTA: Conexão com CNPq caiu!");

                    StringBuilder msg = new StringBuilder();
                    msg.append("<h3>⚠️ Alerta de Monitoramento</h3>");
                    msg.append("<p>O sistema detectou uma falha de comunicação com o webservice do <b>CNPq</b>.</p>");
                    msg.append("<p>Isso pode impedir novas extrações ou atualizações de currículos temporariamente.</p>");
                    msg.append("<hr>");
                    msg.append("<p><b>Status:</b> 🔴 SERVIÇO INDISPONÍVEL / INSTÁVEL</p>");
                    msg.append("<p><b>Horário:</b> " + new Date() + "</p>");

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
            logger.error("Erro ao monitorar conexão com CNPq: ", e);
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
                logger.error("[AUTOMACAO] Erro ao ler controle semanal: ", e);
            }
        }

        logger.info("[Automacao] Preparando Relatório Semanal...");
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
            logger.error("Erro ao enviar Relatório Semanal de Monitoramento: ", e);
        }
    }

    private void rodarBackup() {
        logger.info("=== [AUTO] Iniciando Backup do Banco de Dados... (DB2 DOCKER) ===");

        try {
            // A pasta onde o DB2 vai jogar o backup DENTRO do container
            // (Esta pasta está segura e mapeada no seu volume db2_data no disco físico)
            String backupDirDb2 = "/database/data";

            logger.info(">>> BACKUP: Solicitando backup binário nativo ao DB2 via Docker...");

            // entra no docker e executa o backup a quente (online)
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "exec", "db2_server", "bash", "-c",
                    "su - db2inst1 -c 'db2 backup database LATTES online to " + backupDirDb2 + " include logs'"
            );

            /***
             *  O backup é salvo no diretório remoto do docker: /database/data/ LATTES....
             *
             *  para puxar ele para a maquina local, no terminal, rode o seguinte comando:
             *
             *  docker cp db2_server:/database/data/ IDENTIFICADOR DO TIMESTAMP.001 /sua/pasta/onde/quer/salvar
             *
             */

            // Junta a saída normal e os erros num fluxo só para lermos no console do Java
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean sucesso = false;

            // Lê o que o DB2 está a responder no terminal invisível
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("[DB2 BACKUP] {}", line);
                    // O DB2 sempre devolve esta frase quando dá certo
                    if (line.contains("Backup successful")) {
                        sucesso = true;
                    }
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && sucesso) {
                logger.info(">>> BACKUP CONCLUÍDO COM SUCESSO!");
                System.out.println(">>> O arquivo de imagem (.001) foi salvo de forma segura no volume do Docker.");
            } else {
                System.err.println(">>> ERRO AO REALIZAR BACKUP. Código de saída do Docker: " + exitCode);
            }

        } catch (Exception e) {
            logger.error("Falha crítica no backup DB2", e);
        }
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
