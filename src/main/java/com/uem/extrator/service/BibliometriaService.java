package com.uem.extrator.service;

import com.sun.xml.bind.v2.runtime.output.SAXOutput;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BibliometriaService {

    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .proxy(ProxySelector.of(null))
            .build();

    // gestão de tráfego independente por API
    private static final ConcurrentHashMap<String, Long> controleTrafego = new ConcurrentHashMap<>();

    public static Integer buscarCitacoes(String doi) {
        if (doi == null || doi.trim().isEmpty()) return null;

        try {
            String doiEncoded = limparEEncodarDoi(doi, true);
            String url = "https://api.crossref.org/works/" + doiEncoded + "?mailto=extrator@uem.br";

            System.out.println(">>> CROSSREF BUSCANDO: " + url);
            String json = fazerRequisicao(url, "CrossRef", doi);
            if (json == null) return null;

            Matcher m = Pattern.compile("\"is-referenced-by-count\"\\s*:\\s*(\\d+)").matcher(json);
            if (m.find()) {
                Integer cits = Integer.parseInt(m.group(1));
                System.out.println(">>> [CrossRef] SUCESSO: " + doi + "-> " + cits + " citações.");
                return cits;
            }

        } catch (Exception e) {
            System.err.println("Erro CrossRef [" + doi + "]: " + e.getMessage());
            return null;
        }
        return 0;
    }

    public static String[] buscarStatusAcesso(String doi) {
        if (doi == null || doi.trim().isEmpty()) return new String[]{"-", "secondary"};

        try {
            String doiUrl = limparEEncodarDoi(doi, false);
            String url = "https://api.unpaywall.org/v2/" + doiUrl + "?email=extrator@uem.br";

            System.out.println(">>> UNPAYWALL BUSCANDO: " + url);
            String json = fazerRequisicao(url, "Unpaywall", doi);

            if (json == null) {
                // Fallback com encoding completo
                String doiEncoded = limparEEncodarDoi(doi, true);
                String url2 = "https://api.unpaywall.org/v2/" + doiEncoded + "?email=extrator@uem.br";
                json = fazerRequisicao(url2, "Unpaywall-Fallback", doi);
            }

            if (json == null) return new String[]{"N/A", "secondary"};

            Matcher m = Pattern.compile("\"is_oa\"\\s*:\\s*(true|false)").matcher(json);
            if (m.find()) {
                boolean isOpen = Boolean.parseBoolean(m.group(1));
                // LOG DE SUCESSO: Para confirmar que lemos o JSON corretamente
                System.out.println(">>> [Unpaywall] SUCESSO! DOI: " + doi + " -> Status: " + (isOpen ? "ABERTO" : "FECHADO"));
                return isOpen ? new String[]{"ABERTO", "success"} : new String[]{"FECHADO", "danger"};
            }

        } catch (Exception e) {
            System.err.println("Erro Unpaywall [" + doi + "]: " + e.getMessage());
        }
        return new String[]{"-", "secondary"};
    }

    public static Integer buscarIndiceH(String nomeAutor) {
        if (nomeAutor == null || nomeAutor.trim().isEmpty()) return null;

        try {
            // codifica nome para URL
            String nomeEncoded = URLEncoder.encode(nomeAutor.trim(), StandardCharsets.UTF_8.toString());

            // procura o autor no OpenAlex
            String url = "https://api.openalex.org/authors?search=" + nomeEncoded + "&mailto=extrator@uem.br";

            System.out.println(">>> OpenAlex buscando INDICE H: " + url);
            String json = fazerRequisicao(url, "OpenAlex", nomeAutor);

            if (json == null || !json.contains("\"results\"")) return null;

            // como o openalex devolve uma lista ordenada por relevancia, o primeiro resultado
            // é o mais provavel de ser o nosso autor, então capturamos ele
            Matcher m = Pattern.compile("\"h_index\"\\s*:\\s*(\\d+)").matcher(json);
            if (m.find()) {
                int hIndex = Integer.parseInt(m.group(1));
                System.out.println(">>> [OpenAlex] SUCESSO! Autor: " + nomeAutor + " -> Índice H: " + hIndex);
                return hIndex;
            } else {
                System.out.println(">>> [OpenAlex] VAZIO: O autor '" + nomeAutor + "' não foi encontrado com este nome exato.");
            }
        } catch (Exception e) {
            System.err.println("Erro OpenAlex [" + nomeAutor + "]: " + e.getMessage());
        }
        return null;
    }

    private static String fazerRequisicao(String url, String servico, String identificador) throws Exception {
        // --- SEMÁFORO INTELIGENTE DE TRÁFEGO ---
        if (servico.equals("CrossRef") || servico.equals("")) {
            // Tranca a fila APENAS para este servico específico. um nao atrapalha o outro
            synchronized (servico.intern()) {
                long agora = System.currentTimeMillis();
                long ultima = controleTrafego.getOrDefault(servico, 0L);
                long diferenca = agora - ultima;

                // atraso de 100ms (mais ou menos ~6 requisições por segundo)
                if (diferenca < 300) {
                    Thread.sleep(300 - diferenca);
                }
                controleTrafego.put(servico, System.currentTimeMillis());
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "ExtratorLattes/1.0 (mailto:extrator@uem.br)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // --- ADICIONAMOS ESTE LOG PARA DIAGNÓSTICO ---
        if (response.statusCode() == 404) {
            return null; // o artigo/autor simplesmente não existe na base de dados do serviço.
        }

        if (response.statusCode() == 429) {
            System.err.println("🚨 BLOQUEIO DO " + servico + " (Erro 429)! O servidor pediu para abrandarmos. Identificador: " + identificador);
            return null;
        }

        if (response.statusCode() >= 300) {
            if (!servico.equals("OpenAlex")) { // Oculta erros 404 normais de artigos para não poluir o log
                System.err.println("⚠️ Falha no " + servico + " (Erro " + response.statusCode() + ") para: " + identificador);
            }
            return null;
        }

        return response.body();
    }

    private static String limparEEncodarDoi(String doi, boolean codificarBarra) {
        if (doi == null) return "";
        String limpo = doi.trim().toLowerCase()
                .replace("http://dx.doi.org/", "")
                .replace("https://doi.org/", "")
                .replace("doi:", "")
                .replaceAll("\\s+", "");

        try {
            String encoded = URLEncoder.encode(limpo, StandardCharsets.UTF_8.toString());
            if (!codificarBarra) {
                return encoded.replace("%2F", "/").replace("%2f", "/");
            }
            return encoded;
        } catch (Exception e) {
            return limpo;
        }
    }
}