package com.uem.extrator.util;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.rmi.server.ServerCloneException;

@WebFilter("*.zul")
public class SecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        // onde usuario quer ir
        String url = req.getRequestURI();

        // quem é o usuario
        Object usuarioLogado = (session != null) ? session.getAttribute("usuario_logado") : null;

        // 1. Se for a página de login, deixa passar.
        // 2. Se for recurso estático (CSS, IMAGEM, JS), deixa passar (geralmente não cai aqui por causa do *.zul, mas por segurança).
        // 3. Se estiver logado, deixa passar.
        // 4. SENÃO -> manda para o login.

        boolean isLoginPage = url.endsWith("login.zul");

        if (usuarioLogado != null || isLoginPage) {
            chain.doFilter(request, response); // deixa entrar
        } else {
            // nega acesso: manda para login
            res.sendRedirect(req.getContextPath() + "/login.zul");
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}
}
