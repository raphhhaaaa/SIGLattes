package com.uem.extrator.util;

import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import java.util.concurrent.ConcurrentHashMap;

public class UsuarioSessaoListener implements HttpSessionBindingListener {

    // O grande "cofre" estático que o Tomcat todo enxerga
    private static final ConcurrentHashMap<String, UsuarioSessaoListener> ativos = new ConcurrentHashMap<>();

    private String login;

    public UsuarioSessaoListener(String login) {
        this.login = login;
    }

    // --- Métodos da Interface ---

    @Override
    public void valueBound(HttpSessionBindingEvent event) {
        // Disparado automaticamente assim que adicionamos este objeto na sessão
        ativos.put(this.login, this);
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent event) {
        // Disparado automaticamente pelo Tomcat no Timeout ou Invalidate
        ativos.remove(this.login, this);
    }

    // auxiliar
    public static int getTotalOnline() {
        return ativos.size();
    }
}
