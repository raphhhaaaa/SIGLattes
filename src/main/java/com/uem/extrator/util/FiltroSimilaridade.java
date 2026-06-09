package com.uem.extrator.util;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.hibernate.Session;

import java.text.Normalizer;
import java.util.*;

public class FiltroSimilaridade {

    public static int distanciaLevenshtein(String s1, String s2) {
        LevenshteinDistance levenshteinDistance = LevenshteinDistance.getDefaultInstance();
        return levenshteinDistance.apply(s1, s2);
    }

    public static float distanciaRelativa(String s1, String s2) {
        int comprimentoS1 = s1.length();
        int comprimentoS2 = s2.length();
        float maiorString = Math.max(comprimentoS1, comprimentoS2);

        float distanciaRelativa = distanciaLevenshtein(s1, s2) / maiorString;

        return distanciaRelativa;
    }

    public static boolean isMesmaInstituicao(String instituicao1, String instituicao2) {
        if (instituicao1 == null || instituicao2 == null) return false;

        String s1 = normalizarInstituicao(instituicao1);
        String s2 = normalizarInstituicao(instituicao2);

        if (s1.equals(s2)) return true;

        // 1. Checa por siglas/abreviações (ex: USP em "Universidade de São Paulo")
        if (isSiglaOuAbreviacao(s1, s2) || isSiglaOuAbreviacao(s2, s1)) return true;

        // 2. Se uma contém a outra (e são grandes o suficiente para não ser falso positivo)
        if (s1.length() > 10 && s2.length() > 10) {
            if (s1.contains(s2) || s2.contains(s1)) return true;
        }

        // 3. Distância de Levenshtein
        int distancia = distanciaLevenshtein(s1, s2);

        // Para strings muito curtas (siglas), exige perfeição
        if (s1.length() <= 4 || s2.length() <= 4) return distancia == 0;

        // Tolerância proporcional ao tamanho da string (máx 3 para nomes longos)
        int limite = Math.min(3, Math.max(1, s1.length() / 15));
        
        boolean resultado = (distancia <= limite);
        if (!resultado && (s1.contains("SÃO") || s2.contains("SAO"))) {
             // Debug log opcional para casos difíceis
             // System.out.println("DEBUG SIMILARIDADE: [" + s1 + "] vs [" + s2 + "] - Dist: " + distancia + " Limite: " + limite);
        }
        return resultado;
    }

    public static boolean isMesmoVeiculo(String veiculo1, String veiculo2) {
        if ((veiculo1 == null || veiculo1.isEmpty()) || (veiculo2 == null || veiculo2.isEmpty())) return false;

        String v1 = normalizarGenerico(veiculo1);
        String v2 = normalizarGenerico(veiculo2);

        if (v1.equals(v2)) return true;

        float distanciaRelativa = distanciaRelativa(v1, v2);

        if (distanciaRelativa < 0.15f && v1.length() > 5) return true;

        return false;
    }

    public static boolean isMesmaProducao(String titulo1, String titulo2) {
        if (titulo1 == null || titulo2 == null) return false;

        String t1 = normalizarGenerico(titulo1);
        String t2 = normalizarGenerico(titulo2);

        if (t1.equals(t2)) return true;

        // Títulos muito curtos: qualquer diferença já é significativa
        if (t1.length() <= 10 || t2.length() <= 10) return false;

        int distancia = distanciaLevenshtein(t1, t2);

        // Tolerância proporcional ao menor título, cap em 8
        // Ex: 40 chars → limite 2 | 80 chars → limite 4 | 160+ chars → limite 8
        // Usar o menor evita que um título longo "absorva" um curto
        int limite = Math.min(8, Math.max(2, Math.min(t1.length(), t2.length()) / 20));

        return distancia <= limite;
    }

    public static boolean isMesmoCurso(String curso1, String curso2) {

        if ((curso1 == null || curso1.isEmpty()) || (curso2 == null || curso2.isEmpty())) return false;

        String c1 = normalizarGenerico(curso1);
        String c2 = normalizarGenerico(curso2);

        if (c1.equals(c2)) return true;

        float distanciaRelativa = distanciaRelativa(c1, c2);

        if (distanciaRelativa < 0.15f && c1.length() >= 5) return true;

        return false;
    }

