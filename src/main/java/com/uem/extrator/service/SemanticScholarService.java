package com.uem.extrator.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;
import com.uem.extrator.util.ConfigManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Semaphore;

public class SemanticScholarService {

    private static final String API_KEY = "rTIBDXH92K98RBMkcppjV5jetzlFfsadRR7xOfA9";
    private static final int RATE_LIMIT_DELAY_MS = ConfigManager.TEMPO_ESPERA_API_RATE_LIMIT_MS;
    private static final int MAX_RETRIES = 5;
    private static final Gson gson = new Gson();
    // semaphore
    private static final Semaphore pedagioApi = new Semaphore(ConfigManager.SEMAFORO_SEMANTIC_SCHOLAR);

    public void enriquecerDadosBibliometricos(Curriculo curriculo, List<Producao> producoes) {
        if (curriculo == null || producoes == null) return;

        try {
            pedagioApi.acquire();

            List<Producao> producoesComDoi = new ArrayList<>();
            String doiAncora = null;

            // 1. FILTRO RIGOROSO E PREVENÇÃO DE NULLS
            for (Producao p : producoes) {
                if (p != null && p.getDoi() != null) {
                    String doiLimpo = limparDoiValido(p.getDoi());
                    if (doiLimpo != null && !doiLimpo.isEmpty()) {
                        p.setDoi(doiLimpo);
                        producoesComDoi.add(p);
                        if (doiAncora == null) {
                            doiAncora = doiLimpo;
                        }
                    }
                }
            }

            Integer hIndex = null;

            try {
                String orcid = obterOrcidSeExistir(curriculo);
                hIndex = buscarHIndexPorNomeEOrcid(curriculo.getNomeCompleto(), orcid);
            } catch (Exception e) {
                System.err.println("Erro na Camada 1/2 (Nome/ORCID) para " + curriculo.getNomeCompleto() + ": " + e.toString());
                e.printStackTrace();
            } finally {
                dormir(RATE_LIMIT_DELAY_MS);
            }

            if (hIndex == null && doiAncora != null) {
                System.out.println("-> Homónimos detetados. Iniciando Camada 3 (DOI Âncora) para: " + curriculo.getNomeCompleto());
                try {
                    hIndex = buscarHIndexPorAncora(doiAncora, curriculo.getNomeCompleto());
                } catch (Exception e) {
                    System.err.println("Erro na Camada 3 (DOI) para " + curriculo.getNomeCompleto() + ": " + e.toString());
                    e.printStackTrace();
                } finally {
                    dormir(RATE_LIMIT_DELAY_MS);
                }
            }

            if (hIndex != null) {
                curriculo.setIndiceH(hIndex);
            }

            // ==========================================
            // BATCH: Atualizar Citações e Acesso Aberto
            // ==========================================
            if (!producoesComDoi.isEmpty()) {
                try {
                    atualizarCitacoesEmLote(producoesComDoi);
                } catch (Exception e) {
                    System.err.println("Erro Crítico no Lote de " + curriculo.getNomeCompleto() + ": " + e.toString());
                    e.printStackTrace();
                }
            }
        } catch (InterruptedException interruptedException)  {
            Thread.currentThread().interrupt();
            System.err.println("Thread interrompida enquanto aguardava o pedágio: " + curriculo.getNomeCompleto());
        } finally {
            pedagioApi.release();
        }
    }


