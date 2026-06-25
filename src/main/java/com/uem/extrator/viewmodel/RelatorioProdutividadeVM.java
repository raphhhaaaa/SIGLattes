package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.InstituicaoDAO;
import com.uem.extrator.dao.ProducaoDAO;
import com.uem.extrator.dto.RelatorioProdutividadeDTO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Window;

import java.text.SimpleDateFormat;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class RelatorioProdutividadeVM {

    private static final Logger logger = LoggerFactory.getLogger(RelatorioProdutividadeVM.class);


    private Usuario usuarioLogado;

    private List<String> instituicoes;
    private String instituicaoSelecionada;
    private List<RelatorioProdutividadeDTO> listaProdutividade;
    private String filtroNome;
    private List<RelatorioProdutividadeDTO> listaProdutividadeOriginal;

    // Variáveis individuais para o Pódio (Resolve o erro do ZK)
    private RelatorioProdutividadeDTO primeiroLugar;
    private RelatorioProdutividadeDTO segundoLugar;
    private RelatorioProdutividadeDTO terceiroLugar;

    private CurriculoDAO curriculoDAO = new CurriculoDAO();
    private Curriculo curriculo;
    private boolean resumoExpandido = false;

    @Init
    public void init() {
        usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
        listaProdutividade = new ArrayList<>();

        InstituicaoDAO instituicaoDAO = new InstituicaoDAO();
        List<Object[]> resultados = instituicaoDAO.listarInstituicoesConsolidadas();

        this.listaProdutividadeOriginal = new ArrayList<>(this.listaProdutividade);
        this.filtroNome = "";

        instituicoes = new ArrayList<>();
        for (Object[] row : resultados) {
            if (row[0] != null) {
                instituicoes.add((String) row[0]);
            }
        }
    }

    @Command
    @NotifyChange({"listaProdutividade", "primeiroLugar", "segundoLugar", "terceiroLugar"})
    public void pesquisarInstituicao() {
        listaProdutividade.clear();
        primeiroLugar = null;
        segundoLugar = null;
        terceiroLugar = null;

        if (instituicaoSelecionada == null || instituicaoSelecionada.trim().isEmpty()) {
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            logger.info("Iniciando conexão com o banco (Bypass Relacional)...");

            // 1. Pega apenas os IDs rapidamente, sem JOINs agressivos
            String hqlIds = "SELECT DISTINCT a.curriculo.idLattes FROM Atuacao a WHERE UPPER(a.instituicao.nomeInstituicao) = UPPER(:instituicaoNome)";
            List<String> idsFiltrados = session.createQuery(hqlIds, String.class)
                    .setParameter("instituicaoNome", instituicaoSelecionada)
                    .getResultList();

            if (idsFiltrados.isEmpty()) {
                logger.warn("Nenhum pesquisador encontrado para a instituição.");
                return;
            }

            List<Object[]> resultadosBrutos = new ArrayList<>();
            int batchSize = 500;

            // 2. Busca os dados matemáticos em lotes para evitar erro na cláusula IN
            String hqlMatematica = "SELECT c.idLattes, c.nomeCompleto, c.indiceH, " +
                    "COUNT(p.id), " +
                    "SUM(p.citacoes), " +
                    "(SUM(CASE WHEN p.statusAcesso = 'ABERTO' THEN 1.0 ELSE 0.0 END) * 100.0) / NULLIF(COUNT(p.id), 0) " +
                    "FROM Curriculo c " +
                    "LEFT JOIN c.producoes p " +
                    "WHERE c.idLattes IN (:idsLattes) " +
                    "GROUP BY c.idLattes, c.nomeCompleto, c.indiceH";

            for (int i = 0; i < idsFiltrados.size(); i += batchSize) {
                List<String> subList = idsFiltrados.subList(i, Math.min(i + batchSize, idsFiltrados.size()));
                List<Object[]> res = session.createQuery(hqlMatematica, Object[].class)
                        .setParameterList("idsLattes", subList)
                        .getResultList();
                resultadosBrutos.addAll(res);
            }

            // 3. Ordenação Feita no Java (Java-Side Join O(N log N))
            resultadosBrutos.sort((row1, row2) -> {
                Integer h1 = row1[2] != null ? ((Number) row1[2]).intValue() : 0;
                Integer h2 = row2[2] != null ? ((Number) row2[2]).intValue() : 0;
                if (!h2.equals(h1)) {
                    return h2.compareTo(h1); // DESC por indiceH
                }
                Integer c1 = row1[4] != null ? ((Number) row1[4]).intValue() : 0;
                Integer c2 = row2[4] != null ? ((Number) row2[4]).intValue() : 0;
                return c2.compareTo(c1);     // DESC por citacoes
            });

            logger.info("Métricas calculadas no Java com sucesso. Total: " + resultadosBrutos.size());

            for (Object[] row : resultadosBrutos) {
                String idLattes = (String) row[0];
                String nome = (String) row[1];
                Integer indiceH = row[2] != null ? ((Number) row[2]).intValue() : 0;
                Integer totalArtigos = row[3] != null ? ((Number) row[3]).intValue() : 0;
                Integer totalCitacoes = row[4] != null ? ((Number) row[4]).intValue() : 0;
                Double taxa = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

                listaProdutividade.add(new RelatorioProdutividadeDTO(idLattes, nome, indiceH, totalArtigos, totalCitacoes, taxa));
            }

            // Atribui diretamente às variáveis do pódio com segurança
            if (listaProdutividade.size() > 0) primeiroLugar = listaProdutividade.get(0);
            if (listaProdutividade.size() > 1) segundoLugar = listaProdutividade.get(1);
            if (listaProdutividade.size() > 2) terceiroLugar = listaProdutividade.get(2);

            this.listaProdutividadeOriginal = new ArrayList<>(this.listaProdutividade);
            this.filtroNome = "";
        } catch (Exception e) {
            logger.error("Erro ao executar pesquisa: ", e);
        }
    }

    @Command
    @NotifyChange("listaProdutividade")
    public void filtrar() {
        if (listaProdutividade == null) return;

        if (filtroNome == null || filtroNome.trim().isEmpty()) {
            // se filtro estiver vazio -> lista completa
            listaProdutividade = new ArrayList<>(listaProdutividadeOriginal);
        } else {
            String termo = filtroNome.toLowerCase().trim();
            // filtra a lista original baseando-se no nome do pesquisador
            listaProdutividade = listaProdutividadeOriginal.stream()
                    .filter(p -> p.getNomePesquisador() != null && p.getNomePesquisador().toLowerCase().contains(termo))
                    .collect(Collectors.toList());

            if (listaProdutividade == null || listaProdutividade.isEmpty()) {
                Clients.showNotification("Não há nenhuma correspondência com o termo pesquisado.", "error", null, null, 3000);
                listaProdutividade = new ArrayList<>(listaProdutividadeOriginal);
            }
        }
    }

    @Command
    @NotifyChange({"instituicaoSelecionada", "listaProdutividade", "primeiroLugar", "segundoLugar", "terceiroLugar"})
    public void limparFiltros() {
        instituicaoSelecionada = null;
        listaProdutividade.clear();

        if (listaProdutividadeOriginal != null) {
            listaProdutividadeOriginal.clear();
        }
        filtroNome = "";

        primeiroLugar = null;
        segundoLugar = null;
        terceiroLugar = null;
    }

    @Command
    public void visualizarCurriculo(@BindingParam("id") String id) {
        this.curriculo = curriculoDAO.buscarComDetalhes(id);
        this.resumoExpandido = false;

        if (this.curriculo != null) {
            Map<String, Object> args = new HashMap<>();
            args.put("vm", this);
            Window win = (Window) Executions.createComponents("/paginas/modalDetalhes.zul", null, args);
            win.doModal();
        } else {
            org.zkoss.zk.ui.util.Clients.showNotification("Erro ao carregar detalhes", "error", null, null, 2000);
        }
    }

    @Command
    public void exportarCSV() {
        if (listaProdutividade == null || listaProdutividade.isEmpty()) {
            Clients.showNotification("Sem dados para exportar!", "info", null, null, 4000);
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append("ID Lattes;Nome do Pesquisador;Índice H;Total de Artigos;Total de Citações;Taxa Acesso Aberto (%)\n");

        for (RelatorioProdutividadeDTO item : listaProdutividade) {
            String noemSeguro = item.getNomePesquisador() == null ? "" : item.getNomePesquisador();
            if (noemSeguro.contains(";") || noemSeguro.contains("\"")) {
                noemSeguro = "\"" + noemSeguro.replace("\"", "\"\"") + "\"";
            }

            csv.append("=\"").append(item.getCurriculoId()).append("\";")
                    .append(noemSeguro).append(";")
                    .append(item.getIndiceH()).append(";")
                    .append(item.getTotalArtigos()).append(";")
                    .append(item.getTotalCitacoes()).append(";")
                    .append(item.getTaxaAcessoAbertoFormatada()).append("\n");
        }

        byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
        byte[] dados = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + dados.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(dados, 0, finalBytes, bom.length, dados.length);

        Filedownload.save(finalBytes, "text/csv", "Relatorio_Produtividade_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()));
    }

    @Command
    @NotifyChange("resumoExpandido")
    public void toggleResumo() { this.resumoExpandido = !this.resumoExpandido; }

    @Command
    public void fecharResultado() {}

    // ================= GETTERS E SETTERS =================
    public Usuario getUsuarioLogado() { return usuarioLogado; }
    public List<String> getInstituicoes() { return instituicoes; }
    public String getInstituicaoSelecionada() { return instituicaoSelecionada; }
    public void setInstituicaoSelecionada(String instituicaoSelecionada) { this.instituicaoSelecionada = instituicaoSelecionada; }
    public List<RelatorioProdutividadeDTO> getListaProdutividade() { return listaProdutividade; }

    public RelatorioProdutividadeDTO getPrimeiroLugar() { return primeiroLugar; }
    public RelatorioProdutividadeDTO getSegundoLugar() { return segundoLugar; }
    public RelatorioProdutividadeDTO getTerceiroLugar() { return terceiroLugar; }

    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo curriculo) { this.curriculo = curriculo; }
    public boolean isResumoExpandido() { return resumoExpandido; }
    public void setResumoExpandido(boolean resumoExpandido) { this.resumoExpandido = resumoExpandido; }

    public String getFiltroNome() { return filtroNome; }
    public void setFiltroNome(String filtroNome) { this.filtroNome = filtroNome; }
}