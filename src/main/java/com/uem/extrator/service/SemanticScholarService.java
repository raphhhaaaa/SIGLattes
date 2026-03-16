package com.uem.extrator.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;

import java.util.concurrent.ThreadLocalRandom;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class SemanticScholarService {

    private static final String API_KEY = "rTIBDXH92K98RBMkcppjV5jetzlFfsadRR7xOfA9";
    private static final int RATE_LIMIT_DELAY_MS = 1500;
    private static final int MAX_RETRIES = 3; // Quantas vezes vai tentar de novo se tomar 429
    private static final Gson gson = new Gson();

    public void enriquecerDadosBibliometricos(Curriculo curriculo, List<Producao> producoes) {
        List<Producao> producoesComDoi = new ArrayList<>();
        String doiAncora = null;

        for (Producao p : producoes) {
            if (p.getDoi() != null && p.getDoi().trim().length() > 5) {
                producoesComDoi.add(p);
                if (doiAncora == null) {
                    doiAncora = p.getDoi().trim();
                }
            }
        }

        if (producoesComDoi.isEmpty()) return;

        try {
            Integer hIndex = buscarHIndexPorAncora(doiAncora, curriculo.getNomeCompleto());
            if (hIndex != null) {
                curriculo.setIndiceH(hIndex);
            }
        } catch (Exception e) {
            // Silenciado: Se falhar por DOI inválido, segue em frente
        } finally {
            dormir(RATE_LIMIT_DELAY_MS);
        }

        try {
            atualizarCitacoesEmLote(producoesComDoi);
        } catch (Exception e) {
            // Silenciado
        }
    }

    private Integer buscarHIndexPorAncora(String doi, String nomeProfessor) throws Exception {
        String endpoint = "https://api.semanticscholar.org/graph/v1/paper/DOI:" + doi + "?fields=authors.name,authors.hIndex";

        // LOOP DE RETENTATIVA
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            HttpURLConnection conn = configurarConexao(endpoint, "GET");
            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                String jsonResposta = lerRespostaSegura(conn);
                if (jsonResposta.isEmpty()) return null;

                JsonObject response = gson.fromJson(jsonResposta, JsonObject.class);
                if (response != null && response.has("authors") && !response.get("authors").isJsonNull()) {
                    JsonArray authors = response.getAsJsonArray("authors");
                    for (JsonElement element : authors) {
                        if (element != null && !element.isJsonNull()) {
                            JsonObject author = element.getAsJsonObject();
                            if (author.has("name") && !author.get("name").isJsonNull() &&
                                    author.has("hIndex") && !author.get("hIndex").isJsonNull()) {
                                String authorName = author.get("name").getAsString();
                                if (nomesSemelhantes(nomeProfessor, authorName)) {
                                    return author.get("hIndex").getAsInt();
                                }
                            }
                        }
                    }
                }
                return null; // Achou o artigo, mas não o autor correspondente

            } else if (statusCode == 429) {
                int tempoJitter = 3000 + ThreadLocalRandom.current().nextInt(2000);
                System.out.println("Aviso Semantic: Rate Limit (429) atingido no H-Index. Tentativa " + tentativa + " de " + MAX_RETRIES + ". Aguardando " + tempoJitter + "ms...");
                dormir(tempoJitter); // Pausa longa para a API respirar
            } else {
                break; // Outro erro qualquer (404 Not Found, etc), aborta o loop
            }
        }
        return null;
    }

    private void atualizarCitacoesEmLote(List<Producao> producoesComDoi) throws Exception {
        int batchSize = 400;

        for (int i = 0; i < producoesComDoi.size(); i += batchSize) {
            List<Producao> loteAtual = producoesComDoi.subList(i, Math.min(i + batchSize, producoesComDoi.size()));

            JsonArray idsArray = new JsonArray();
            for (Producao p : loteAtual) {
                idsArray.add("DOI:" + p.getDoi().trim());
            }
            JsonObject requestBody = new JsonObject();
            requestBody.add("ids", idsArray);
            String payload = requestBody.toString();

            String endpoint = "https://api.semanticscholar.org/graph/v1/paper/batch?fields=externalIds,citationCount,isOpenAccess";

            // LOOP DE RETENTATIVA PARA O LOTE
            for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
                HttpURLConnection conn = configurarConexao(endpoint, "POST");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int statusCode = conn.getResponseCode();

                if (statusCode == 200) {
                    String jsonResposta = lerRespostaSegura(conn);
                    if (!jsonResposta.isEmpty()) {
                        JsonArray responseArray = gson.fromJson(jsonResposta, JsonArray.class);
                        if (responseArray != null) {
                            for (int j = 0; j < responseArray.size(); j++) {
                                if (j >= loteAtual.size()) break;

                                JsonElement element = responseArray.get(j);
                                if (element != null && !element.isJsonNull()) {
                                    JsonObject paper = element.getAsJsonObject();
                                    Producao prod = loteAtual.get(j);

                                    if (paper.has("citationCount") && !paper.get("citationCount").isJsonNull()) {
                                        prod.setCitacoes(paper.get("citationCount").getAsInt());
                                    }
                                    if (paper.has("isOpenAccess") && !paper.get("isOpenAccess").isJsonNull()) {
                                        boolean isOpen = paper.get("isOpenAccess").getAsBoolean();
                                        prod.setStatusAcesso(isOpen ? "ABERTO" : "FECHADO");
                                    }
                                }
                            }
                        }
                    }
                    break; // Sucesso! Sai do loop de retentativa e avança para o próximo lote

                } else if (statusCode == 429) {
                    int tempoJitter = 3000 + ThreadLocalRandom.current().nextInt(2000);
                    System.out.println("Aviso Semantic: Rate Limit (429) atingido no Lote (Batch). Tentativa " + tentativa + " de " + MAX_RETRIES + ". Aguardando " + tempoJitter + "ms...");
                    dormir(tempoJitter); // Pausa longa e vai tentar o MESMO lote de novo no próximo ciclo do for
                } else {
                    break; // Outro erro, avança para o próximo lote para não travar a fila
                }
            }
            dormir(RATE_LIMIT_DELAY_MS);
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES E DE SEGURANÇA
    // ==========================================

    private String lerRespostaSegura(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                sb.append(linha);
            }
        }
        return sb.toString().trim();
    }

    private HttpURLConnection configurarConexao(String endpoint, String method) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("x-api-key", API_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        if (method.equals("POST")) {
            conn.setDoOutput(true);
        }
        return conn;
    }

    private void dormir(int milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean nomesSemelhantes(String nomeLattes, String nomeApi) {
        if (nomeLattes == null || nomeApi == null) return false;

        String cleanLattes = removerAcentos(nomeLattes.toLowerCase()).trim();
        String cleanApi = removerAcentos(nomeApi.toLowerCase()).trim();

        if (cleanLattes.isEmpty() || cleanApi.isEmpty()) return false;

        String[] partesApi = cleanApi.split("\\s+");
        if (partesApi.length == 0) return false;

        String ultimoNomeApi = partesApi[partesApi.length - 1];
        if (ultimoNomeApi.isEmpty()) return false;

        String primeiraLetraApi = partesApi[0].substring(0, 1);
        return cleanLattes.contains(ultimoNomeApi) && cleanLattes.startsWith(primeiraLetraApi);
    }

    private String removerAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }
}