    private Integer buscarHIndexPorNomeEOrcid(String nomeProfessor, String orcid) throws Exception {
        if (nomeProfessor == null || nomeProfessor.trim().isEmpty()) return null;

        String query = URLEncoder.encode(removerAcentos(nomeProfessor), StandardCharsets.UTF_8.toString());
        String endpoint = "https://api.semanticscholar.org/graph/v1/author/search?query=" + query + "&fields=name,hIndex,externalIds&limit=10";

        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            HttpURLConnection conn = configurarConexao(endpoint, "GET");
            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                String jsonResposta = lerResposta(conn.getInputStream());
                if (jsonResposta.isEmpty()) return null;

                JsonObject response = gson.fromJson(jsonResposta, JsonObject.class);
                if (response != null && response.has("data") && response.get("data").isJsonArray()) {
                    JsonArray data = response.getAsJsonArray("data");
                    if (data.size() == 0) return null;

                    // CAMADA 1: ORCID (Usando loop for tradicional para evitar NoSuchElementException)
                    if (orcid != null && !orcid.trim().isEmpty()) {
                        for (int i = 0; i < data.size(); i++) {
                            JsonElement element = data.get(i);
                            if (element.isJsonObject()) {
                                JsonObject author = element.getAsJsonObject();
                                if (author.has("externalIds") && author.get("externalIds").isJsonObject()) {
                                    JsonObject extIds = author.getAsJsonObject("externalIds");
                                    if (extIds.has("ORCID") && !extIds.get("ORCID").isJsonNull()) {
                                        if (extIds.get("ORCID").getAsString().equals(orcid.trim())) {
                                            return author.has("hIndex") && !author.get("hIndex").isJsonNull() ? author.get("hIndex").getAsInt() : null;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // CAMADA 2: Único no mundo
                    if (data.size() == 1) {
                        JsonElement element = data.get(0);
                        if (element.isJsonObject()) {
                            JsonObject author = element.getAsJsonObject();
                            return author.has("hIndex") && !author.get("hIndex").isJsonNull() ? author.get("hIndex").getAsInt() : null;
                        }
                    }
                }
                return null;
            } else if (statusCode == 429) {
                dormirComJitter(tentativa, "Busca de Autor");
            } else {
                break;
            }
        }
        return null;
    }

    private Integer buscarHIndexPorAncora(String doi, String nomeProfessor) throws Exception {
        String endpointArtigo = "https://api.semanticscholar.org/graph/v1/paper/DOI:" + doi + "?fields=authors";
        String authorIdEncontrado = null;

        // PASSO A: Descobrir o ID do Autor usando o DOI
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            HttpURLConnection conn = configurarConexao(endpointArtigo, "GET");
            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                String jsonResposta = lerResposta(conn.getInputStream());
                if (jsonResposta.isEmpty()) break;

                JsonObject response = gson.fromJson(jsonResposta, JsonObject.class);
                if (response != null && response.has("authors") && response.get("authors").isJsonArray()) {
                    JsonArray authors = response.getAsJsonArray("authors");

                    // Loop tradicional contra NoSuchElementException
                    for (int i = 0; i < authors.size(); i++) {
                        JsonElement element = authors.get(i);
                        if (element.isJsonObject()) {
                            JsonObject author = element.getAsJsonObject();
                            if (author.has("name") && author.has("authorId") && !author.get("authorId").isJsonNull()) {
                                if (nomesSemelhantes(nomeProfessor, author.get("name").getAsString())) {
                                    authorIdEncontrado = author.get("authorId").getAsString();
                                    break;
                                }
                            }
                        }
                    }
                }
                break;
            } else if (statusCode == 429) {
                dormirComJitter(tentativa, "Busca de Artigo Âncora");
            } else {
                break;
            }
        }

        if (authorIdEncontrado == null) return null;
        dormir(RATE_LIMIT_DELAY_MS);

        // PASSO B: Descobrir o H-Index usando o ID do Autor
        String endpointAuthor = "https://api.semanticscholar.org/graph/v1/author/" + authorIdEncontrado + "?fields=hIndex";
        for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
            HttpURLConnection conn = configurarConexao(endpointAuthor, "GET");
            int statusCode = conn.getResponseCode();

            if (statusCode == 200) {
                String jsonResposta = lerResposta(conn.getInputStream());
                if (jsonResposta.isEmpty()) return null;

                JsonObject response = gson.fromJson(jsonResposta, JsonObject.class);
                if (response != null && response.has("hIndex") && !response.get("hIndex").isJsonNull()) {
                    return response.get("hIndex").getAsInt();
                }
                return null;
            } else if (statusCode == 429) {
                dormirComJitter(tentativa, "Busca de perfil pelo ID do autor");
            } else {
                break;
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
                idsArray.add("DOI:" + p.getDoi());
            }
            JsonObject requestBody = new JsonObject();
            requestBody.add("ids", idsArray);
            String payload = requestBody.toString();

            String endpoint = "https://api.semanticscholar.org/graph/v1/paper/batch?fields=externalIds,citationCount,isOpenAccess";

            for (int tentativa = 1; tentativa <= MAX_RETRIES; tentativa++) {
                HttpURLConnection conn = configurarConexao(endpoint, "POST");

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int statusCode = conn.getResponseCode();

                if (statusCode == 200) {
                    String jsonResposta = lerResposta(conn.getInputStream());
                    if (!jsonResposta.isEmpty()) {

                        // Parse seguro para evitar NullPointerException ou erros se a API devolver JSON não-array
                        try {
                            JsonElement root = gson.fromJson(jsonResposta, JsonElement.class);
                            if (root != null && root.isJsonArray()) {
                                JsonArray responseArray = root.getAsJsonArray();

                                for (int j = 0; j < responseArray.size(); j++) {
                                    if (j >= loteAtual.size()) break;

                                    JsonElement element = responseArray.get(j);
                                    Producao prod = loteAtual.get(j);

                                    // Prevenção extra contra NPE
                                    if (element != null && element.isJsonObject()) {
                                        JsonObject paper = element.getAsJsonObject();

                                        if (paper.has("citationCount") && !paper.get("citationCount").isJsonNull()) {
                                            prod.setCitacoes(paper.get("citationCount").getAsInt());
                                        } else {
                                            prod.setCitacoes(0);
                                        }

                                        if (paper.has("isOpenAccess") && !paper.get("isOpenAccess").isJsonNull()) {
                                            boolean isOpen = paper.get("isOpenAccess").getAsBoolean();
                                            prod.setStatusAcesso(isOpen ? "ABERTO" : "FECHADO");
                                        }
                                    } else {
                                        prod.setCitacoes(0);
                                    }
                                }
                            } else {
                                System.err.println("Aviso: Lote retornou formato inválido. Ignorando.");
                            }
                        } catch (JsonSyntaxException jse) {
                            System.err.println("Erro ao processar JSON do Lote: " + jse.getMessage());
                        }
                    }
                    break;
                } else if (statusCode == 429) {
                    dormirComJitter(tentativa, "Batch de Citações");
                } else {
                    break;
                }
            }
            dormir(RATE_LIMIT_DELAY_MS);
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES E DE SEGURANÇA
    // ==========================================

    /**
     * Aplica um Jitter + Backoff Linear para evitar o Efeito Manada (Thundering Herd)
     * Ex: Tentativa 1 = ~3 seg. Tentativa 2 = ~6 seg.
     */
    private void dormirComJitter(int tentativa, String contexto) {
        int tempoBase = tentativa * 3000;
        int jitter = ThreadLocalRandom.current().nextInt(2000); // 0 a 2000ms aleatórios
        int tempoEspera = tempoBase + jitter;

        System.out.println("Aviso Semantic (429): Rate Limit em [" + contexto + "]. Aplicando Jitter: " + tempoEspera + "ms (Tentativa " + tentativa + ")");
        dormir(tempoEspera);
    }

    private String limparDoiValido(String doi) {
        if (doi == null || doi.trim().isEmpty()) return null;
        String limpo = doi.replaceAll("(?i)https?://(dx\\.)?doi\\.org/", "")
                .replaceAll("(?i)doi:", "")
                .trim();
        if (limpo.startsWith("10.") && limpo.contains("/")) {
            return limpo;
        }
        return null;
    }

    private String lerResposta(InputStream stream) {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                sb.append(linha);
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString().trim();
    }

    private HttpURLConnection configurarConexao(String endpoint, String method) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(java.net.Proxy.NO_PROXY);

        conn.setRequestMethod(method);
        if (API_KEY != null && !API_KEY.isEmpty()) {
            conn.setRequestProperty("x-api-key", API_KEY);
        }
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15000); // Aumentei o tempo de tolerância para 15 segundos
        conn.setReadTimeout(15000);

        if (method.equals("POST")) {
            conn.setDoOutput(true);
        }
        return conn;
    }

    private void dormir(int milisegundos) {
        try { Thread.sleep(milisegundos); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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

    private String obterOrcidSeExistir(Curriculo curriculo) {
        try {
            java.lang.reflect.Method metodo = curriculo.getClass().getMethod("getOrcid");
            Object resultado = metodo.invoke(curriculo);
            return (resultado != null) ? resultado.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}