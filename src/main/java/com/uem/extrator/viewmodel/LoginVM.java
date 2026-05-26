package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.UsuarioDAO;
import com.uem.extrator.model.Usuario;
import com.uem.extrator.util.ConfigManager;
import com.uem.extrator.service.UemLdapService;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import com.uem.extrator.service.AuditLogService;

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

        String usuarioLimpo = usuario.trim().toUpperCase();

//        // verifica se o usuário tentando acessar não é aluno; se for, barra.
//        if (usuarioLimpo.startsWith("RA") || usuarioLimpo.startsWith("PG")) {
//            AuditLogService.log("LOGIN_BLOQUEADO", usuario, "Tentativa de acesso por perfil de aluno.");
//            this.mensagemErro = "Acesso restrito: Alunos não têm permissão para utilizar essa ferramenta.";
//            return;
//        }

        // verifica qual bind de autenticação está configurada
        String tipoAutenticacao = ConfigManager.getInstance().getAuthType();
        boolean autenticado = false;
        Usuario usuarioSessao = null; // Este será o usuário (Real ou Temporário)

        // atenção, o usuario ADMIN possui acesso LIVRE e IRRESTRITO independente do metodo de autenticacao configurado
        boolean isContaEmergencia = "ADMIN".equals(usuarioLimpo);

        // se o tipo de autenticação configurado é LDAP E NÃO for a conta de admin, chama o serviço uem e tenta autenticar
        if ("LDAP".equalsIgnoreCase(tipoAutenticacao) && !isContaEmergencia) {

            // servidor da UEM (completamente isolado do banco)
            UemLdapService ldap = new UemLdapService();
            autenticado = ldap.autenticar(usuario, senha);

            if (autenticado) {
                // cria usuário temporário na memória
                // vive apenas na sessão do Tomcat e nunca é salvo no DB2.
                usuarioSessao = new Usuario();
                usuarioSessao.setLogin(usuario);
                usuarioSessao.setNome(usuario.split("@")[0]); // Usa o RA ou NPM como nome
                // define um perfil/nível de acesso padrão para quem vem do LDAP
                usuarioSessao.setAdmin(false);
            } else {
                this.mensagemErro = "Utilizador ou palavra-passe institucionais inválidos.";
            }

        } else {

            // se o tipo de autenticação configurado é LOCAL, faz a verificação padrão no banco de dados (Requer cadastro prévio pelo Admin)
            usuarioSessao = dao.buscarPorLogin(usuario);

            if (usuarioSessao == null) {
                AuditLogService.log("LOGIN_FALHA", usuario, "Tentativa com utilizador inexistente no banco local.");
                this.mensagemErro = "Acesso negado: Utilizador não cadastrado no sistema.";
                return;
            }

            autenticado = usuarioSessao.validarSenha(senha);

            if (!autenticado) {
                this.mensagemErro = "Palavra-passe local inválida.";
            }
        }

        // auditoria
        if (autenticado && usuarioSessao != null) {
            // Salva o utilizador (seja o do banco ou o temporário do LDAP) na sessão
            Sessions.getCurrent().setAttribute("usuario_logado", usuarioSessao);

            String usuarioPraLog = "";

            // se houver domínio no usuário, remove para logar
            if (usuarioSessao.getLogin().contains("@uem.br")) {
                usuarioPraLog = usuarioSessao.getLogin().split("@")[0].trim();
            } else { // se não houver, apenas pega o login
                usuarioPraLog = usuarioSessao.getLogin().trim();
            }

            AuditLogService.log("LOGIN_SUCESSO", usuarioPraLog,
                    "Acesso " + tipoAutenticacao + " realizado. IP: " + Executions.getCurrent().getRemoteAddr());

            Executions.sendRedirect("/index.zul");
        } else if (!autenticado && this.mensagemErro.isEmpty()) {
            AuditLogService.log("LOGIN_FALHA", usuario, "Falha na autenticação via " + tipoAutenticacao + ".");
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
