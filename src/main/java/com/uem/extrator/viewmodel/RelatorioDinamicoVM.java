package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.InstituicaoDAO;
import com.uem.extrator.dao.RelatorioDAO;
import com.uem.extrator.model.Producao;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AuditLogService;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.ListModelList;
import org.zkoss.zk.ui.util.Clients;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class RelatorioDinamicoVM {

    private RelatorioDAO dao = new RelatorioDAO();
    private InstituicaoDAO instituicaoDAO = new InstituicaoDAO();

    // Dados dos Gráficos
    private ListModelList<ItemGrafico> dadosGrafico = new ListModelList<>();
    private ListModelList<ItemGrafico> dadosGraficoOrdenado = new ListModelList<>();
    private Long totalGeral = 0L;
    private String tituloGrafico;

    // KPIs do dashboard
    private long totalPesquisadores = 0L;
    private long totalArtigos = 0L;
    private long totalLivros = 0L;
    private long totalEventos = 0L;
    private long totalDoutorados = 0L;
    private long totalMestrados = 0L;

    // --- FILTROS ---
    private ListModelList<String> listaOpcoes = new ListModelList<>(Arrays.asList(
            "Artigos em Periódicos",
            "Livros e Capítulos",
            "Trabalhos em Eventos/Congressos",
            "Doutorados Concluídos",
            "Mestrados Concluídos"
    ));
    private String opcaoSelecionada = "Artigos em Periódicos";

    private ListModelList<String> listaInstituicoes = new ListModelList<>();
    private String instituicaoSelecionada = "TODAS";

    private Map<String, String> mapaDeChaves = new HashMap<>();

    @Init
    public void init() {
        mapaDeChaves.put("Artigos em Periódicos", "ARTIGO");
        mapaDeChaves.put("Livros e Capítulos", "LIVRO");
        mapaDeChaves.put("Trabalhos em Eventos/Congressos", "EVENTO");
        mapaDeChaves.put("Doutorados Concluídos", "DOUTORADO");
        mapaDeChaves.put("Mestrados Concluídos", "MESTRADO");

        carregarInstituicoes();

        // No INIT, carregamos tudo: Gráfico + KPIs
        atualizarKPIs(instituicaoSelecionada);
        atualizarGrafico();
    }

    private void carregarInstituicoes() {
        listaInstituicoes.clear();
        listaInstituicoes.add("TODAS");
        List<Object[]> raw = instituicaoDAO.listarInstituicoesConsolidadas();
        for (Object[] row : raw) {
            String nome = (String) row[0];
            if (nome != null) listaInstituicoes.add(nome.toUpperCase());
        }
        listaInstituicoes.addToSelection("TODAS");
    }

    // Chamado apenas quando a INSTITUIÇÃO muda (Recalcula TUDO)
    @Command
    @NotifyChange({"dadosGrafico", "dadosGraficoOrdenado", "totalGeral", "tituloGrafico", "totalPesquisadores", "totalArtigos", "totalLivros", "totalEventos", "modeloPizzaGeral", "modeloPizzaPesquisadores"})
    public void alterarInstituicao() {
        atualizarKPIs(instituicaoSelecionada);
        atualizarGrafico();
    }

    // Chamado apenas quando o TIPO DE GRÁFICO muda (Não mexe nos KPIs)
    @Command
    @NotifyChange({"dadosGrafico", "dadosGraficoOrdenado", "totalGeral", "tituloGrafico"})
    public void alterarTipoProducao() {
        atualizarGrafico();
    }

    // Método que gera APENAS o gráfico da aba
    private void atualizarGrafico() {
        if (opcaoSelecionada == null) return;

        String chave = mapaDeChaves.get(opcaoSelecionada);
        this.tituloGrafico = opcaoSelecionada + ("TODAS".equals(instituicaoSelecionada) ? " (Geral)" : " (" + instituicaoSelecionada + ")");

        this.dadosGrafico.clear();
        this.dadosGraficoOrdenado.clear();
        this.totalGeral = 0L;

        Usuario user = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
        String autor = (user != null) ? user.getLogin() : "ANONIMO";
        AuditLogService.log("CONSULTA", autor, "Gráfico: " + opcaoSelecionada + " | Inst: " + instituicaoSelecionada);

        try {
            List<Object[]> resultados = dao.gerarRelatorio(chave, instituicaoSelecionada);

            if (resultados != null && !resultados.isEmpty()) {
                long maxValor = 0;
                for (Object[] row : resultados) {
                    Long qtd = (Long) row[1];
                    if (qtd > maxValor) maxValor = qtd;
                    totalGeral += qtd;
                }

                for (Object[] row : resultados) {
                    Integer ano = (Integer) row[0];
                    Long qtd = (Long) row[1];
                    if (ano == null) continue;

                    int percentual = (int) ((qtd * 100) / (maxValor == 0 ? 1 : maxValor));
                    String cor = definirCor(chave);

                    dadosGrafico.add(new ItemGrafico(ano, qtd, percentual, cor));
                }

                this.dadosGraficoOrdenado.addAll(this.dadosGrafico);
                this.dadosGraficoOrdenado.sort(Comparator.comparingInt(ItemGrafico::getAno));
                atualizarGraficosPizza();
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.tituloGrafico = "Erro: " + e.getMessage();
        }
    }

    private void atualizarKPIs(String instituicao) {
        try {
            this.totalPesquisadores = dao.contarTotalPesquisadores(instituicao);
            this.totalArtigos = dao.contarTotalProducao("ARTIGO", instituicao);
            this.totalLivros = dao.contarTotalProducao("LIVRO", instituicao);
            this.totalEventos = dao.contarTotalProducao("EVENTO", instituicao);
            this.totalDoutorados = dao.contarTotalProducao("DOUTORADO", instituicao);
            this.totalMestrados = dao.contarTotalProducao("MESTRADO", instituicao);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void atualizarGraficosPizza() {
        // 1. Montar dados do Gráfico Geral AGORA COM AS 5 CATEGORIAS
        String jsonGeral = String.format("{ \"labels\": [\"Artigos\", \"Livros\", \"Eventos\", \"Doutorados\", \"Mestrados\"], \"data\": [%d, %d, %d, %d, %d] }",
                totalArtigos, totalLivros, totalEventos, totalDoutorados, totalMestrados);

        // 2. Montar dados do Top 5 Pesquisadores (Isto mantém-se igual)
        StringBuilder labelsTop5 = new StringBuilder("[");
        StringBuilder dataTop5 = new StringBuilder("[");

        try {
            String chave = mapaDeChaves.get(opcaoSelecionada);
            List<?> listaDetalhada = dao.listarDadosDetalhados(chave, instituicaoSelecionada);

            if (listaDetalhada != null && !listaDetalhada.isEmpty()) {
                Map<String, Integer> contagem = new HashMap<>();

                for (Object obj : listaDetalhada) {
                    String nome = null;

                    // Verifica se o item é uma Producao (Artigo, Livro) ou Formacao (Mestrado, Doutorado)
                    if (obj instanceof com.uem.extrator.model.Producao) {
                        nome = ((com.uem.extrator.model.Producao) obj).getCurriculo().getNomeCompleto();
                    } else if (obj instanceof com.uem.extrator.model.Formacao) {
                        nome = ((com.uem.extrator.model.Formacao) obj).getCurriculo().getNomeCompleto();
                    }

                    if (nome != null) {
                        String[] partes = nome.split(" ");
                        String nomeCurto = partes[0] + (partes.length > 1 ? " " + partes[partes.length - 1] : "");
                        contagem.put(nomeCurto, contagem.getOrDefault(nomeCurto, 0) + 1);
                    }
                }

                // Ordena e pega os top 5
                List<Map.Entry<String, Integer>> top5 = contagem.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(5)
                        .collect(Collectors.toList());

                for (int i = 0; i < top5.size(); i++) {
                    labelsTop5.append("\"").append(top5.get(i).getKey()).append("\"");
                    dataTop5.append(top5.get(i).getValue());
                    if (i < top5.size() - 1) {
                        labelsTop5.append(",");
                        dataTop5.append(",");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        labelsTop5.append("]");
        dataTop5.append("]");

        String jsonTop5 = "{ \"labels\": " + labelsTop5.toString() + ", \"data\": " + dataTop5.toString() + " }";

        Clients.evalJavaScript("desenharGraficos(" + jsonGeral + ", " + jsonTop5 + ");");
    }

    // --- EXPORTAÇÕES ---
    @Command
    public void exportarCsvSimples() {
        if (dadosGrafico.isEmpty()) {
            msgAviso("Sem dados para exportar.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Ano;Quantidade;Metrica;Instituicao\n");

        for (ItemGrafico item : dadosGrafico) {
            sb.append(item.getAno()).append(";")
                    .append(item.getQuantidade()).append(";")
                    .append(opcaoSelecionada).append(";")
                    .append(instituicaoSelecionada).append("\n");
        }

        baixarCsv(sb, "RESUMO");
    }

    @Command
    public void exportarCsvDetalhado() {
        try {
            String chave = mapaDeChaves.get(opcaoSelecionada);
            List<?> listaDetalhada = dao.listarDadosDetalhados(chave, instituicaoSelecionada);

            if (listaDetalhada == null || listaDetalhada.isEmpty()) {
                msgAviso("Sem dados detalhados disponíveis.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Titulo;Ano;Pesquisador;Veículo;Citações;Acesso;DOI\n");

            for (Object obj : listaDetalhada) {
                if (obj instanceof Producao) {
                    Producao p = (Producao) obj;
                    sb.append(limparCsv(p.getTitulo())).append(";");
                    sb.append(p.getAno()).append(";");
                    sb.append(limparCsv(p.getCurriculo().getNomeCompleto())).append(";");
                    sb.append(limparCsv(p.getNomeVeiculo())).append(";");
                    sb.append(p.getCitacoes() == null ? "0" : p.getCitacoes()).append(";");
                    sb.append(p.getStatusAcesso() == null ? "-" : p.getStatusAcesso()).append(";");
                    sb.append(p.getDoi() == null ? "" : p.getDoi()).append("\n");
                }
            }

            baixarCsv(sb, "DETALHADO");

        } catch (Exception e) {
            e.printStackTrace();
            msgErro("Erro ao exportar detalhado: " + e.getMessage());
        }
    }

    private void baixarCsv(StringBuilder conteudo, String sufixo) {
        String nomeArquivo = "RELATORIO_" + sufixo + "_" +
                removerAcentos(opcaoSelecionada).replaceAll("[^a-zA-Z0-9]", "") + "_" +
                new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()) + ".csv";

        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}; // BOM para Excel
        byte[] dados = conteudo.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + dados.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(dados, 0, finalBytes, bom.length, dados.length);

        Filedownload.save(finalBytes, "text/csv", nomeArquivo);
    }

    private String definirCor(String chave) {
        switch (chave) {
            case "LIVRO": return "bg-success";
            case "DOUTORADO": return "bg-warning";
            case "EVENTO": return "bg-info";
            case "MESTRADO": return "bg-danger";
            default: return "bg-primary";
        }
    }

    private String limparCsv(String texto) {
        return texto == null ? "" : texto.replace(";", ",").replace("\n", " ").trim();
    }

    private String removerAcentos(String str) {
        if (str == null) return "";
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
                .matcher(Normalizer.normalize(str, Normalizer.Form.NFD))
                .replaceAll("");
    }

    private void msgAviso(String msg) { Clients.showNotification(msg, "warning", null, null, 2000); }
    private void msgErro(String msg) { Clients.showNotification(msg, "error", null, null, 3000); }

    // Getters e Setters
    public ListModelList<ItemGrafico> getDadosGraficoOrdenado() { return dadosGraficoOrdenado; }
    public ListModelList<ItemGrafico> getDadosGrafico() { return dadosGrafico; }
    public Long getTotalGeral() { return totalGeral; }
    public String getTituloGrafico() { return tituloGrafico; }
    public ListModelList<String> getListaOpcoes() { return listaOpcoes; }
    public String getOpcaoSelecionada() { return opcaoSelecionada; }
    public void setOpcaoSelecionada(String o) { this.opcaoSelecionada = o; }
    public ListModelList<String> getListaInstituicoes() { return listaInstituicoes; }
    public String getInstituicaoSelecionada() { return instituicaoSelecionada; }
    public void setInstituicaoSelecionada(String i) { this.instituicaoSelecionada = i; }
    public long getTotalPesquisadores() { return totalPesquisadores; }
    public long getTotalArtigos() { return totalArtigos; }
    public long getTotalLivros() { return totalLivros; }
    public long getTotalEventos() { return totalEventos; }


    public static class ItemGrafico {
        private Integer ano;
        private Long quantidade;
        private Integer percentual;
        private String cor;
        public ItemGrafico(Integer a, Long q, Integer p, String c) { ano=a; quantidade=q; percentual=p; cor=c; }
        public Integer getAno() { return ano; }
        public Long getQuantidade() { return quantidade; }
        public Integer getPercentual() { return percentual; }
        public String getCor() { return cor; }
    }
}