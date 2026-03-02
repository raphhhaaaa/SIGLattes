package com.uem.extrator.service;

import com.uem.extrator.util.ConfigManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

public class AuditLogService {

    // Salva o arquivo na pasta do usuário do sistema operacional (funciona em Linux/Windows)
    private static final String CAMINHO_LOG = System.getProperty("user.home") + File.separator + "extrator_lattes_audit.log";

    // formatos da data
    private static final SimpleDateFormat SDF_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat SDF_FILTRO = new SimpleDateFormat("yyyy-MM-dd");

    public static void log(String acao, String usuario, String detalhes) {
        new AuditLogService().registrarLogGeral(acao, usuario, detalhes);
    }

    public static void registrarExtracao(String tipoExtracao, String usuario, boolean sucesso, String idLattes, String nomePesquisador) {
        // define a ação baseada no sucesso (facilita contagem de curriculos processados no dia)
        String acao = sucesso ? "EXTRACAO_SUCESSO" : "EXTRACAO_FALHA";

        // verifica configuração
        if (!ConfigManager.getInstance().isAuditQueries()) { return; }

        // monta os detalhes
        String detalhes = String.format("Tipo: %s | ID: %s | Nome: %s",
                tipoExtracao,
                (idLattes != null ? idLattes : "N/A"),
                (nomePesquisador != null ? nomePesquisador : "N/A"));

        // loga
        log(acao, usuario, detalhes);
    }

    public synchronized void registrarLogGeral(String acao, String usuario, String detalhes) {
        if (!isAuditoriaHabilitada(acao)) {
            return; // se config estiver desmarcada ignore e nao grava nada
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CAMINHO_LOG, true))) {
            // formato padrão: DATA | ACAO | USUARIO | DETALHES
            String linha = String.format("%s | %-15s | %-15s | %s", SDF_LOG.format(new Date()), acao, usuario, detalhes);

            writer.write(linha);
            writer.newLine();
        } catch (IOException e) {
            System.out.println("Erro ao gravar log de auditoria: " + e.getMessage());
        }
    }

    private boolean isAuditoriaHabilitada(String acao) {
        ConfigManager config = ConfigManager.getInstance();

        // 1. Tentativas de Login
        if (acao.startsWith("LOGIN")) {
            return config.isAuditLogins();
        }

        // 2. Consultas e Extrações (Já verificado no registrarExtracao, mas garantindo aqui também)
        if (acao.startsWith("EXTRACAO") || acao.startsWith("BUSCA") || acao.equals("LOTE")) {
            return config.isAuditQueries();
        }

        // 3. Ações Administrativas (CRUD Usuário, Segurança)
        if (acao.contains("USUARIO") || acao.equals("SEGURANCA") || acao.equals("CONFIG")) {
            return config.isAuditAdminActions();
        }

        // Por padrão, logs desconhecidos são gravados (ou mude para false se quiser ser restritivo)
        return true;
    }

    // Registra que um currículo foi processado agora
    public synchronized void registrarProcessamento(String idLattes) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CAMINHO_LOG, true))) {
            String linha = SDF_LOG.format(new Date()) + "|" + idLattes;
            writer.write(linha);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Erro ao gravar log de auditoria: " + e.getMessage());
        }
    }

    // Lê o arquivo e conta quantas linhas começam com a data de hoje.
    public long contarProcessamentosHoje() {
        File arquivo = new File(CAMINHO_LOG);
        if (!arquivo.exists()) return 0L;

        String dataHoje = SDF_FILTRO.format(new Date());

        try (Stream<String> stream = Files.lines(Paths.get(CAMINHO_LOG))) {
            return stream.filter(linha -> {
                // 1. Tem que ser de hoje
                if (!linha.startsWith(dataHoje)) return false;

                // 2. Lógica Híbrida (Novo + Antigo)
                boolean isNovoSucesso = linha.contains("EXTRACAO_SUCESSO");

                // O log antigo era simples: "DATA|ID".
                // Então, se NÃO for log novo (LOGIN, SEGURANCA, FALHA) e tiver "|", é um log antigo válido.
                boolean isAntigo = !linha.contains("EXTRACAO")
                        && !linha.contains("LOGIN")
                        && !linha.contains("SEGURANCA")
                        && linha.contains("|");

                return isNovoSucesso || isAntigo;
            }).count();
        } catch (IOException e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public List<String> lerLogCompleto() {
        File arquivo = new File(CAMINHO_LOG);
        if (!arquivo.exists()) return new ArrayList<>();

        try {
            List<String> linhas = Files.readAllLines(Paths.get(CAMINHO_LOG));
            // inverte para mostrar mais recente no topo
            Collections.reverse(linhas);
            return linhas;
        } catch (IOException e) {
            List<String> erro = new ArrayList<>();
            erro.add("Erro ao ler arquivo de log: " + e.getMessage());
            return erro;
        }
    }

    public String getCaminhoArquivo() {
        return CAMINHO_LOG;
    }

    public void apagarLog() {
        try {
            Path path = Paths.get(getCaminhoArquivo());
            boolean apagou = Files.deleteIfExists(path); // Deleta se existir
            if (apagou) {
                System.out.println("Arquivo deletado.");
            } else {
                System.out.println("Arquivo não encontrado.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}