    // ==========================================
    // CACHE E NORMALIZAÇÃO DE VEÍCULOS
    // ==========================================

    private static final Set<String> cacheVeiculos = new LinkedHashSet<>();
    private static boolean cacheVeiculosInicializado = false;

    /**
     * Carrega os nomes de veículos já existentes no banco como nomes canônicos.
     * Deve ser chamado uma vez antes do parse, quando uma Session estiver disponível.
     */
    public static synchronized void inicializarCacheVeiculos(Session session) {
        if (cacheVeiculosInicializado) return;
        try {
            List<String> existentes = session.createQuery(
                    "SELECT DISTINCT p.nomeVeiculo FROM Producao p WHERE p.nomeVeiculo IS NOT NULL",
                    String.class).list();
            cacheVeiculos.addAll(existentes);
            cacheVeiculosInicializado = true;
        } catch (Exception e) {
            // Se falhar, opera sem cache (sem normalização nessa rodada)
        }
    }

    /**
     * Normaliza o nome de um veículo consultando o cache.
     * Se um nome similar já existir (≥ 85% similaridade), retorna o nome canônico.
     * Caso contrário, registra o novo nome como canônico e o retorna.
     *
     * synchronized: obrigatório — múltiplas threads de extração em lote podem
     * iterar e modificar o cache simultaneamente, causando ConcurrentModificationException.
     */
    public static synchronized String verificaVeiculo(String nomeEntrada) {
        if (nomeEntrada == null || nomeEntrada.trim().isEmpty()) return nomeEntrada;
        String limpo = nomeEntrada.trim();
        for (String canonico : cacheVeiculos) {
            if (isMesmoVeiculo(limpo, canonico)) {
                return canonico;
            }
        }
        cacheVeiculos.add(limpo);
        return limpo;
    }

    /**
     * Invalida o cache de veículos. Deve ser chamado após salvar um currículo
     * para que o próximo parse recarregue os dados atualizados do banco.
     */
    public static synchronized void invalidarCacheVeiculos() {
        cacheVeiculosInicializado = false;
        cacheVeiculos.clear();
    }

    // ==========================================
    // CACHE E NORMALIZAÇÃO DE TÍTULOS DE PRODUÇÃO
    // ==========================================

    /**
     * Índice de títulos canônicos por primeira palavra normalizada.
     * Reduz o espaço de busca Levenshtein de ~55k para ~200 títulos por consulta.
     * Ex: "machine" → ["Machine Learning em Saúde", "Machine Learning aplicado..."]
     */
    private static final Map<String, List<String>> cacheTitulosPorPrefixo = new java.util.HashMap<>();
    private static boolean cacheTitulosInicializado = false;

    /**
     * Carrega todos os títulos existentes no banco, indexando pela primeira palavra
     * normalizada para buscas eficientes. Deve ser chamado antes do parse.
     */
    public static synchronized void inicializarCacheTitulos(Session session) {
        if (cacheTitulosInicializado) return;
        try {
            List<String> existentes = session.createQuery(
                    "SELECT DISTINCT p.titulo FROM Producao p WHERE p.titulo IS NOT NULL",
                    String.class).list();
            for (String titulo : existentes) {
                indexarTitulo(titulo);
            }
            cacheTitulosInicializado = true;
        } catch (Exception e) {
            // Se falhar, opera sem cache nessa rodada
        }
    }

    private static void indexarTitulo(String titulo) {
        String normalizado = normalizarGenerico(titulo);
        if (normalizado.isEmpty()) return;
        String primeiraPalavra = normalizado.split("\\s+")[0];
        cacheTitulosPorPrefixo
                .computeIfAbsent(primeiraPalavra, k -> new ArrayList<>())
                .add(titulo);
    }

