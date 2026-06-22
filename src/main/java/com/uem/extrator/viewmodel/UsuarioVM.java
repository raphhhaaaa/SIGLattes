package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.service.AuditLogService;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.util.Clients;
import java.util.List;
import org.zkoss.zk.ui.event.Event;
import com.uem.extrator.service.AuditLogService;
import org.zkoss.zk.ui.Sessions;

public class UsuarioVM {

    private Usuario usuarioLogado;
    private UsuarioDAO dao = new UsuarioDAO();
    private List<Usuario> listaUsuarios;
    private Usuario usuarioEdicao; // o usuário sendo criado/editado no modal
    private String senhaTemporaria; // campo auxiliar para a senha no formulario
    private boolean modoEdicao = false; // controla se o modal está visível

    @Init
    public void init() {
        usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
        carregarLista();
    }

    public void carregarLista() {
        this.listaUsuarios = dao.listarTodos();
    }

    @Command
    @NotifyChange({"usuarioEdicao", "modoEdicao", "senhaTemporaria"})
    public void novoUsuario() {
        this.usuarioEdicao = new Usuario();
        this.senhaTemporaria = ""; // limpa campo de senha
        this.modoEdicao = true; // abre o modal
    }

    @Command
    @NotifyChange({"usuarioEdicao", "modoEdicao", "senhaTemporaria"})
    public void editarUsuario(@org.zkoss.bind.annotation.BindingParam("u") Usuario u) {
        this.usuarioEdicao = u;
        this.senhaTemporaria = ""; // não mostramos senha antiga, deixa vazio
        this.modoEdicao = true;
    }

    @Command
    @NotifyChange({"listaUsuarios", "modoEdicao"})
    public void salvar() {
        // validação
        if (usuarioEdicao.getLogin() == null || usuarioEdicao.getLogin().isEmpty()) {
            Clients.showNotification("O login é obrigatório!", "warning", null, null, 2000);
            return;
        }

        // lógica de senha:
        // se for NOVO usuario, a senha é obrigatório
        // se for EDIÇÃO, a senha é opcional (só muda se digitar algo novo)
        if (usuarioEdicao.getId() == null && (senhaTemporaria == null || senhaTemporaria.trim().isEmpty())) {
            Clients.showNotification("Defina uma senha para o novo usuário!", "warning", null, null, 2000);
            return;
        }

        // se digitou senha nova, aplica hash e salva
        if (senhaTemporaria != null && !senhaTemporaria.isEmpty()) {
            usuarioEdicao.setSenha(senhaTemporaria);
        }

        if (dao.salvar(usuarioEdicao)) {
            // colhe informações do usuario
            Usuario admin = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
            String autor = (admin != null) ? admin.getLogin() : "SISTEMA";
            String acao = (usuarioEdicao.getId() == null) ? "CRIAR_USUARIO" : "EDITAR_USUARIO";

            // loga
            AuditLogService.log(acao, autor, "Alvo: " + usuarioEdicao.getLogin() + " | Admin: " + usuarioEdicao.isAdmin());

            Clients.showNotification("Usuário salvo com sucesso!", "info", null, null, 3000);
            carregarLista();
            this.modoEdicao = false; // fecha modal
        } else {
            Clients.showNotification("Erro ao salvar. Tente outro login.", "error", null, null, 3000);
        }
    }

    @Command
    @NotifyChange({"listaUsuarios"})
    public void excluir(@org.zkoss.bind.annotation.BindingParam("u") Usuario u) {
        if ("admin".equals(u.getLogin())) {
            Clients.showNotification("O admin principal não pode ser excluído.", "error", null, null, 3000);
            return;
        }

        if (dao.excluir(u)) {
            // colhe informacoes do usuario
            Usuario admin = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
            String autor = (admin != null) ? admin.getLogin() : "SISTEMA";

            // loga
            AuditLogService.log("EXCLUIR_USUARIO", autor, "Usuário removido: " + u.getLogin());

            Clients.showNotification("Usuário removido com sucesso.", "info", null, null, 3000);
            carregarLista();
        } else {
            Clients.showNotification("Erro ao excluir. Tente novamente.", "error", null, null, 2000);
        }
    }

    @Command
    @NotifyChange("usuarioEdicao")
    public void alternarPermissao(@BindingParam("tipo") String tipo) {
        if (usuarioEdicao == null) return;
        
        if ("ADMIN".equals(tipo) && usuarioEdicao.isAdmin()) {
            usuarioEdicao.setGestor(false);
        } else if ("GESTOR".equals(tipo) && usuarioEdicao.isGestor()) {
            usuarioEdicao.setAdmin(false);
        }
    }

    @Command
    @NotifyChange({"modoEdicao"})
    public void cancelar(@ContextParam(ContextType.TRIGGER_EVENT) Event event) {
        // O SEGREDO ESTÁ AQUI:
        if (event != null) {
            // Impede que o ZK destrua (detach) a janela ao clicar no X
            event.stopPropagation();
        }

        this.modoEdicao = false; // Apenas esconde a janela (visible=false)
        carregarLista();
    }

    // Getter & Setters

    public List<Usuario> getListaUsuarios() { return listaUsuarios; }
    public Usuario getUsuarioEdicao() { return usuarioEdicao; }
    public void setUsuarioEdicao(Usuario usuarioEdicao) { this.usuarioEdicao = usuarioEdicao; }
    public boolean isModoEdicao() { return modoEdicao; }
    public String getSenhaTemporaria() { return senhaTemporaria; }
    public void setSenhaTemporaria(String senhaTemporaria) { this.senhaTemporaria = senhaTemporaria; }
    public Usuario getUsuarioLogado() { return usuarioLogado; }
}
