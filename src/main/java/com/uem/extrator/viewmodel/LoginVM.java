package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;

public class LoginVM {

    private String usuario;
    private String senha;
    private String mensagemErro;
    private UsuarioDAO dao = new UsuarioDAO();

    @Init
    public void init() {
        // se ja tiver logado joga pro index.xul (pagina principal)
        if (Sessions.getCurrent().getAttribute("usuario_logado") != null) {
            Executions.sendRedirect("/index.zul");
        }
    }

    @Command
    @NotifyChange("mensagemErro")
    public void autenticar() {
        this.mensagemErro = "";

        if (usuario == null || usuario.trim().isEmpty() || senha == null || senha.trim().isEmpty()) {
            this.mensagemErro = "Preencha todos os campos.";
            return;
        }

        // busca no banco
        Usuario userBanco = dao.buscarPorLogin(usuario);

        // verifica se existe e se a senha bate
        if (userBanco != null && userBanco.validarSenha(senha)) {
            // se existe, salva o usuario na sessão
            Sessions.getCurrent().setAttribute("usuario_logado", userBanco);

            // redireciona para a tela principa
            Executions.sendRedirect("/index.zul");
        } else {
            // nao existe
            this.mensagemErro = "Usuário ou senha incorretos.";
        }
    }

    // Getters && Setters

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getMensagemErro() { return mensagemErro; }
    public void setMensagemErro(String mensagemErro) { this.mensagemErro = mensagemErro; }
}
