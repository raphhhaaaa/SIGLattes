package com.uem.extrator.util;

import com.uem.extrator.model.Usuario;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@WebFilter("*.zul")
public class SecurityFilter implements Filter {

    // rotas públicas (qualquer pessoa da internet pode entrar)
    private static final List<String> PUBLIC_WHITELIST = Arrays.asList(
            "/login.zul"
    );

    // rotas do usuário COMUM
    private static final List<String> COMMON_USER_WHITELIST  = Arrays.asList(
            "/index.zul",
            "/navbar.zul",
            "/header.zul",
            "/footer.zul",
            "/menubar.zul",
            "/detalhes.zul",
            "/paginas/modalDetalhes.zul",
            "/paginas/pessoa/pessoaList.zul",
            "/paginas/instituicao/instituicaoList.zul",
            "/paginas/curso/cursoList.zul",
            "/paginas/relatorio/relatorioDinamico.zul",
            "/paginas/relatorio/relatorioProdutividade.zul",
            "/paginas/relatorio/relatorioRevistas.zul",
            "/paginas/ferramentas/verificacao.zul"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        // remove o contexto para analisar somente o caminho
        String path = url.substring(contextPath.length());

        Object usuarioLogado = (session != null) ? session.getAttribute("usuario_logado") : null;

        // regra 1: se for página publica, deixa passar
        if (PUBLIC_WHITELIST.stream().anyMatch(path::equals)) {
            chain.doFilter(request, response);
            return;
        }

        // regra 2: se não for pública e não está logado, manda para o login
        if (usuarioLogado == null) {
            res.sendRedirect(contextPath + "/login.zul");
            return;
        }

        // SISTEMA DE AUTORIZAÇÃO
        Usuario usuario = (Usuario) usuarioLogado;

        // regra 3: se for admin tem passe para o sistema inteiro
        if (usuario.isAdmin()) {
            chain.doFilter(request, response);
            return;
        }

        // regra 4: se for usuário comum, verifica a white-list
        boolean isPermitido = COMMON_USER_WHITELIST.stream().anyMatch(path::equals);

        if (isPermitido) {
            chain.doFilter(request, response); // esta na lista pode passar
        } else {
            res.sendRedirect(contextPath + "/index.zul");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}
}