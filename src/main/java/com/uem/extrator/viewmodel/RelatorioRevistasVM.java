package com.uem.extrator.viewmodel;

import com.uem.extrator.dto.RelatorioRevistaDTO;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.Command;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zul.Filedownload;
import org.zkoss.bind.annotation.NotifyChange;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RelatorioRevistasVM {

    private Usuario usuarioLogado;

    private List<RelatorioRevistaDTO> listaRelatorio; // guarda apenas o que vai ser mostrado na tela
    private List<RelatorioRevistaDTO> listaCompleta; // guarda tudo o que veio do banco

    // filtros
    private String filtroBusca = "";
    private String filtroQualis = "Todos";

    @Init
    public void init() {
        usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");

        carregarDados();
    }

    private void carregarDados() {
        listaRelatorio = new ArrayList<>();
        listaCompleta = new ArrayList<>();

        String sql = "SELECT " +
                // Pega o nome oficial do Qualis. Se não tiver (S/N), pega o que o professor digitou.
                "  COALESCE(MAX(q.nm_revista), MAX(p.nm_veiculo)) AS revista, " +
                "  p.cd_isbn_issn AS issn, " +
                "  q.estrato AS qualis, " +
                "  COUNT(p.id) AS qtd " +
                "FROM PRODUCAO p " +
                "LEFT JOIN QUALIS q ON REPLACE(p.cd_isbn_issn, '-', '') = REPLACE(q.issn, '-', '') " +
                "WHERE p.tp_producao = 'ARTIGO' AND p.cd_isbn_issn IS NOT NULL AND p.cd_isbn_issn != '' " +
                // Agora agrupamos APENAS pelo ISSN e Nota. Tudo que for igual se junta num só!
                "GROUP BY p.cd_isbn_issn, q.estrato " +
                "ORDER BY q.estrato ASC, qtd DESC";

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            NativeQuery<Object[]> query = session.createNativeQuery(sql);
            List<Object[]> resultados = query.list();

            for (Object[] row : resultados) {
                String revista = row[0] != null ? row[0].toString() : "Revista Desconhecida";
                String issn = row[1] != null ? row[1].toString() : "-";
                String qualis = row[2] != null ? row[2].toString() : null;
                Long qtd = ((Number) row[3]).longValue();

                listaCompleta.add(new RelatorioRevistaDTO(revista, issn, qualis, qtd));
            }
            aplicarFiltros();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Command
    @NotifyChange("listaRelatorio")
    public void filtrar() {
        aplicarFiltros();
    }

    @Command
    @NotifyChange({"listaRelatorios", "filtroBusca", "filtroQualis"})
    public void limparFiltros() {
        this.filtroBusca = "";
        this.filtroQualis = "Todos";
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        if (listaCompleta == null) return;

        listaRelatorio = listaCompleta.stream().filter(item -> {
            // 1. Filtro de Busca (Texto)
            boolean bateBusca = true;
            if (filtroBusca != null && !filtroBusca.trim().isEmpty()) {
                String termo = filtroBusca.toLowerCase().trim();
                boolean bateNome = item.getNomeRevista() != null && item.getNomeRevista().toLowerCase().contains(termo);
                boolean bateIssn = item.getIssn() != null && item.getIssn().toLowerCase().contains(termo);
                bateBusca = bateNome || bateIssn;
            }

            // 2. Filtro de Qualis (Combobox)
            boolean bateQualis = true;
            if (filtroQualis != null && !filtroQualis.trim().isEmpty() && !filtroQualis.equalsIgnoreCase("Todos")) {
                // Remove espaços invisíveis que possam vir da tela
                String qualisSelecionado = filtroQualis.trim();
                bateQualis = item.getQualis().equalsIgnoreCase(qualisSelecionado);
            }

            return bateBusca && bateQualis; // Só mostra se passar nos dois testes
        }).collect(Collectors.toList());
    }

    @Command
    public void exportarCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Revista;ISSN;Qualis CAPES;Quantidade de Artigos\n");

        for (RelatorioRevistaDTO item : listaRelatorio) {
            String revistaLimpa = item.getNomeRevista().replace(";", ",").replace("\n", " ");
            csv.append(revistaLimpa).append(";")
                    .append(item.getIssn()).append(";")
                    .append(item.getQualis()).append(";")
                    .append(item.getQuantidadeArtigos()).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.ISO_8859_1);
        Filedownload.save(bytes, "text/csv", "Relatorio_Revistas_Qualis.csv");
    }

    // Getters e Setters

    public List<RelatorioRevistaDTO> getListaRelatorio() {
        return listaRelatorio;
    }
    public String getFiltroBusca() { return filtroBusca; }
    public void setFiltroBusca(String filtroBusca) { this.filtroBusca = filtroBusca; }
    public String getFiltroQualis() { return filtroQualis; }
    public void setFiltroQualis(String filtroQualis) { this.filtroQualis = filtroQualis; }
    public Usuario getUsuarioLogado() {
        return usuarioLogado;
    }
}

