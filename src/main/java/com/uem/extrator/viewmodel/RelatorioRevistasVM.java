package com.uem.extrator.viewmodel;

import com.uem.extrator.dto.RelatorioRevistaDTO;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.Command;
import org.zkoss.zul.Filedownload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RelatorioRevistasVM {

    private List<RelatorioRevistaDTO> listaRelatorio;

    @Init
    public void init() {
        carregarDados();
    }

    private void carregarDados() {
        listaRelatorio = new ArrayList<>();

        String sql = "SELECT " +
                "  p.nm_veiculo AS revista, " +
                "  p.cd_isbn_issn AS issn, " +
                "  q.estrato AS qualis, " +
                "  COUNT(p.id) AS qtd " +
                "FROM PRODUCAO p " +
                "LEFT JOIN QUALIS q ON REPLACE(p.cd_isbn_issn, '-', '') = REPLACE(q.issn, '-', '') " +
                "WHERE p.tp_producao = 'ARTIGO' AND p.cd_isbn_issn IS NOT NULL AND p.cd_isbn_issn != '' " +
                "GROUP BY p.nm_veiculo, p.cd_isbn_issn, q.estrato " +
                "ORDER BY q.estrato ASC, qtd DESC";

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            NativeQuery<Object[]> query = session.createNativeQuery(sql);
            List<Object[]> resultados = query.list();

            for (Object[] row : resultados) {
                String revista = row[0] != null ? row[0].toString() : "Revista Desconhecida";
                String issn = row[1] != null ? row[1].toString() : "-";
                String qualis = row[2] != null ? row[2].toString() : null;
                Long qtd = ((Number) row[3]).longValue();

                listaRelatorio.add(new RelatorioRevistaDTO(revista, issn, qualis, qtd));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public List<RelatorioRevistaDTO> getListaRelatorio() {
        return listaRelatorio;
    }
}

