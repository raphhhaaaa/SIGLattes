package com.uem.extrator.service;

import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.util.FiltroSimilaridade;
import com.uem.extrator.util.HibernateUtil;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.BindingProvider;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.uem.extrator.service.SemanticScholarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LattesService {

    // instancia logger
    private static final Logger logger = LoggerFactory.getLogger(LattesService.class);

    // Aponta para o Localhost na porta do Túnel (-L)
    // CONSTANTES FIXAS
    private static final String NAMESPACE = "http://ws.servico.repositorio.cnpq.br/";
    private static final String SERVICE_NAME = "WSCurriculo";

    private static Service cachedService = null;
    private static String lastWsdlUrl = null;

    // Configuração global para o pool de conexões HTTP do Java
    // Evita a exaustão de canais do túnel SSH reaproveitando os sockets TCP
    static {
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "100"); // Padrão do Java é 5, o que causa gargalo em multithread
    }

    private CurriculoDAO curriculoDAO = new CurriculoDAO();

    private synchronized Service getService(String wsdlUrl) throws Exception {
        if (cachedService == null || !wsdlUrl.equals(lastWsdlUrl)) {
            logger.info("Inicializando/Atualizando cache do Serviço Lattes (WSDL: {})", wsdlUrl);
            URL url = new URL(wsdlUrl);
            QName qname = new QName(NAMESPACE, SERVICE_NAME);
            cachedService = Service.create(url, qname);
            lastWsdlUrl = wsdlUrl;
        }
        return cachedService;
    }

    // classe interna para transportar resultados/informações
    public static class RelatorioProcessamento {
        public List<String> atualizados = Collections.synchronizedList(new ArrayList<>());
        public List<String> erros = Collections.synchronizedList(new ArrayList<>());
        public List<String> desatualizados = Collections.synchronizedList(new ArrayList<>());
    }

    private ILattesSOAP criarCliente() throws Exception {
        // busca URL dinâmica do configmanager
        ConfigManager config = ConfigManager.getInstance();
        String urlConfigurada = config.getWsdlUrl();
        int timeoutMs = config.getTimeout() * 1000; // converte segundos para ms

        try {
            Service service = getService(urlConfigurada);
            ILattesSOAP port = service.getPort(ILattesSOAP.class);

            BindingProvider bp = (BindingProvider) port;
            Map<String, Object> requestContext = bp.getRequestContext();

            // define o endpoint
            String endpointAdress = urlConfigurada.replace("?wsdl", "");
            requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointAdress);

            // -- CONFIGURAÇÃO DE TIMEOUT --
            requestContext.put("javax.xml.ws.client.connectionTimeout", timeoutMs);
            requestContext.put("javax.xml.ws.client.receiveTimeout", timeoutMs);
            // fallback para implementações em sun/oracle, talvez fique redundante mas melhor do que quebrar caso
            // aconteça (comuns em jdk 8/11)
            requestContext.put("com.sun.xml.internal.ws.connect.timeout", timeoutMs);
            requestContext.put("com.sun.xml.internal.ws.request.timeout", timeoutMs);
            // fallback extra (Garante que funciona em servidores com Apache CXF)
            requestContext.put("javax.xml.ws.client.receiveTimeout", timeoutMs);
            requestContext.put("javax.xml.ws.client.connectionTimeout", timeoutMs);

            // Força o header HTTP Keep-Alive para que o CNPq (e o túnel SSH) não fechem a conexão TCP
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Connection", Collections.singletonList("Keep-Alive"));
            requestContext.put(javax.xml.ws.handler.MessageContext.HTTP_REQUEST_HEADERS, headers);

            return port;
        } catch (Exception e) {
            throw new Exception("Falha ao conectar no Serviço Lattes (" + urlConfigurada + "). Verifique a URL na tela de Configuração ou o Túnel SSH.");
        }
    }

    // --- MÉTODOS DE BUSCA (Extração e ID) ---

    public Curriculo getCurriculo(String idLattes) throws Exception {
        if (idLattes == null) throw new Exception("ID nulo.");
        String idLimpo = idLattes.trim().replaceAll("[^0-9]", "");

        ConfigManager config = ConfigManager.getInstance();
        int maxRetries = config.getRetryAttempts();
        Exception lastError = null;

        // Loop de Tentativas (0 até maxRetries)
        for (int i = 0; i <= maxRetries; i++) {
            try {
                if (i > 0) System.out.println("Tentativa " + (i+1) + " de " + (maxRetries+1) + " para ID " + idLimpo + "...");

                logger.debug("Tentativa {} de {} para ID {} ...", i+1, maxRetries+1, idLimpo);

                ILattesSOAP port = criarCliente();
                byte[] zipBytes = port.getCurriculoCompactado(idLimpo);

                if (zipBytes == null || zipBytes.length == 0) {
                    throw new Exception("CNPq retornou dados vazios ou ID inexistente.");
                }

                String xmlConteudo = descompactarZip(zipBytes);
                // Inicializa os caches de normalização antes do parse
                try (org.hibernate.Session dbSession = HibernateUtil.getSessionFactory().openSession()) {
                    FiltroSimilaridade.inicializarCacheVeiculos(dbSession);
                    FiltroSimilaridade.inicializarCacheTitulos(dbSession);
                    FiltroSimilaridade.inicializarCacheCursos(dbSession);
                }

                LattesParser parser = new LattesParser();
                Curriculo curriculo = parser.parse(xmlConteudo, idLimpo);

                // IMPORTANTE: A data dentro do XML (ddMMyyyy) costuma ter um atraso/cache do CNPq
                // em relação à data real do banco de dados deles. Se usarmos a data do XML,
                // o sistema vai ficar em um loop achando que está sempre desatualizado.
                // Vamos sempre sobrescrever com a data quente do WebService.
                Date dataRealCNPq = obterDataAtualizacaoRemota(idLimpo);
                if (dataRealCNPq != null) {
                    curriculo.setDataAtualizacao(dataRealCNPq);
                }

                List<Producao> producoes = curriculo.getProducoes();

                // enriquece apenas se houver produções do tipo artigo

                boolean possuiArtigos = false;
                if (producoes != null && !producoes.isEmpty()) {
                    possuiArtigos = producoes.stream().anyMatch(p ->
                            p.getTipo() != null &&
                                    p.getTipo().toUpperCase().contains("ARTIGO") &&
                                    p.getDoi() != null &&
                                    !p.getDoi().trim().isEmpty()
                    );

                    if (possuiArtigos) {
                    // enriquece
                    SemanticScholarService semanticScholarService = new SemanticScholarService();
                    semanticScholarService.enriquecerDadosBibliometricos(curriculo, producoes);
                    } else {
                        // retorna sem enriquecer
                        logger.debug("-> API IGNORADA, SEM ARTIGOD: {}", curriculo.getNomeCompleto());
                    }
                }
                return curriculo;

            } catch (Exception e) {
                lastError = e;
                // Se for erro de "ID inexistente", não adianta tentar de novo
                if (e.getMessage() != null && e.getMessage().contains("inexistente")) throw e;

                // Se ainda tiver tentativas, aguarda antes de tentar novamente.
                // 5s dá tempo ao CNPq de se recuperar de lentidão/throttling
                if (i < maxRetries) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }

        String msgUltimoErro = (lastError != null && lastError.getMessage() != null)
                ? lastError.getMessage()
                : (lastError != null ? lastError.getClass().getName() : "erro desconhecido");
        throw new Exception("Falha após " + (maxRetries + 1) + " tentativas. Último erro: " + msgUltimoErro);
    }

    // Metodo interno que chama o SOAP
    public String buscarIdPorDados(String cpf, String nome, String data) throws Exception {
        ILattesSOAP port = criarCliente();

        logger.debug(">>> [SOAP CNPQ] Enviando -> CPF: [{}], NOME: [{}], DATA: [{}]", cpf, nome, data);
        String resposta = port.getIdentificadorCNPq(cpf, nome, data);

        logger.debug(">>> [SOAP CNPQ] Resposta: [{}]", resposta);

        if (resposta == null || resposta.trim().isEmpty()) {
            logger.debug(port.getOcorrenciaCV(resposta));
            throw new Exception("Erro de CNPq: ID vazio ou nulo." + resposta);
        }

        if (resposta.length() != 16) {
            logger.debug(port.getOcorrenciaCV(resposta));
            throw new Exception("Erro de CNPq: " + resposta);
        }
        logger.debug(port.getOcorrenciaCV(resposta));
        return resposta;
    }

    // Metodo chamado pelo ExtratorVM (Recebe o Map de parâmetros)
    public String getIdLattes(Map<String, String> params) throws Exception {

        // CENÁRIO 1: Busca por CPF
        if (params.containsKey("cpf")) {
            String cpf = params.get("cpf");
            // Chama o SOAP passando CPF e anulando os outros
            return buscarIdPorDados(cpf, null, null);
        }

        // CENÁRIO 2: Busca por Nome + Data de Nascimento
        else if (params.containsKey("nome") && params.containsKey("dataNascimento")) {
            String nome = params.get("nome");
            String data = params.get("dataNascimento"); // Formato esperado: dd/MM/yyyy
            // Chama o SOAP passando Nome/Data e anulando o CPF
            return buscarIdPorDados(null, nome, data);
        }

        return null;
    }


    // consulta o CNPq para saber a data da última atualização do currículo na plataforma
    public Date obterDataAtualizacaoRemota(String idLattes) {
        // Tenta até 3 vezes antes de desistir e atirar a exceção
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            try {
                ILattesSOAP port = criarCliente();
                String dataString = port.getDataAtualizacaoCV(idLattes);
                if (dataString != null && !dataString.isEmpty()) {
                    return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse(dataString);
                }
                return null;
            } catch (Exception e) {
                if (tentativa == 3) {
                    // Na última tentativa, se falhar, aí sim ele desiste e deixa o catch do ExtratorVM apanhar
                    throw new RuntimeException("Falha ao buscar data após 3 tentativas: " + e.getMessage());
                }
                // Se falhou mas tem tentativas, dorme meio segundo e tenta de novo
                try { Thread.sleep(500); } catch (InterruptedException ie) {}
            }
        }
        return null;
    }

    public RelatorioProcessamento verificarAtualizacao() {
        logger.info(">>> AUTOMACAO: Iniciando varredura de atualizacoes no CNPq...");
        RelatorioProcessamento relatorio = new RelatorioProcessamento();

        // busca leve
        List<Object[]> listaResumida = curriculoDAO.listarResumoParaVerificacao();
        int total = listaResumida.size();
        logger.info(">>> AUTOMAÇÃO: {} currículos para verificar.", total);

        // cria pool com 30 Threads (30 currículos por vez)
        // atenção: não aumente muito o numero de threads para não ser bloqueado pelo CNPq (Ip ban)
        ExecutorService executor = Executors.newFixedThreadPool(ConfigManager.MAX_THREADS_EXTRACAO);

        // contadores Thread-Safe (atomicos)
        AtomicInteger processados = new AtomicInteger(0);

        for (Object[] dados : listaResumida) {
            executor.submit(() -> {
                String idLattes = (String) dados[0];
                Date dataLocal = (Date) dados[1];
                String nome = (String) dados[2];

                try {
                    // --- Validação Rápida ---
                    Date dataRemotaFull = obterDataAtualizacaoRemota(idLattes);

                    if (dataRemotaFull != null) {
                        Date localSemHora = zerarHoras(dataLocal);
                        Date remotaSemHora = zerarHoras(dataRemotaFull);

                        // Se precisa atualizar
                        if (localSemHora == null || remotaSemHora.after(localSemHora)) {

                            System.out.println(">>> [BAIXANDO] " + nome + " (Nova versão detectada)");

                            // AQUI (e só aqui) gastamos memória para baixar e salvar o XML completo
                            Curriculo novo = getCurriculo(idLattes);
                            curriculoDAO.salvar(novo);

                            // Força limpeza imediata do objeto pesado
                            novo = null;

                            relatorio.atualizados.add(nome);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Erro ao verificar atualizações para {}", nome, e);
                    relatorio.erros.add(nome + " (" + idLattes + ")");
                } finally {
                    int p = processados.incrementAndGet();
                    // Log de progresso a cada 100 currículos para não poluir o console
                    if (p % 100 == 0) {
                        logger.debug(">>> Progresso: {} / {}", p, total);
                    }
                }
            });
        }
        // encerra o recebimento das tarefas e aguarda o fim das atuais
        executor.shutdown();
        try {
            if (!executor.awaitTermination(4, TimeUnit.HOURS)) {
                executor.shutdownNow();
                System.err.println(">>> TIMEOUT: O processo demorou demais e foi forçado a parar.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // limpeza final forçada
//        System.gc();

        logger.info(">>> AUTOMAÇÃO: Varredura concluída. Atualizados: {}, Erros: {}", relatorio.atualizados.size(), relatorio.erros.size());
        if (relatorio.atualizados.size() == 0 && relatorio.erros.size() == 0) {
            System.out.println(">>> AUTOMAÇÃO: Sistema sincronizado!!");
        }
        return relatorio;
    }


    // --- UTILITÁRIOS ---

    private Date zerarHoras(Date data) {
        if (data == null) return null;
        Calendar cal = Calendar.getInstance();
        cal.setTime(data);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private String descompactarZip(byte[] dados) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(dados))) {
            ZipEntry entry = zis.getNextEntry();
            // O XML do Lattes geralmente vem codificado em ISO-8859-1
            if (entry != null) return new String(zis.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        throw new Exception("Arquivo ZIP retornado pelo CNPq é inválido ou vazio.");
    }

    /**
     * Testa a conexão com o SOAP do CNPq
     * @return true se responder, false se der erro/timeout
     */
    public boolean testarConexaoCNPq() {
        try {
            ConfigManager config = ConfigManager.getInstance();
            String urlConfigurada = ConfigManager.getInstance().getWsdlUrl();
            URL url = new URL(urlConfigurada);
            int timeout = config.getTimeout() * 1000;

            // --- CONFIGURAÇÃO DE PROXY (OPCIONAL) ---
            // Se você estiver na rede da UEM e precisar usar o Proxy Institucional (ex: Squid),
            // descomente as linhas abaixo e ajuste o IP/Porta.
            /*
            String proxyHost = "proxy.uem.br"; // Ajuste aqui
            int proxyPort = 8080;              // Ajuste aqui
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            connection = (HttpURLConnection) url.openConnection(proxy);
            */
            // ----------------------------------------

            // PADRÃO: Conexão direta (ou via Túnel Localhost)
            // Se estiver usando o Túnel SSH (-L 8888:...), isso vai funcionar.

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout); // usa o timeout configurado / padrão
            connection.setReadTimeout(timeout);
            connection.connect();

            // Se retornar 200 (OK) ou 405 (Method Not Allowed - comum em SOAP GET), o serviço está vivo.
            int code = connection.getResponseCode();
            return (code == 200 || code == 405);

        } catch (Exception e) {
            logger.error("Falha no teste de conexão CNPq: ", e);
            return false;
        }
    }

    public LattesService getService() { return new LattesService(); }

}