    /**
     * Normaliza o título de uma produção consultando o cache indexado.
     * Se um título similar já existir (isMesmaProducao), retorna o canônico —
     * garantindo que o hash gerado em seguida coincida com o do coautor já salvo.
     *
     * synchronized: necessário pelo mesmo motivo que normalizarVeiculo.
     */
    public static synchronized String normalizarTituloProducao(String tituloEntrada) {
        if (tituloEntrada == null || tituloEntrada.trim().isEmpty()) return tituloEntrada;
        String limpo = tituloEntrada.trim();
        String normalizado = normalizarGenerico(limpo);
        if (normalizado.isEmpty()) return limpo;

        // Busca apenas no bucket da primeira palavra — O(~200) em vez de O(55k)
        String primeiraPalavra = normalizado.split("\\s+")[0];
        List<String> candidatos = cacheTitulosPorPrefixo.get(primeiraPalavra);
        if (candidatos != null) {
            for (String canonico : candidatos) {
                if (isMesmaProducao(limpo, canonico)) {
                    return canonico;
                }
            }
        }

        // Novo título — indexa como canônico
        indexarTitulo(limpo);
        return limpo;
    }

    /**
     * Invalida o cache de títulos. Deve ser chamado após salvar um currículo
     * para que o próximo parse recarregue os títulos atualizados do banco.
     */
    public static synchronized void invalidarCacheTitulos() {
        cacheTitulosInicializado = false;
        cacheTitulosPorPrefixo.clear();
    }

    // ==========================================
    // CACHE E NORMALIZAÇÃO DE CURSOS
    // ==========================================

    private static final Set<String> cacheCursos = new LinkedHashSet<>();
    private static boolean cacheCursosInicializado = false;

    /**
     * Carrega os nomes de cursos existentes no banco como nomes canônicos.
     * O volume de cursos distintos é pequeno (~500), então um Set linear é suficiente.
     */
    public static synchronized void inicializarCacheCursos(Session session) {
        if (cacheCursosInicializado) return;
        try {
            List<String> existentes = session.createQuery(
                    "SELECT DISTINCT c.nomeCurso FROM Curso c WHERE c.nomeCurso IS NOT NULL",
                    String.class).list();
            cacheCursos.addAll(existentes);
            cacheCursosInicializado = true;
        } catch (Exception e) {
            // Se falhar, opera sem cache nessa rodada
        }
    }

    /**
     * Normaliza o nome de um curso consultando o cache.
     * Se um nome similar já existir (isMesmoCurso), retorna o canônico.
     * Caso contrário, registra o novo nome como canônico e o retorna.
     */
    public static synchronized String normalizarNomeCurso(String nomeEntrada) {
        if (nomeEntrada == null || nomeEntrada.trim().isEmpty()) return nomeEntrada;
        String limpo = nomeEntrada.trim();
        for (String canonico : cacheCursos) {
            if (isMesmoCurso(limpo, canonico)) {
                return canonico;
            }
        }
        cacheCursos.add(limpo);
        return limpo;
    }

    /**
     * Invalida o cache de cursos. Deve ser chamado após salvar um currículo.
     */
    public static synchronized void invalidarCacheCursos() {
        cacheCursosInicializado = false;
        cacheCursos.clear();
    }

    private static boolean isSiglaOuAbreviacao(String sigla, String nomeCompleto) {
        if (sigla.length() < 2 || sigla.length() > 10) return false;

        
        // Se a sigla aparece dentro do nome completo entre espaços ou parênteses
        if (nomeCompleto.contains(" " + sigla + " ") || 
            nomeCompleto.startsWith(sigla + " ") || 
            nomeCompleto.endsWith(" " + sigla) ||
            nomeCompleto.contains("(" + sigla + ")")) return true;

        String[] palavras = nomeCompleto.split("\\s+");
        
        // 1. Tenta extrair a sigla tradicional (primeiras letras)
        StringBuilder sb = new StringBuilder();
        for (String p : palavras) {
            // Palavras importantes, mesmo curtas, contribuem para a sigla
            if (p.length() >= 3 || p.equals("SAO")) { 
                sb.append(p.charAt(0));
            }
        }
        String siglaGerada = sb.toString();
        
        if (siglaGerada.equals(sigla) || (siglaGerada.length() > 1 && sigla.contains(siglaGerada))) {
            return true;
        }

        // 2. Tenta bater a sigla como uma composição silábica (ex: UNICAMP)
        return isSiglaSilabica(sigla, palavras, 0, 0);
    }

