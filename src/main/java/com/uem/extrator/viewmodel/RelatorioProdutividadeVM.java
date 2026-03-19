package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.InstituicaoDAO;
import com.uem.extrator.dao.QualisDAO;
import com.uem.extrator.dto.RelatorioProdutividadeDTO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;
import com.uem.extrator.model.Qualis;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zul.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RelatorioProdutividadeVM {

    private Usuario usuarioLogado;

    private List<String> instituicoes;
    private String instituicaoSelecionada;
    private List<RelatorioProdutividadeDTO> listaProdutividade;

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

        instituicoes = new ArrayList<>();
        for (Object[] row : resultados) {
            if (row[0] != null) {
                instituicoes.add((String) row[0]);
            }
        }
    }

    @Command
    @NotifyChange({"listaProdutividade", "primeiroLugar", "segundoLugar", "terceiroLugar"})
    public void pesquisar() {
        listaProdutividade.clear();
        primeiroLugar = null;
        segundoLugar = null;
        terceiroLugar = null;

        if (instituicaoSelecionada == null || instituicaoSelecionada.trim().isEmpty()) {
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {

            // O HQL com COALESCE para tratar os NULLs perfeitamente
            String hql = "SELECT c.idLattes, c.nomeCompleto, c.indiceH, " +
                    "COUNT(p.id), " +
                    "SUM(p.citacoes), " +
                    "(SUM(CASE WHEN p.statusAcesso = 'ABERTO' THEN 1.0 ELSE 0.0 END) * 100.0) / NULLIF(COUNT(p.id), 0) " +
                    "FROM Curriculo c " +
                    "LEFT JOIN c.producoes p " +
                    "WHERE EXISTS (SELECT 1 FROM Atuacao a WHERE a.curriculo.idLattes = c.idLattes AND a.instituicao.nomeInstituicao = :instituicaoNome) " +
                    "GROUP BY c.idLattes, c.nomeCompleto, c.indiceH " +
                    "ORDER BY COALESCE(c.indiceH, 0) DESC, COALESCE(SUM(p.citacoes), 0) DESC";

            Query<Object[]> query = session.createQuery(hql, Object[].class);
            query.setParameter("instituicaoNome", instituicaoSelecionada);

            List<Object[]> resultados = query.getResultList();

            for (Object[] row : resultados) {
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
        }
    }

    @Command
    @NotifyChange({"instituicaoSelecionada", "listaProdutividade", "primeiroLugar", "segundoLugar", "terceiroLugar"})
    public void limparFiltros() {
        instituicaoSelecionada = null;
        listaProdutividade.clear();
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
}