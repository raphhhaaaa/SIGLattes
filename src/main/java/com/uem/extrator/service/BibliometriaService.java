package com.uem.extrator.service;

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

    public static Integer buscarCitacoes(String doi) {
        if (doi == null || doi.trim().isEmpty()) return null;

        try {
            String doiEncoded = limparEEncodarDoi(doi, true);
            String url = "https://api.crossref.org/works/" + doiEncoded + "?mailto=extrator@uem.br";

            String json = fazerRequisicao(url, "CrossRef", doi);
            if (json == null) return null;

            Matcher m = Pattern.compile("\"is-referenced-by-count\"\\s*:\\s*(\\d+)").matcher(json);
            if (m.find()) return Integer.parseInt(m.group(1));

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

    private static String fazerRequisicao(String url, String servico, String doiOriginal) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "ExtratorLattes/1.0 (mailto:extrator@uem.br)")
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) return null;
        if (response.statusCode() >= 300) return null;

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