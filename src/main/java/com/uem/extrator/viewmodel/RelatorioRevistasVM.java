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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class RelatorioRevistasVM {

    private Usuario usuarioLogado;
    private List<RelatorioRevistaDTO> listaRelatorio;
    private List<RelatorioRevistaDTO> listaCompleta;
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

        String sql =
            "SELECT " +
            "  COALESCE(MAX(qc.nm_revista), MAX(qh.nm_revista), MAX(p.nm_veiculo)) AS revista, " +
            "  p.cd_isbn_issn AS issn, " +
            "  COALESCE(MAX(qc.estrato), MAX(qh.estrato))                           AS qualis, " +
            "  COUNT(p.id)                                                           AS qtd " +
            "FROM LATTESEXTRATOR.PRODUCAO p " +
            // JOIN 1: busca ISSN sem hífen no banco (ex: coluna armazena '12345678')
            "LEFT JOIN LATTESEXTRATOR.QUALIS qc " +
            "  ON REPLACE(p.cd_isbn_issn, '-', '') = qc.issn " +
            // JOIN 2: busca ISSN com hífen no banco (ex: coluna armazena '1234-5678')
            "LEFT JOIN LATTESEXTRATOR.QUALIS qh " +
            "  ON p.cd_isbn_issn = qh.issn " +
            "WHERE p.tp_producao = 'ARTIGO' " +
            "  AND p.cd_isbn_issn IS NOT NULL " +
            "  AND p.cd_isbn_issn <> '' " +
            "GROUP BY p.cd_isbn_issn " +
            "ORDER BY qtd DESC ";

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            NativeQuery<Object[]> query = session.createNativeQuery(sql);
            List<Object[]> resultados = query.list();

            for (Object[] row : resultados) {
                String revista   = row[0] != null ? row[0].toString() : "Revista Desconhecida";

                // oculta links de DOIs no lugar do nome da revista
                String revLower = revista.toLowerCase();
                if (revLower.contains("http") || revLower.contains("doi.org") ||
                        revLower.contains("www.") || revLower.startsWith("doi:")) {
                    revista = "[Nome não informado - Apenas Link/DOI cadastrado no Lattes]";
                }

                String issn      = row[1] != null ? row[1].toString() : "-";
                String qualis    = row[2] != null ? row[2].toString() : null;
                Long   qtd       = ((Number) row[3]).longValue();
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
    @NotifyChange({"listaRelatorio", "filtroBusca", "filtroQualis"})
    public void limparFiltros() {
        this.filtroBusca = "";
        this.filtroQualis = "Todos";
        aplicarFiltros();
    }

    private void aplicarFiltros() {
        if (listaCompleta == null) return;
        listaRelatorio = listaCompleta.stream().filter(item -> {
            boolean bateBusca = true;
            if (filtroBusca != null && !filtroBusca.trim().isEmpty()) {
                String termo = filtroBusca.toLowerCase().trim();
                bateBusca = (item.getNomeRevista() != null && item.getNomeRevista().toLowerCase().contains(termo))
                         || (item.getIssn() != null && item.getIssn().toLowerCase().contains(termo));
            }
            boolean bateQualis = filtroQualis == null || filtroQualis.trim().isEmpty()
                    || filtroQualis.equalsIgnoreCase("Todos")
                    || item.getQualis().equalsIgnoreCase(filtroQualis.trim());
            return bateBusca && bateQualis;
        }).collect(Collectors.toList());
    }

    @Command
    public void exportarCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Revista;ISSN;Qualis CAPES;Quantidade de Artigos\n");
        for (RelatorioRevistaDTO item : listaRelatorio) {
            csv.append(item.getNomeRevista().replace(";", ",").replace("\n", " ")).append(";")
               .append(item.getIssn()).append(";")
               .append(item.getQualis()).append(";")
               .append(item.getQuantidadeArtigos()).append("\n");
        }
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] dados = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] finalBytes = new byte[bom.length + dados.length];
        System.arraycopy(bom, 0, finalBytes, 0, bom.length);
        System.arraycopy(dados, 0, finalBytes, bom.length, dados.length);

        Filedownload.save(finalBytes, "text/csv", "Relatorio_Revistas_Qualis_" + new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date()));
    }

    // Getters e Setters
    public List<RelatorioRevistaDTO> getListaRelatorio() { return listaRelatorio; }
    public String getFiltroBusca() { return filtroBusca; }
    public void setFiltroBusca(String filtroBusca) { this.filtroBusca = filtroBusca; }
    public String getFiltroQualis() { return filtroQualis; }
    public void setFiltroQualis(String filtroQualis) { this.filtroQualis = filtroQualis; }
    public Usuario getUsuarioLogado() { return usuarioLogado; }
}
