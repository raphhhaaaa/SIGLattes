package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.service.LattesService;
import com.uem.extrator.util.HibernateUtil;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.NotifyChange;
import org.hibernate.Session;
import org.zkoss.zk.ui.util.Clients;

import java.util.List;
import java.util.stream.Collectors;

public class DashboardVM {

    private CurriculoDAO curriculoDAO = new CurriculoDAO();
    private LattesService lattesService = new LattesService();


    // métricas
    private Long totalCurriculos;

    // status do sistema
    private boolean online;
    private String statusTexto;
    private String statusClasse;
    private String statusIcone;

    @Init
    public void init() {
        atualizarDashboard();
    }

    @Command
    @NotifyChange({"totalCurriculos", "online", "statusTexto", "statusClasse", "statusIcone"})
    private void atualizarDashboard() {
        // 1. busca total de curriculos
        this.totalCurriculos = curriculoDAO.contarTotalCurriculos();

        // 2. testa a conexão
        this.online = lattesService.testarConexaoCNPq();

        if (this.online) {
            this.statusTexto = "LATTES ONLINE";
            this.statusClasse = "bg-success";
            this.statusIcone = "z-icon-check-circle";
        } else {
            this.statusTexto = "LATTES OFFLINE";
            this.statusClasse = "bg-danger";
            this.statusIcone = "z-icon-exclamation-triangle";
        }
    }

    // Getters

    public Long getTotalCurriculos() { return totalCurriculos; }
    public boolean getOnline() { return online; }
    public String getStatusTexto() { return statusTexto; }
    public String getStatusClasse() { return statusClasse; }
    public String getStatusIcone() { return statusIcone; }
}
