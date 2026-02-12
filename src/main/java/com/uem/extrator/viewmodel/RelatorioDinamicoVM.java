package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.InstituicaoDAO;
import com.uem.extrator.dao.ProducaoDAO;
import com.uem.extrator.dao.RelatorioDAO;
import com.uem.extrator.model.Producao;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.ListModelList;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class RelatorioDinamicoVM {

    private RelatorioDAO dao = new RelatorioDAO();
    private InstituicaoDAO instituicaoDAO = new InstituicaoDAO();
    private ProducaoDAO producaoDAO = new ProducaoDAO();
    private List<Producao> listaProducoes = new ArrayList<>();

    // Dados do Gráfico
    private ListModelList<ItemGrafico> dadosGrafico = new ListModelList<>();
    private ListModelList<ItemGrafico> dadosGraficoOrdenado = new ListModelList<>();
    private Long totalGeral = 0L;
    private String tituloGrafico;

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
        // Mapeamento Chave Tela -> Chave Banco
        mapaDeChaves.put("Artigos em Periódicos", "ARTIGO");
        mapaDeChaves.put("Livros e Capítulos", "LIVRO");
        mapaDeChaves.put("Trabalhos em Eventos/Congressos", "EVENTO");
        mapaDeChaves.put("Doutorados Concluídos", "DOUTORADO");
        mapaDeChaves.put("Mestrados Concluídos", "MESTRADO");

        carregarInstituicoes();
        carregarDados();
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

    @Command
    @NotifyChange({"dadosGrafico", "totalGeral", "tituloGrafico", "opcaoSelecionada", "listaProducoes"})
    public void carregarDados() {
        if (opcaoSelecionada == null) {
            Clients.showNotification("Selecione um tipo de produção!", "warning", null, null, 3000);
            return;
        }

        String chave = mapaDeChaves.get(opcaoSelecionada);

        // Título do Gráfico
        this.tituloGrafico = opcaoSelecionada + ("TODAS".equals(instituicaoSelecionada) ? " (Geral)" : " (" + instituicaoSelecionada + ")");

        this.dadosGrafico.clear();
        this.dadosGraficoOrdenado.clear();
        this.listaProducoes.clear();
        this.totalGeral = 0L;

        try {
            // Busca dados AGREGADOS (Count) para o Gráfico
            List<Object[]> resultados = dao.gerarRelatorio(chave, instituicaoSelecionada);

            if (resultados != null && !resultados.isEmpty()) {
                long maxValor = 0;
                // 1. Calcula totais
                for (Object[] row : resultados) {
                    Long qtd = (Long) row[1];
                    if (qtd > maxValor) maxValor = qtd;
                    totalGeral += qtd;
                }
                // 2. Monta objetos do gráfico
                for (Object[] row : resultados) {
                    Integer ano = (Integer) row[0];
                    Long qtd = (Long) row[1];

                    if (ano == null) continue;

                    int percentual = (int) ((qtd * 100) / (maxValor == 0 ? 1 : maxValor));
                    String cor = definirCor(chave);

                    dadosGrafico.add(new ItemGrafico(ano, qtd, percentual, cor));
                }

                this.listaProducoes = producaoDAO.listarPorTipo(chave, instituicaoSelecionada);
                this.dadosGraficoOrdenado.addAll(this.dadosGrafico);
                this.dadosGraficoOrdenado.sort(Comparator.comparingInt(ItemGrafico::getAno));


            } else {
                Clients.showNotification("Nenhum dado encontrado.", "info", null, null, 3000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Clients.showNotification("Erro ao gerar relatório: " + e.getMessage(), "error", null, null, 3000);
            this.tituloGrafico = "Erro: " + e.getMessage();
        }
    }

    // --- 1. EXPORTAÇÃO SIMPLES ---
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

    // --- 2. EXPORTAÇÃO DETALHADA (Apenas Artigos) ---
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

            // Cabeçalho Rico
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

    // --- UTILITÁRIOS ---

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

    private void msgAviso(String msg) {
        org.zkoss.zk.ui.util.Clients.showNotification(msg, "warning", null, null, 2000);
    }
    private void msgErro(String msg) {
        org.zkoss.zk.ui.util.Clients.showNotification(msg, "error", null, null, 3000);
    }

    // Getters e Setters
    public ListModelList<ItemGrafico> getDadosGraficoOrdenado() {
        return dadosGraficoOrdenado;
    }
    public ListModelList<ItemGrafico> getDadosGrafico() { return dadosGrafico; }
    public Long getTotalGeral() { return totalGeral; }
    public String getTituloGrafico() { return tituloGrafico; }
    public ListModelList<String> getListaOpcoes() { return listaOpcoes; }
    public String getOpcaoSelecionada() { return opcaoSelecionada; }
    public void setOpcaoSelecionada(String o) { this.opcaoSelecionada = o; }
    public ListModelList<String> getListaInstituicoes() { return listaInstituicoes; }
    public String getInstituicaoSelecionada() { return instituicaoSelecionada; }
    public void setInstituicaoSelecionada(String i) { this.instituicaoSelecionada = i; }
    public List<Producao> getListaProducoes() { return listaProducoes; }
    public void setListaProducoes(List<Producao> listaProducoes) { this.listaProducoes = listaProducoes; }


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