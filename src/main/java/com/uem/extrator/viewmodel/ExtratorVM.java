package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.CurriculoDAO;
import com.uem.extrator.dao.ProducaoDAO;
import com.uem.extrator.model.Curriculo;
import com.uem.extrator.model.Producao;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.BibliometriaService;
import com.uem.extrator.service.LattesService;
import com.uem.extrator.service.AuditLogService;
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


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ExtratorVM {

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
    private int abaSelecionada = 0;
    private String logBatch = "";

    // --- DASHBOARD --- //
    private Long totalCurriculos;
    private String textoDesatualizados = "...";
    private Long consultasHoje;

    // --- STATUS CONEXÃO --- //
    private boolean online;
    private String statusTexto;
    private String statusClasse;
    private String statusIcone;

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
    @NotifyChange({"totalCurriculos", "textoDesatualizados", "consultasHoje", "online", "statusTexto", "statusClasse", "statusIcone"})
    public void atualizarDashboard() {
        // 1. Métricas rápidas
        this.totalCurriculos = curriculoDAO.contarTotalCurriculos();
        this.consultasHoje = curriculoDAO.getConsultasHoje();

        // 2. Conexão
        this.online = lattesService.testarConexaoCNPq();

        if (this.online) {
            this.statusTexto = "LATTES ONLINE";
            this.statusClasse = "bg-success";
            this.statusIcone = "z-icon-check-circle";
            // Inicia contagem em background se não estiver rodando
            if (!verificandoAtualizacoes) iniciarVerificacaoDesatualizados();
        } else {
            this.statusTexto = "LATTES OFFLINE";
            this.statusClasse = "bg-danger";
            this.statusIcone = "z-icon-exclamation-triangle";
            this.textoDesatualizados = "Off";
        }
    }

    private void iniciarVerificacaoDesatualizados() {
        this.verificandoAtualizacoes = true;
        this.textoDesatualizados = "A verificar...";

        // Captura o desktop atual para verificação posterior
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        new Thread(() -> {
            try {
                List<Curriculo> listaLocal = curriculoDAO.listarTodos();
                int contador = 0;
                for (Curriculo local : listaLocal) {
                    // SE A TELA JÁ FECHOU, PARA A THREAD IMEDIATAMENTE
                    if (!desktop.isAlive()) {
                        return;
                    }
                    if (isDesatualizado(local)) contador++;
                }

                final String resultado = String.valueOf(contador);

                // Só agenda a atualização se o desktop ainda estiver vivo
                if (desktop.isAlive()) {
                    try {
                        Executions.schedule(desktop, event -> {
                            this.textoDesatualizados = resultado;
                            this.verificandoAtualizacoes = false;
                            org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "textoDesatualizados");
                        }, new Event("onUpdate"));
                    } catch (Exception ex) {
                        // Ignora erro de desktop indisponível (o usuário mudou de página)
                    }
                }

            } catch (Exception e) {
                this.verificandoAtualizacoes = false;
            }
        }).start();
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
        this.listaDesatualizados.clear();
        this.logAtualizacao = "Iniciando verificação completa...\n";

        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        new Thread(() -> {
            List<Curriculo> locais = curriculoDAO.listarTodos();
            int total = locais.size();
            int atual = 0;

            for (Curriculo c : locais) {
                atual++;
                final String nome = c.getNomeCompleto().length() > 30 ? c.getNomeCompleto().substring(0, 30) + "..." : c.getNomeCompleto();
                final String progresso = "Checando " + atual + "/" + total + ": " + nome;

                atualizarLogAtualizacao(desktop, progresso + "...");

                if (isDesatualizado(c)) {
                    adicionarNaLista(desktop, c);
                    atualizarLogAtualizacao(desktop, " [DESATUALIZADO]\n");
                } else {
                    atualizarLogAtualizacao(desktop, " [OK]\n");
                }
            }

            atualizarLogAtualizacao(desktop, "\n✅ Concluído. " + listaDesatualizados.size() + " desatualizados.");
            finalizarProcesso(desktop, "Verificação concluída!", true);
        }).start();
    }

    @Command
    @NotifyChange({"listaDesatualizados", "processando", "logAtualizacao"})
    public void atualizarTodosDesatualizados() {
        if (listaDesatualizados.isEmpty()) {
            Clients.showNotification("A lista está vazia.", "warning", null, null, 3000);
            return;
        }

        this.processando = true;
        this.logAtualizacao += "\n--------------------------\nIniciando atualização em massa...\n";
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        // Copia a lista para evitar erro de concorrência ao remover itens
        List<Curriculo> paraAtualizar = new ArrayList<>(this.listaDesatualizados);

        new Thread(() -> {
            int total = paraAtualizar.size();
            int count = 0;

            for (Curriculo c : paraAtualizar) {
                count++;
                atualizarLogAtualizacao(desktop, "[" + count + "/" + total + "] Baixando " + c.getNomeCompleto() + "...");

                try {
                    Curriculo novo = lattesService.getCurriculo(c.getIdLattes());
                    if (novo != null) {
                        curriculoDAO.salvar(novo);
                        removerDaLista(desktop, c); // Remove da tabela visualmente
                        atualizarLogAtualizacao(desktop, " ✅ Sucesso.\n");
                    } else {
                        atualizarLogAtualizacao(desktop, " ❌ Vazio/Erro.\n");
                    }
                } catch (Exception e) {
                    atualizarLogAtualizacao(desktop, " ❌ Erro: " + e.getMessage() + "\n");
                }
            }
            atualizarLogAtualizacao(desktop, "\n🏁 Atualização finalizada.");
            finalizarProcesso(desktop, "Todos os currículos processados.", true);

            // Atualiza o dashboard geral (os cards)
            Executions.schedule(desktop, event -> atualizarDashboard(), new Event("onUpdate"));

        }).start();
    }

    @Command
    @NotifyChange("processando")
    public void atualizarUnico(@BindingParam("item") Curriculo item) {
        this.processando = true;
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        new Thread(() -> {
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
        }).start();
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
        } catch (Exception e) { return false; }
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

        new Thread(() -> {
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

                    // CORREÇÃO: Forçar CAIXA ALTA para garantir o match no banco do CNPq
                    String nomeEnvio = nomeInput.trim().toUpperCase();

                    // Passamos null no CPF, o LattesService converte para ""
                    idEncontrado = lattesService.buscarIdPorDados(null, nomeEnvio, dataFormatada);
                }

                if (idEncontrado != null && !idEncontrado.isEmpty() && !idEncontrado.equals("0")) {
                    this.idLattesInput = idEncontrado;
                    atualizarTela(desktop, "idLattesInput");
                    atualizarLog(desktop, "ID Encontrado: " + idEncontrado + ". Baixando...");

                    executarExtracaoInternal(desktop, idEncontrado, tipoBusca);
                } else {
                    // loga falha
                    AuditLogService.registrarExtracao(tipoBusca, login, false, termoBusca, "Pesquisador não localizado na busca");
                    finalizarProcesso(desktop, "❌ Pesquisador não encontrado. Tente verificar a data de nascimento.", false);
                }
            } catch (Exception e) {
                e.printStackTrace();

                // log de erro
                AuditLogService.registrarExtracao("BUSCA_ERRO", login, false, "N/A", "Erro: " + e.getMessage());

                finalizarProcesso(desktop, "❌ Erro na busca: " + e.getMessage(), false);
            }
        }).start();
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

        new Thread(() -> executarExtracaoInternal(desktop, idLattesInput, "POR_ID")).start();
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
            e.printStackTrace();

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
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- CORREÇÃO DE SINCRONIA AQUI ---
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
        } catch (Exception e) { e.printStackTrace(); }
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
        this.logBatch = "📂 Arquivo: " + media.getName() + "\n";

        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) desktop.enableServerPush(true);

        List<String> linhas = lerArquivo(media);
        if (linhas.isEmpty()) {
            this.logBatch += "⚠️ Arquivo vazio/inválido.";
            this.processando = false;
            return;
        }
        this.logBatch += "✅ " + linhas.size() + " linhas. Iniciando...\n----------------\n";
        new Thread(() -> processarLote(desktop, linhas)).start();
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
        } catch (Exception e) { e.printStackTrace(); }
        return lista;
    }

    private void processarLote(Desktop desktop, List<String> ids) {
        int total = ids.size();
        int sucesso = 0;
        int erro = 0;

        // captura login
        String login = (usuarioLogado != null) ? usuarioLogado.getLogin() : "ANONIMO";

        for (int i = 0; i < total; i++) {
            String dado = ids.get(i);
            try {
                String idBusca = dado;
                if (dado.length() == 11) {
                    atualizarLogBatch(desktop, "["+(i+1)+"/"+total+"] CPF "+dado+"...");
                    String conv = lattesService.buscarIdPorDados(dado, "", "");
                    if(conv != null && !conv.isEmpty()) idBusca = conv;
                    else {
                        erro++;
                        AuditLogService.registrarExtracao("LOTE_CPF", login, false, dado, "CPF não convertido" );
                        continue;
                    }
                }
                if (idBusca.length() != 16) { erro++; continue; }

                atualizarLogBatch(desktop, "["+(i+1)+"/"+total+"] ID "+idBusca+"...");
                Curriculo c = lattesService.getCurriculo(idBusca);
                if (c!=null) {
                    curriculoDAO.salvar(c);
                    sucesso++;
                    atualizarLogBatch(desktop, "✅ Salvo: "+c.getNomeCompleto()+"\n");

                    // log de sucesso
                    AuditLogService.registrarExtracao("LOTE", login, true, c.getIdLattes(), c.getNomeCompleto());
                }
                else {
                    erro++;
                    atualizarLogBatch(desktop, "❌ Falha.\n");

                    // log de falha
                    AuditLogService.registrarExtracao("LOTE", login, false, idBusca, "Não encontrado/Vazio");
                }
            } catch(Exception e){
                erro++;
                atualizarLogBatch(desktop, "❌ Erro.\n");

                // log de erro técnico
                AuditLogService.registrarExtracao("LOTE", login, false, dado, "Erro: " + e.getMessage());
            }
        }
        atualizarLogBatch(desktop, "\n🏁 FIM! OK: "+sucesso+" | Erros: "+erro);

        // Finaliza processo de lote
        try {
            if (desktop != null && desktop.isAlive()) {
                Executions.schedule(desktop, event -> {
                    this.processando = false;
                    org.zkoss.bind.BindUtils.postNotifyChange(null, null, ExtratorVM.this, "processando");
                    Clients.showNotification("Lote concluído!", "info", null, null, 3000);
                }, new Event("onUpdate"));
            }
        } catch (Exception e) {}
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
        } catch (Exception e) { e.printStackTrace(); }
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
        } catch (Exception e) { e.printStackTrace(); }
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
        new Thread(() -> {
            try {
                // Vai na internet buscar os dados (Lento)
                Integer cits = BibliometriaService.buscarCitacoes(artigo.getDoi());
                String[] acesso = BibliometriaService.buscarStatusAcesso(artigo.getDoi());

                artigo.setCitacoes(cits);
                artigo.setStatusAcesso(acesso[0]);

                producaoDAO.atualizarMetricas(artigo);

                // 5. Agenda a atualização da UI de volta no Desktop do ZK
                Executions.schedule(desktop, new EventListener<Event>() {
                    public void onEvent(Event event) {
                        // Atualiza o objeto
                        artigo.setCitacoes(cits);
                        artigo.setStatusAcesso(acesso[0]);
                        artigo.setCorAcesso(acesso[1]);

                        // Notifica a tela
                        BindUtils.postNotifyChange(null, null, artigo, "citacoes");
                        BindUtils.postNotifyChange(null, null, artigo, "statusAcesso");
                        BindUtils.postNotifyChange(null, null, artigo, "corAcesso");
                    }
                }, new Event("updateUI"));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
}