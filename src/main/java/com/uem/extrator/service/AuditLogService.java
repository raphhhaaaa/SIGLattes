package com.uem.extrator.service;

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
    private static final SimpleDateFormat SDF_LOG = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat SDF_FILTRO = new SimpleDateFormat("yyyy-MM-dd");


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

        String dataHoje = SDF_FILTRO.format(new Date()); // Ex: "2023-10-27"

        try (Stream<String> stream = Files.lines(Paths.get(CAMINHO_LOG))) {
            // Filtra linhas que começam com a data de hoje e conta
            return stream.filter(linha -> linha.startsWith(dataHoje)).count();
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

//    public void apagarLog() {
//        try {
//            Path path = Paths.get(getCaminhoArquivo());
//            boolean apagou = Files.deleteIfExists(path); // Deleta se existir
//            if (apagou) {
//                System.out.println("Arquivo deletado.");
//            } else {
//                System.out.println("Arquivo não encontrado.");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}