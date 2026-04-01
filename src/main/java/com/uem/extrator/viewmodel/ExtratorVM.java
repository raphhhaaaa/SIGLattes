package com.uem.extrator.viewmodel;

import ch.qos.logback.classic.selector.servlet.LoggerContextFilter;
import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.ProducaoDAO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.SemanticScholarService;
import com.uem.extrator.service.LattesService;
import com.uem.extrator.service.AuditLogService;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.util.HibernateUtil;
import org.hibernate.Session;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.media.Media;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zk.ui.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Query;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ExtratorVM {

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(ExtratorVM.class);

    // --- LOGIN --- //
    private Usuario usuarioLogado;

    // --- VARIAVÉIS DE ENTRADA --- //
    private String idLattesInput;
    private String cpfInput;
    private String nomeInput;
    private Date dataNascimentoInput;

    // --- CONTROLE --- //
    private Curriculo curriculo = null;
    private String logStatus = null;
    private boolean barraVisivel = false;
    private boolean resumoExpandido = false;
    private boolean processando = false;
    private volatile boolean cancelarLote = false;
    private volatile boolean cancelarAtualizacao = false;
    private int abaSelecionada = 0;
    private String logBatch = "";

    // --- DASHBOARD --- //
    private Long totalCurriculos;
    private String textoDesatualizados = "...";
    private Long consultasHoje;
    private Long totalPesquisadoresUem;

    // --- STATUS CONEXÃO --- //
    private boolean online;
    private String statusTexto;
    private String statusClasse;
    private String statusIcone;

    // Gerenciamento de Threads - limite inicial de 10 simultâneas
    private static final ExecutorService executor = Executors.newFixedThreadPool(ConfigManager.MAX_THREADS_EXTRACAO);

    // Controle de verificação de atualizações
    private boolean verificandoAtualizacoes = false;

    // aba atualizações //
    private List<Curriculo> listaDesatualizados = new ArrayList<>();
    private String logAtualizacao = "";

    private LattesService lattesService = new LattesService();
    private CurriculoDAO curriculoDAO = new CurriculoDAO();
    private ProducaoDAO producaoDAO = new ProducaoDAO();

    @Init
    public void init() {
        this.usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
        if (this.usuarioLogado == null) {
            Executions.sendRedirect("/login.zul");
        }
        atualizarDashboard();
    }

    @Command
    @NotifyChange({"totalCurriculos", "totalPesquisadoresUem", "textoDesatualizados", "consultasHoje", "online", "statusTexto", "statusClasse", "statusIcone"})
    public void atualizarDashboard() {
        // 1. Métricas rápidas
        this.totalCurriculos = curriculoDAO.contarTotalCurriculos();
        this.consultasHoje = curriculoDAO.getConsultasHoje();
        this.totalPesquisadoresUem = curriculoDAO.contarPesquisadoresUem();

        // 2. Ping do CNPq FORA DA THREAD (Síncrono - a tela espera ele terminar para desenhar os cartões)
        this.online = lattesService.testarConexaoCNPq();

        if (this.online) {
            this.statusTexto = "LATTES ONLINE";
            this.statusClasse = "bg-success";
            this.statusIcone = "z-icon-check-circle";
            if (!verificandoAtualizacoes) iniciarVerificacaoDesatualizados();
        } else {
            this.statusTexto = "LATTES OFFLINE";
            this.statusClasse = "bg-danger";
            this.statusIcone = "z-icon-exclamation-triangle";
            this.textoDesatualizados = "Off";
        }

        // Prepara o Server Push para atualizar a tela depois que os gráficos carregarem
        final Desktop desktop = Executions.getCurrent().getDesktop();
        final org.zkoss.zk.ui.Session zkSession = org.zkoss.zk.ui.Sessions.getCurrent();

        // sistema de cache inteligente
        String cacheGraficos = (String) zkSession.getAttribute("CACHE_GRAFICOS_UEM");

        if (cacheGraficos != null && !cacheGraficos.isEmpty()) {
            // se tem cache puxa diretamente sem chamar as threads
            Clients.evalJavaScript(cacheGraficos);
        }

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // Consultas SQL demoradas rodam sem travar a interface
                String scriptGraficos = gerarScriptGraficos();

                // Devolve a resposta ponta para a tela (injeta o JS)
                if (desktop != null && desktop.isAlive()) {
                    Executions.schedule(desktop, event -> {
                        if (!scriptGraficos.isEmpty()) {
                            zkSession.setAttribute("CACHE_GRAFICOS_UEM", scriptGraficos);
                            Clients.evalJavaScript(scriptGraficos);
                        }
                    }, new Event("onReady"));
                }
            } catch (Exception e) {
                logger.error("Erro na ViewModel de Extração", e);
            }
        });
    }

    private String gerarScriptGraficos() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String filtroUEM = " AND (i.siglaInstituicao = 'UEM' OR i.nomeInstituicao LIKE '%Universidade Estadual de Maringá%') ";
            int anoAtual = java.time.Year.now().getValue();
            int anoLimite = anoAtual - 10;

            // Gráfico 1: Evolução
            String hql1 = "SELECT p.ano, COUNT(p.id) " +
                    "FROM Curriculo c JOIN c.producoes p JOIN c.atuacoes a JOIN a.instituicao i " +
                    "WHERE p.ano >= " + anoLimite + filtroUEM +
                    "GROUP BY p.ano " +
                    "ORDER BY p.ano ASC";

            List<Object[]> res1 = session.createQuery(hql1, Object[].class).getResultList();
            String labels1 = "['" + res1.stream().map(r -> r[0].toString()).collect(Collectors.joining("','")) + "']";
            String data1 = "[" + res1.stream().map(r -> r[1].toString()).collect(Collectors.joining(",")) + "]";

            // Gráfico 2: Tipos
            String hql2 = "SELECT p.tipo, COUNT(p.id) " +
                    "FROM Curriculo c JOIN c.producoes p JOIN c.atuacoes a JOIN a.instituicao i " +
                    "WHERE 1=1 " + filtroUEM +
                    "GROUP BY p.tipo " +
                    "ORDER BY COUNT(p.id) DESC";

            List<Object[]> res2 = session.createQuery(hql2, Object[].class).getResultList();
            String labels2 = "['" + res2.stream().map(r -> r[0] != null ? r[0].toString() : "Outros").collect(Collectors.joining("','")) + "']";
            String data2 = "[" + res2.stream().map(r -> r[1].toString()).collect(Collectors.joining(",")) + "]";

            return String.format("setTimeout(function(){ if(typeof renderizarGraficos === 'function') renderizarGraficos(%s, %s, %s, %s); }, 300);", labels1, data1, labels2, data2);

        } catch (Exception e) {
            logger.error("Erro na ViewModel de Extração", e);
            return "";
        }
    }

    private void iniciarVerificacaoDesatualizados() {
        this.verificandoAtualizacoes = true;
        this.textoDesatualizados = "A verificar...";

        // Captura o desktop atual para verificação posterior
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<Object[]> resumos = curriculoDAO.listarResumoParaVerificacao();
                int total = resumos.size();

                if (total == 0) {
                    this.verificandoAtualizacoes = false;
                    atualizarTextoDesatualizados(desktop, "0");
                    return;
                }

                java.util.concurrent.atomic.AtomicInteger concluidos = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger contador = new java.util.concurrent.atomic.AtomicInteger(0);

                for (Object[] resumo : resumos) {
                    executor.submit(() -> {
                        try {
                            // SE A TELA JÁ FECHOU, PARA A THREAD IMEDIATAMENTE
                            if (!desktop.isAlive()) return;

                            // Cria o "Curriculo Fantasma" levíssimo para poupar RAM
                            Curriculo curriculoLeve = new Curriculo();
                            curriculoLeve.setIdLattes((String) resumo[0]);
                            curriculoLeve.setDataAtualizacao((java.util.Date) resumo[1]);

                            if (isDesatualizado(curriculoLeve)) {
                                contador.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Ignora erros isolados de rede
                        } finally {
                            int atual = concluidos.incrementAndGet();

                            // Apenas a última thread atualiza o valor na tela
                            if (atual == total) {
                                this.verificandoAtualizacoes = false;
                                atualizarTextoDesatualizados(desktop, String.valueOf(contador.get()));
                            }
                        }
                    });
                }

            } catch (Exception e) {
                this.verificandoAtualizacoes = false;
                atualizarTextoDesatualizados(desktop, "Erro");
                logger.error("Erro ao verificar desatualizados no Dashboard", e);            }
        });
    }

    private void atualizarTextoDesatualizados(Desktop desktop, String texto) {
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop, event -> {
                    this.textoDesatualizados = texto;
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "textoDesatualizados");
                }, new Event("onUpdate"));
            }
        } catch (Exception e) {}
    }

    @Command
    @NotifyChange({"listaDesatualizados", "processando", "logAtualizacao"})
    public void verificarListaCompleta() {
        this.processando = true;
        this.cancelarAtualizacao = false;
        this.listaDesatualizados.clear();
        this.logAtualizacao = "Iniciando verificação...\n";

        final org.zkoss.zk.ui.Desktop desktop = org.zkoss.zk.ui.Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        java.util.concurrent.Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // 1. Busca no banco de dados agora acontece em background!
                List<Object[]> resumos = curriculoDAO.listarResumoParaVerificacao();
                final int total = resumos.size();

                final java.util.concurrent.atomic.AtomicInteger concluidos = new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger encontrados = new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger progressoVisual = new java.util.concurrent.atomic.AtomicInteger(0);

                final java.util.concurrent.ConcurrentLinkedQueue<String> filaLogs = new java.util.concurrent.ConcurrentLinkedQueue<>();
                final java.util.concurrent.ConcurrentLinkedQueue<Curriculo> filaCurriculos = new java.util.concurrent.ConcurrentLinkedQueue<>();

                this.logAtualizacao = ("Processando...\n");

                // 2. A THREAD MAESTRO (Controla a Interface a cada 1.5s)
                executor.submit(() -> {
                    while (concluidos.get() < total && !cancelarAtualizacao) {
                        try { Thread.sleep(ConfigManager.TEMPO_ATUALIZACAO_UI_MS); } catch (InterruptedException e) {}

                        StringBuilder sb = new StringBuilder();
                        String msg;
                        while ((msg = filaLogs.poll()) != null) { sb.append(msg); }

                        List<Curriculo> novosCurriculos = new ArrayList<>();
                        Curriculo c;
                        while ((c = filaCurriculos.poll()) != null) { novosCurriculos.add(c); }

                        if (sb.length() > 0 || !novosCurriculos.isEmpty()) {
                            if (desktop != null && desktop.isAlive()) {
                                org.zkoss.zk.ui.Executions.schedule(desktop, event -> {
                                    this.logAtualizacao += sb.toString();

                                    if (this.logAtualizacao.length() > ConfigManager.MAX_CARACTERES_LOG_TELA) {
                                        this.logAtualizacao = "...\n[Log truncado para economizar memória]\n" +
                                                this.logAtualizacao.substring(this.logAtualizacao.length() - ConfigManager.MAX_CARACTERES_LOG_TELA);
                                    }

                                    this.listaDesatualizados.addAll(novosCurriculos);
                                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, this, "logAtualizacao");
                                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, this, "listaDesatualizados");
                                }, new org.zkoss.zk.ui.event.Event("onUIUpdate"));
                            }
                        }
                    }
                });

                // 3. AS 30 THREADS TRABALHADORAS
                for (Object[] resumo : resumos) {
                    final String idLattes = (String) resumo[0];
                    final java.util.Date dataAtualizacaoLocal = (java.util.Date) resumo[1];
                    final String nomeCompleto = (String) resumo[2];

                    executor.submit(() -> {
                        if (this.cancelarAtualizacao) {
                            finalizarVerificacaoSegura(desktop, concluidos, total, encontrados, filaLogs);
                            return;
                        }

                        int atual = progressoVisual.incrementAndGet();

                        try {
                            Curriculo curriculoLeve = new Curriculo();
                            curriculoLeve.setIdLattes(idLattes);
                            curriculoLeve.setDataAtualizacao(dataAtualizacaoLocal);
                            curriculoLeve.setNomeCompleto(nomeCompleto);

                            if (isDesatualizado(curriculoLeve)) {
                                filaCurriculos.add(curriculoLeve);
                                encontrados.incrementAndGet();
                                filaLogs.add(String.format("[%d/%d] ⚠️ Desatualizado: %s\n", atual, total, nomeCompleto));
                            } else {
                                filaLogs.add(String.format("[%d/%d] ✅ Atualizado: %s\n", atual, total, nomeCompleto));

                            }
                        } catch (Exception e) {
                            filaLogs.add(String.format("[%d/%d] ❌ Erro ao checar %s\n", atual, total, nomeCompleto));
                        } finally {
                            finalizarVerificacaoSegura(desktop, concluidos, total, encontrados, filaLogs);
                        }
                    });
                }
            } catch (Exception e) {
                logger.error("Erro na ViewModel de Extração", e);
            }
        });
    }

    private void finalizarVerificacaoSegura(org.zkoss.zk.ui.Desktop desktop, java.util.concurrent.atomic.AtomicInteger concluidos, int total, java.util.concurrent.atomic.AtomicInteger encontrados, java.util.concurrent.ConcurrentLinkedQueue<String> filaLogs) {
        int atual = concluidos.incrementAndGet();

        if (atual == total) {
            if (this.cancelarAtualizacao) {
                filaLogs.add("\n🛑 [Processo interrompido pelo usuário]\n");
            }
            filaLogs.add("\n✅ Verificação concluída. " + encontrados.get() + " currículos necessitam de atualização.\n");

            if (desktop != null && desktop.isAlive()) {
                org.zkoss.zk.ui.Executions.schedule(desktop, event -> {
                    finalizarProcesso(desktop, "Verificação finalizada!", true);
                }, new org.zkoss.zk.ui.event.Event("onFinish"));
            }
        }
    }

    @Command
    public void cancelarProcessamentoAtualizacao() {
        this.cancelarAtualizacao = true;
        Clients.showNotification("Cancelando operação... Aguardando conclusão do item atual.", "warning", null, "middle-center", 3000);
    }

    @Command
    @NotifyChange({"listaDesatualizados", "processando", "logAtualizacao"})
    public void atualizarTodosDesatualizados() {
        if (listaDesatualizados.isEmpty()) {
            org.zkoss.zk.ui.util.Clients.showNotification("A lista está vazia.", "warning", null, null, 3000);
            return;
        }

        this.processando = true;
        this.cancelarAtualizacao = false;
        this.logAtualizacao += "\n--------------------------\nIniciando atualização em massa PARALELA...\n";

        final org.zkoss.zk.ui.Desktop desktop = org.zkoss.zk.ui.Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        List<Curriculo> paraAtualizar = new ArrayList<>(this.listaDesatualizados);
        final int total = paraAtualizar.size();

        final AtomicInteger concluidos = new AtomicInteger(0);
        final AtomicInteger sucesso = new AtomicInteger(0);
        final AtomicInteger erro = new AtomicInteger(0);
        final AtomicInteger progressoVisual = new AtomicInteger(0);

        final java.util.concurrent.ConcurrentLinkedQueue<String> filaLogs = new java.util.concurrent.ConcurrentLinkedQueue<>();
        final java.util.concurrent.ConcurrentLinkedQueue<Curriculo> curriculosRemover = new java.util.concurrent.ConcurrentLinkedQueue<>();

        // 1. A THREAD MAESTRO (Controla a UI)
        executor.submit(() -> {
            while (concluidos.get() < total && !cancelarAtualizacao) {
                try { Thread.sleep(ConfigManager.TEMPO_ATUALIZACAO_UI_MS); } catch (InterruptedException e) {}

                StringBuilder sb = new StringBuilder();
                String msg;
                while ((msg = filaLogs.poll()) != null) { sb.append(msg); }

                List<Curriculo> removerLista = new ArrayList<>();
                Curriculo c;
                while ((c = curriculosRemover.poll()) != null) { removerLista.add(c); }

                if (sb.length() > 0 || !removerLista.isEmpty()) {
                    if (desktop != null && desktop.isAlive()) {
                        org.zkoss.zk.ui.Executions.schedule(desktop, event -> {
                            this.logAtualizacao += sb.toString();

                            // LIMITE DE MEMÓRIA (30k caracteres)
                            if (this.logAtualizacao.length() > ConfigManager.MAX_CARACTERES_LOG_TELA) {
                                this.logAtualizacao = "...\n[Log truncado]\n" + this.logAtualizacao.substring(this.logAtualizacao.length() - ConfigManager.MAX_CARACTERES_LOG_TELA);
                            }

                            this.listaDesatualizados.removeAll(removerLista);
                            org.zkoss.bind.BindUtils.postNotifyChange(null, null, this, "logAtualizacao");
                            org.zkoss.bind.BindUtils.postNotifyChange(null, null, this, "listaDesatualizados");
                        }, new org.zkoss.zk.ui.event.Event("onUIUpdate"));
                    }
                }
            }
        });

        // 2. WORKERS (AS 30 THREADS EXECUTORAS DE BACTH)
        for (Curriculo c : paraAtualizar) {
            executor.submit(() -> {
                if (cancelarAtualizacao) {
                    finalizarAtualizacaoLoteSegura(desktop, concluidos, total, sucesso, erro, filaLogs);
                    return;
                }

                int atual = progressoVisual.incrementAndGet();

                try {
                    Curriculo novo = lattesService.getCurriculo(c.getIdLattes());
                    if (novo != null) {
                        curriculoDAO.salvar(novo);
                        curriculosRemover.add(c);
                        sucesso.incrementAndGet();
                        filaLogs.add(String.format("[%d/%d] ✅ Sucesso: %s\n", atual, total, novo.getNomeCompleto()));
                    } else {
                        erro.incrementAndGet();
                        filaLogs.add(String.format("[%d/%d] ❌ Vazio/Erro: %s\n", atual, total, c.getNomeCompleto()));
                    }
                } catch (Exception e) {
                    erro.incrementAndGet();
                    filaLogs.add(String.format("[%d/%d] ❌ Erro (%s): %s\n", atual, total, c.getNomeCompleto(), e.getMessage()));
                } finally {
                    finalizarAtualizacaoLoteSegura(desktop, concluidos, total, sucesso, erro, filaLogs);
                }
            });
        }
    }

    private void finalizarAtualizacaoLoteSegura(org.zkoss.zk.ui.Desktop desktop, java.util.concurrent.atomic.AtomicInteger concluidos, int total, java.util.concurrent.atomic.AtomicInteger sucesso, java.util.concurrent.atomic.AtomicInteger erro, java.util.concurrent.ConcurrentLinkedQueue<String> filaLogs) {
        int atual = concluidos.incrementAndGet();

        if (atual == total) {
            if (this.cancelarAtualizacao) {
                filaLogs.add("\n🛑 [Processo interrompido pelo usuário]\n");
            }
            filaLogs.add("\n🏁 Atualização finalizada! OK: " + sucesso.get() + " | Erros: " + erro.get() + "\n");

            if (desktop != null && desktop.isAlive()) {
                org.zkoss.zk.ui.Executions.schedule(desktop, event -> {
                    finalizarProcesso(desktop, "Processo finalizado.", true);
                    try { atualizarDashboard(); } catch (Exception e) {}
                }, new org.zkoss.zk.ui.event.Event("onFinish"));
            }
        }
    }

    @Command
    @NotifyChange("processando")
    public void atualizarUnico(@BindingParam("item") Curriculo item) {
        this.processando = true;
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        executor.submit(() -> {
            try {
                Curriculo novo = lattesService.getCurriculo(item.getIdLattes());
                if (novo != null) {
                    curriculoDAO.salvar(novo);
                    removerDaLista(desktop, item);
                    finalizarProcesso(desktop, "Atualizado: " + novo.getNomeCompleto(), true);
                    Executions.schedule(desktop, event -> atualizarDashboard(), new Event("onUpdate"));
                } else {
                    finalizarProcesso(desktop, "Erro: CNPq retornou vazio.", false);
                }
            } catch (Exception e) {
                finalizarProcesso(desktop, "Erro: " + e.getMessage(), false);
            }
        });
    }

    @Command
    public void logout() {
        // mata a sesssão
        Sessions.getCurrent().invalidate();
        // manda pro login
        Executions.sendRedirect("/login.zul");
    }

    //  UTILITÁRIOS //

    private boolean isDesatualizado(Curriculo local) {
        try {
            if (local.getIdLattes() == null) return false;
            // pega data remota
            Date dataRemota = lattesService.obterDataAtualizacaoRemota(local.getIdLattes());
            Date dataLocal = local.getDataAtualizacao();

            if (dataRemota != null && dataLocal != null) {
                return zerarHora(dataRemota).after(zerarHora(dataLocal));
            } else return dataRemota != null && dataLocal != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void adicionarNaLista(Desktop desktop, Curriculo c) {
        try {
            Executions.schedule(desktop, event -> {
                this.listaDesatualizados.add(c);
                org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "listaDesatualizados");
            }, new Event("onUpdate"));
        } catch (Exception e) {}
    }

    private void removerDaLista(Desktop desktop, Curriculo c) {
        try {
            Executions.schedule(desktop, event -> {
                this.listaDesatualizados.remove(c);
                org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "listaDesatualizados");
            }, new Event("onUpdate"));
        } catch (Exception e) {}
    }

    private void atualizarLogAtualizacao(Desktop desktop, String msg) {
        try {
            Executions.schedule(desktop, event -> {
                this.logAtualizacao += msg;
                // mantem o log leve (ultimos 5000 caracteres)
                if (this.logAtualizacao.length() > 5000) {
                    this.logAtualizacao = this.logAtualizacao.substring(this.logAtualizacao.length() - 5000);
                }
                org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "logAtualizacao");
            }, new Event("onUpdate"));
        } catch (Exception e) {}
    }

    private Date zerarHora(Date data) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(data);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Command
    @NotifyChange({"logStatus", "barraVisivel", "processando", "idLattesInput", "curriculo", "resumoExpandido"})
    public void buscarPesquisador() {
        // Validação Inteligente baseada na Aba
        if (abaSelecionada == 1) { // Aba CPF
            if (cpfInput == null || cpfInput.trim().isEmpty()) {
                Clients.showNotification("⚠️ Informe o CPF.", Clients.NOTIFICATION_TYPE_WARNING, null, "middle_center", 3000);
                return;
            }
        } else if (abaSelecionada == 2) { // Aba Nome/Data
            if (nomeInput == null || nomeInput.trim().isEmpty() || dataNascimentoInput == null) {
                Clients.showNotification("⚠️ Preencha Nome e Data de Nascimento.", Clients.NOTIFICATION_TYPE_WARNING, null, "middle_center", 3000);
                return;
            }
        }

        this.curriculo = null;
        this.resumoExpandido = false;

        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        this.processando = true;
        atualizarLog(desktop, "Consultando base do CNPq...");

        final String login = (usuarioLogado != null) ? usuarioLogado.getLogin() : "ANONIMO";

        executor.submit(() -> {
            try {
                String idEncontrado = null;
                String tipoBusca = (abaSelecionada == 1) ? "POR_CPF" : "POR_NOME"; // define o tipo da busca
                String termoBusca = (abaSelecionada == 1) ? cpfInput : nomeInput; // Guarda o que foi digitado para logar

                // --- LÓGICA DE ENVIO SEGURA ---
                if (abaSelecionada == 1) {
                    // Busca por CPF
                    String cpfEnvio = cpfInput.trim().replaceAll("[^0-9]", "");
                    // Passamos null, mas o LattesService agora converte para ""
                    idEncontrado = lattesService.buscarIdPorDados(cpfEnvio, null, null);

                } else if (abaSelecionada == 2) {
                    // Busca por Nome
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
                    String dataFormatada = sdf.format(dataNascimentoInput);

                    // remove espaços excedentes
                    String nomeEnvio = nomeInput.trim();

                    // Passamos null no CPF, o LattesService converte para ""
                    idEncontrado = lattesService.buscarIdPorDados(null, nomeEnvio, dataFormatada);
                }

                if (idEncontrado != null && !idEncontrado.isEmpty() && !idEncontrado.equals("0") && idEncontrado.length() == 16) {
                    this.idLattesInput = idEncontrado;
                    atualizarTela(desktop, "idLattesInput");
                    atualizarLog(desktop, "ID Encontrado: " + idEncontrado + ". Baixando...");

                    executarExtracaoInternal(desktop, idEncontrado, tipoBusca);
                } else {
                    // loga falha
                    AuditLogService.registrarExtracao(tipoBusca, login, false, termoBusca, "Pesquisador não localizado na busca");
                    finalizarProcesso(desktop, "❌ Pesquisador não encontrado. Tente verificar o nome exato e a data de nascimento.", false);
                }
            } catch (Exception e) {
                logger.error("Erro na ViewModel de Extração", e);

                // log de erro
                AuditLogService.registrarExtracao("BUSCA_ERRO", login, false, "N/A", "Erro: " + e.getMessage());

                finalizarProcesso(desktop, "❌ Erro na busca: " + e.getMessage(), false);
            }
        });
    }

    @Command
    @NotifyChange({"logStatus", "barraVisivel", "processando", "curriculo", "resumoExpandido"})
    public void buscarPorId() {
        if (idLattesInput == null || idLattesInput.trim().length() != 16) {
            Clients.showNotification("⚠️ ID inválido (16 dígitos).", Clients.NOTIFICATION_TYPE_WARNING, null, "middle_center", 3000);
            return;
        }

        this.curriculo = null;
        this.resumoExpandido = false;
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        this.processando = true;
        this.logStatus = "Iniciando download direto...";
        this.barraVisivel = true;

        executor.submit(() -> executarExtracaoInternal(desktop, idLattesInput, "POR_ID"));
    }

    private void executarExtracaoInternal(Desktop desktop, String id, String tipoOrigem) {
        // captura o usuario logado
        String login = (usuarioLogado != null) ? usuarioLogado.getLogin() : "ANONIMO";

        try {
            atualizarLog(desktop, "Extraindo XML...");
            Curriculo c = lattesService.getCurriculo(id);

            if (c != null) {
                atualizarLog(desktop, "Processando dados...");
                curriculoDAO.salvar(c);
                this.curriculo = c;
                atualizarTela(desktop, "curriculo");

                // loga sucesso
                AuditLogService.registrarExtracao(tipoOrigem, login, true, c.getIdLattes(), c.getNomeCompleto());

                finalizarProcesso(desktop, "Currículo de " + c.getNomeCompleto() + " salvo!", true);
            } else {
                // loga falha
                AuditLogService.registrarExtracao(tipoOrigem, login, false, id, "CNPq retornou vazio/privado");

                finalizarProcesso(desktop, "CNPq retornou vazio ou perfil privado.", false);
            }
        } catch (Exception e) {
            logger.error("Erro na ViewModel de Extração", e);

            // log de erro técnico
            AuditLogService.registrarExtracao(tipoOrigem, login, false, id, "ERRO: " + e.getMessage());

            finalizarProcesso(desktop, "Falha técnica: " + e.getMessage(), false);
        }
    }

    private void atualizarLog(Desktop desktop, String msg) {
        // Atualiza status intermediário
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop, event -> {
                    this.logStatus = msg;
                    this.barraVisivel = true;
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "logStatus");
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "barraVisivel");
                }, new Event("onUpdate"));
            }
        } catch (Exception e) { logger.error("Erro na ViewModel de Extração", e); }
    }

    private void finalizarProcesso(Desktop desktop, String msg, boolean sucesso) {
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop, event -> {
                    // 1. Altera variáveis DENTRO da thread de UI
                    this.processando = false;
                    this.barraVisivel = false; // Desliga a barra
                    this.logStatus = null;     // Limpa o texto

                    // 2. Notifica o ZK
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "processando");
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "barraVisivel");
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "logStatus");

                    // 3. Mostra mensagem flutuante
                    if (sucesso) {
                        String js = "if(typeof toastr !== 'undefined') { toastr.success('" + msg.replace("'", "") + "'); }";
                        Clients.evalJavaScript(js);
                    } else {
                        Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_ERROR, null, "middle_center", 4000);
                    }
                }, new Event("onNotify"));
            }
        } catch (Exception e) { logger.error("Erro na ViewModel de Extração", e); }
    }

    // --- UPLOAD ---
    @Command
    @NotifyChange({"logBatch", "processando"})
    public void carregarArquivo(@BindingParam("media") Media media) {
        if (media == null) return;
        if (!media.getName().toLowerCase().endsWith(".txt")) {
            Clients.showNotification("Envie um arquivo .txt", Clients.NOTIFICATION_TYPE_WARNING, null, null, 3000);
            return;
        }
        this.processando = true;
        this.cancelarLote = false;
        this.logBatch = "📂 Arquivo: " + media.getName() + "\n";

        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        List<String> linhas = lerArquivo(media);

        // remove CPFs ou IDs duplicados presentes no arquivo .txt
        linhas = linhas.stream().distinct().collect(Collectors.toList());

        if (linhas.isEmpty()) {
            this.logBatch += "⚠️ Arquivo vazio/inválido.";
            this.processando = false;
            return;
        }
        this.logBatch += "✅ " + linhas.size() + " linhas. Iniciando...\n----------------\n";
        processarLote(desktop, linhas);
    }

    // UPLOAD MANUAL (XML/ZIP)
    @Command
    @NotifyChange({"logStatus", "barraVisivel", "processando", "curriculo", "resumoExpandido"})
    public void uploadXmlZip(@BindingParam("media") Media media) {
        if (media == null) return;

        // segurança e auditoria
        if (usuarioLogado == null || !usuarioLogado.isAdmin()) {
            Clients.showNotification("Acesso negado: Apenas administradores podem ter acesso a essa funcionalidade.", "error", null, "middle-center", 3000);
            return;
        }

        String nomeArquivo = media.getName().toLowerCase();
        if (!nomeArquivo.endsWith(".xml") && !nomeArquivo.endsWith(".zip")) {
            Clients.showNotification("Envie um arquivo .xml ou .zip", "warning", null, "middle-center", 3000);
            return;
        }

        this.curriculo = null;
        this.resumoExpandido = false;
        final Desktop desktop = Executions.getCurrent().getDesktop();

        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        this.processando = true;
        this.logStatus = "Processando arquivo: " + media.getName() + "...";
        this.barraVisivel = true;

        final String login = usuarioLogado.getLogin();

        // copia os dados do arquivo em memória de forma segura
        final boolean isBinary = media.isBinary();
        final byte[] byteData = isBinary ? media.getByteData() : null;
        final String stringData = !isBinary ? media.getStringData() : null;

        executor.submit(() -> {
            try {
                atualizarLog(desktop, "Descompactando/Lendo arquivo...");
                String xmlConteudo = "";

                // Decodifica o XML ou descompacta o ZIP
                if (nomeArquivo.endsWith(".zip")) {
                    if (!isBinary) throw new Exception("O navegador não enviou o ZIP em formato binário.");

                    java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(byteData);
                    java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(bais);

                    java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
                    if (zipEntry == null) {
                        zis.close();
                        throw new Exception("O arquivo ZIP está vazio ou num formato não suportado.");
                    }

                    // --- PROTEÇÃO CONTRA ZIP BOMB (Limite de 50MB) ---
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count = 0;
                    long totalBytesLidos = 0;
                    long limiteMaximo = 50 * 1024 * 1024; // 50 Megabytes

                    while ((count = zis.read(buffer)) != -1) {
                        totalBytesLidos += count;
                        if (totalBytesLidos > limiteMaximo) {
                            zis.closeEntry();
                            zis.close();
                            throw new SecurityException("Erro de Segurança: Arquivo excede o limite de 50MB.");
                        }
                        baos.write(buffer, 0, count);
                    }

                    // Converte os bytes seguros numa String
                    xmlConteudo = baos.toString(StandardCharsets.ISO_8859_1);

                    zis.closeEntry();
                    zis.close();

                } else {
                    // Se for XML direto (seu código original de leitura de string aqui)
                    xmlConteudo = isBinary ? new String(byteData, StandardCharsets.ISO_8859_1) : stringData;
                }

                atualizarLog(desktop, "Extraindo ID Lattes...");
                String idLattes = "0000000000000000"; // Fallback caso não encontre
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("NUMERO-IDENTIFICADOR=\"(\\d{16})\"").matcher(xmlConteudo);
                if (matcher.find()) {
                    idLattes = matcher.group(1);
                }

                atualizarLog(desktop, "Lendo estrutura do Currículo...");
                com.uem.extrator.service.LattesParser parser = new com.uem.extrator.service.LattesParser();
                Curriculo c = parser.parse(xmlConteudo, idLattes);

                if (c != null) {
                    atualizarLog(desktop, "Salvando dados no banco...");
                    curriculoDAO.salvar(c);

                    // Atualiza a tela local com o currículo
                    Executions.schedule(desktop, event -> {
                        this.curriculo = c;
                        org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "curriculo");
                        atualizarDashboard();
                    }, new Event("onUpdate"));

                    // Audita como UPLOAD MANUAL
                    AuditLogService.registrarExtracao("UPLOAD_MANUAL", login, true, c.getIdLattes(), c.getNomeCompleto());
                    finalizarProcesso(desktop, "Currículo salvo com sucesso via Upload!", true);
                } else {
                    AuditLogService.registrarExtracao("UPLOAD_MANUAL", login, false, idLattes, "Falha na conversão do XML");
                    finalizarProcesso(desktop, "Erro: Não foi possível estruturar o XML.", false);
                }
            } catch (Exception e) {
                logger.error("Erro na ViewModel de Extração", e);
                AuditLogService.registrarExtracao("UPLOAD_MANUAL", login, false, "N/A", "ERRO: " + e.getMessage());
                finalizarProcesso(desktop, "Falha técnica ao processar arquivo: " + e.getMessage(), false);
            }
        });
    }

    @Command
    public void cancelarProcessamentoLote() {
        this.cancelarLote = true;
        Clients.showNotification("Cancelando processamento do lote. O processo irá parar após o currículo atual.", "warning", null, "middle-center", 3000);
    }

    private List<String> lerArquivo(Media media) {
        List<String> lista = new ArrayList<>();
        try {
            BufferedReader br;
            if (media.isBinary()) {
                br = new BufferedReader(new InputStreamReader(media.getStreamData(), StandardCharsets.UTF_8));
            } else {
                br = new BufferedReader(new StringReader(media.getStringData()));
            }
            try (BufferedReader leitor = br) {
                String linha;
                while ((linha = leitor.readLine()) != null) {
                    String limpa = linha.replaceAll("[^0-9]", "");
                    if (!limpa.isEmpty()) lista.add(limpa);
                }
            }
        } catch (Exception e) { logger.error("Erro na ViewModel de Extração", e); }
        return lista;
    }

    private void processarLote(Desktop desktop, List<String> ids) {
        int total = ids.size();

        // Usamos AtomicInteger porque várias threads vão atualizar os números ao mesmo tempo
        AtomicInteger sucesso = new AtomicInteger(0);
        AtomicInteger erro = new AtomicInteger(0);
        AtomicInteger concluidos = new AtomicInteger(0);

        String login = (usuarioLogado != null) ? usuarioLogado.getLogin() : "ANONIMO";

        for (int i = 0; i < total; i++) {
            final int index = i;
            final String dado = ids.get(index);

            executor.submit(() -> {
                if (this.cancelarLote) {
                    atualizarLogBatch(desktop, "\n⚠ PROCESSAMENTO INTERROMPIDO PELO UTILIZADOR!\n");
                    finalizarTarefa(desktop, concluidos, total, sucesso, erro);
                    return;
                }

                try {
                    String idBusca = dado;
                    if (dado.length() == 11) {
                        atualizarLogBatch(desktop, "["+(index+1)+"/"+total+"] CPF "+dado+"...\n");
                        String conv = lattesService.buscarIdPorDados(dado, "", "");
                        if(conv != null && !conv.isEmpty()) {
                            idBusca = conv;
                        } else {
                            erro.incrementAndGet();
                            AuditLogService.registrarExtracao("LOTE_CPF_NAO_ENCONTRADO", login, false, dado, "CNPq não retornou ID Lattes para este CPF");
                            finalizarTarefa(desktop, concluidos, total, sucesso, erro);
                            return;
                        }
                    }

                    if (idBusca.length() != 16) {
                        erro.incrementAndGet();
                        finalizarTarefa(desktop, concluidos, total, sucesso, erro);
                        return;
                    }

                    if (curriculoDAO.existe(idBusca)) {
                        sucesso.incrementAndGet();
                        atualizarLogBatch(desktop, "⏩  ["+(index+1)+"/"+total+"] ID " + idBusca + " já cadastrado. Pulando...\n");
                        AuditLogService.registrarExtracao("LOTE_PULADO", login, true, idBusca, "Currículo já existente no banco (Ignorado)");
                        finalizarTarefa(desktop, concluidos, total, sucesso, erro);
                        return;
                    }

                    atualizarLogBatch(desktop, "["+(index+1)+"/"+total+"] ID "+idBusca+"...");
                    Curriculo c = lattesService.getCurriculo(idBusca);

                    if (c!=null) {
                        curriculoDAO.salvar(c);
                        sucesso.incrementAndGet();
                        atualizarLogBatch(desktop, "✅ Salvo: "+c.getNomeCompleto()+"\n");
                        AuditLogService.registrarExtracao("LOTE", login, true, c.getIdLattes(), c.getNomeCompleto());
                    } else {
                        erro.incrementAndGet();
                        atualizarLogBatch(desktop, "❌ Falha. " + idBusca + "\n");
                        AuditLogService.registrarExtracao("LOTE", login, false, idBusca, "Não encontrado/Vazio no CNPq");
                    }
                } catch(Exception e) {
                    erro.incrementAndGet();
                    atualizarLogBatch(desktop, "❌ Erro: " + e.getMessage() + "\n");
                    AuditLogService.registrarExtracao("LOTE_ERRO", login, false, dado, "Erro técnico: " + e.getMessage());
                }

                finalizarTarefa(desktop, concluidos, total, sucesso, erro);
            });
        }
    }


    private void finalizarTarefa(Desktop desktop, AtomicInteger concluidos, int total, AtomicInteger sucesso, AtomicInteger erro) {
        int fim = concluidos.incrementAndGet();
        if (fim == total) {
            atualizarLogBatch(desktop, "\n🏁 FIM! OK: " + sucesso.get() + " | Erros: " + erro.get() + "\n");
            try {
                if (desktop != null && desktop.isAlive()) {
                    Executions.schedule(desktop, event -> {
                        this.processando = false;
                        org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "processando");
                        Clients.showNotification("Lote concluído com sucesso!", "info", null, null, 3000);
                        atualizarDashboard();
                    }, new Event("onUpdate"));
                }
            } catch (Exception e) {}
        }
    }

    private void atualizarLogBatch(Desktop desktop, String msg) {
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop, event -> {
                    this.logBatch += msg + (msg.endsWith("\n") ? "" : "");
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "logBatch");

                    org.zkoss.zk.ui.util.Clients.evalJavaScript(
                            "setTimeout(function(){ var obj = document.querySelector('textarea.log-box'); if(obj) obj.scrollTop = obj.scrollHeight; }, 50);");
                }, new Event("onUpdate"));
            }
        } catch (Exception e) { logger.error("Erro na ViewModel de Extração", e); }
    }

    @Command
    @NotifyChange("resumoExpandido")
    public void toggleResumo() { this.resumoExpandido = !this.resumoExpandido; }

    @Command
    @NotifyChange({"curriculo", "logStatus", "barraVisivel"})
    public void fecharResultado() {
        this.curriculo = null;
        this.logStatus = null;
        this.barraVisivel = false;
    }

    @Command
    @NotifyChange("*")
    public void limpar() {
        this.curriculo = null;
        this.idLattesInput = null;
        this.cpfInput = null;
        this.nomeInput = null;
        this.dataNascimentoInput = null;
        this.logStatus = null;
        this.barraVisivel = false;
        this.processando = false;
        this.logBatch = "";
        this.listaDesatualizados.clear();
        this.logAtualizacao = "";
    }

    private void atualizarTela(Desktop desktop, String campo) {
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop,
                        event -> org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, campo),
                        new Event("onUpdate"));
            }
        } catch (Exception e) { logger.error("Erro na ViewModel de Extração", e); }
    }

    @Command
    public void verificarMetricas(@BindingParam("artigo") Producao artigo) {
        if (artigo == null) return;

        // 1. Atualiza visualmente para "Carregando..." (Isso funciona pois é a Thread principal)
        artigo.setCitacoes(null);
        artigo.setStatusAcesso("...");
        BindUtils.postNotifyChange(null, null, artigo, "citacoes");
        BindUtils.postNotifyChange(null, null, artigo, "statusAcesso");

        // 2. Captura o Desktop atual antes de entrar na Thread secundária
        final Desktop desktop = Executions.getCurrent().getDesktop();

        // 3. Habilita o Server Push se não estiver ligado
        if (!desktop.isServerPushEnabled()) {
            desktop.enableServerPush(true);
        }

        // 4. Inicia a Thread de trabalho pesado
        executor.submit(() -> {
            try {
                // Vai na internet buscar os dados (Lento)
               SemanticScholarService semanticScholarService = new SemanticScholarService();

               Object[] metricas = semanticScholarService.buscarMetricaUnicas(artigo.getDoi());

               Integer cits = (Integer) metricas[0];
               String[] acesso = (String[]) metricas[1];

                artigo.setCitacoes(cits);
                artigo.setStatusAcesso(acesso[0]);
                artigo.setCorAcesso(acesso[1]);

                producaoDAO.atualizarMetricas(artigo);

                // 5. Agenda a atualização da UI de volta no Desktop do ZK
                Executions.schedule(desktop, new EventListener<Event>() {
                    public void onEvent(Event event) {
                        // Notifica a tela
                        BindUtils.postNotifyChange(null, null, artigo, "citacoes");
                        BindUtils.postNotifyChange(null, null, artigo, "statusAcesso");
                        BindUtils.postNotifyChange(null, null, artigo, "corAcesso");
                    }
                }, new Event("updateUI"));

            } catch (Exception e) {
                logger.error("Erro na ViewModel de Extração", e);
            }
        });
    }

    public static void encerrarThreads() {
        // Envia um sinal de interrupção imediata (shutdownNow) para as 30 threads
        if (executor != null && !executor.isShutdown()) {
            logger.info("Parando o extrator Lattes e limpando threads em background...");
            executor.shutdownNow();
        }
    }

    // Getters
    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo c) { this.curriculo = c; }
    public String getIdLattesInput() { return idLattesInput; }
    public void setIdLattesInput(String id) { this.idLattesInput = id; }
    public String getCpfInput() { return cpfInput; }
    public void setCpfInput(String cpf) { this.cpfInput = cpf; }
    public String getNomeInput() { return nomeInput; }
    public void setNomeInput(String nome) { this.nomeInput = nome; }
    public Date getDataNascimentoInput() { return dataNascimentoInput; }
    public void setDataNascimentoInput(Date data) { this.dataNascimentoInput = data; }
    public String getLogStatus() { return logStatus; }
    public boolean isBarraVisivel() { return barraVisivel; }
    public boolean isProcessando() { return processando; }
    public int getAbaSelecionada() { return abaSelecionada; }
    public void setAbaSelecionada(int aba) { this.abaSelecionada = aba; }
    public boolean isResumoExpandido() { return resumoExpandido; }
    public void setResumoExpandido(boolean resumoExpandido) { this.resumoExpandido = resumoExpandido; }
    public String getLogBatch() { return logBatch; }
    public void setLogBatch(String logBatch) { this.logBatch = logBatch; }
    public Long getTotalCurriculos() { return totalCurriculos; }
    public boolean isOnline() { return online; }
    public String getStatusTexto() { return statusTexto; }
    public String getStatusClasse() { return statusClasse; }
    public String getStatusIcone() { return statusIcone; }
    public String getTextoDesatualizados() { return textoDesatualizados; }
    public Long getConsultasHoje() { return consultasHoje; }
    public List<Curriculo> getListaDesatualizados() { return listaDesatualizados; }
    public String getLogAtualizacao() { return logAtualizacao; }
    public Usuario getUsuarioLogado() { return usuarioLogado; }
    public Long getTotalPesquisadoresUem() { return totalPesquisadoresUem; }
}