    private static boolean isSiglaSilabica(String sigla, String[] palavras, int indiceSigla, int indicePalavra) {
        // Se consumiu toda a sigla, deu certo
        if (indiceSigla == sigla.length()) return true;
        // Se acabaram as palavras mas a sigla ainda não foi totalmente consumida, falhou nesta rota
        if (indicePalavra == palavras.length) return false;

        // Tentativa 1: Podemos tentar pular a palavra atual (comum em preposições ou palavras que não entram na sigla)
        if (isSiglaSilabica(sigla, palavras, indiceSigla, indicePalavra + 1)) {
            return true;
        }

        // Tentativa 2: Tentar "consumir" partes da sigla usando a palavra atual como prefixo
        String palavraAtual = palavras[indicePalavra];
        
        // Tentamos de 1 até o tamanho da palavra (ou até onde fizer sentido)
        // Normalmente não pegamos mais do que 4 letras de uma palavra para compor sigla silábica
        int maxPrefixo = Math.min(palavraAtual.length(), 4); 

        for (int tamanhoCorte = 1; tamanhoCorte <= maxPrefixo; tamanhoCorte++) {
            String prefixo = palavraAtual.substring(0, tamanhoCorte);
            
            // Verifica se a parte restante da sigla começa com o prefixo que estamos testando
            if (sigla.startsWith(prefixo, indiceSigla)) {
                // Se bateu, avançamos o índice da sigla e vamos para a próxima palavra
                if (isSiglaSilabica(sigla, palavras, indiceSigla + tamanhoCorte, indicePalavra + 1)) {
                    return true;
                }
            } else {
                // Se não bateu com tamanho 'tamanhoCorte', também não vai bater com tamanhos maiores,
                // pois as primeiras letras já divergiram.
                break;
            }
        }

        return false;
    }

    private static String normalizarInstituicao(String texto) {
        String n = removerAcentos(texto.trim().toUpperCase());
        
        // 1. Remove pontuações e caracteres especiais
        n = n.replaceAll("[\\.,\\/\\(\\)\\[\\]\\-_]", " ");

        // 2. Limpeza de prefixos estruturais (Genérico) - SÓ SE NÃO FOR A ÚNICA PALAVRA
        // Evitamos remover se a string for pequena ou o termo for parte essencial
        String[] parts = n.split("\\s+");
        if (parts.length > 2) {
             n = n.replaceAll("\\b(FUNDACAO|EDITORA|ASSOCIACAO|COLEGIO|PROGRAMA|MUSEU|HOSPITAL|LABORATORIO|NUCLEO|SERVICO|PRO REITORIA|DEPARTAMENTO|UNIDADE)\\b", "");
        }
        
        n = n.replaceAll("\\b(DA|DO|DE|DI|DAS|DOS|EM|PARA|COM)\\b", "");
        
        // 3. Limpeza de termos de localidade/unidade (Genérico)
        n = n.replaceAll("\\b(CAMPUS|CAMPUS REGIONAL|POLO|EAD|PR|SC|SP|RJ|MG|RS|BA)\\b", "");

        // 4. Remove espaços extras
        n = n.replaceAll("\\s+", " ").trim();
        
        return n;
    }

    public static String normalizarGenerico(String string) {
        String n = removerAcentos(string.trim().toUpperCase());

        // remove pontuações / caracteres especiais
        n = n.replaceAll("[\\.,\\/\\(\\)\\[\\]\\-_]", " ");

        // 4. Remove espaços extras
        n = n.replaceAll("\\s+", " ").trim();

        return n;
    }

    private static String removerAcentos(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }
}
