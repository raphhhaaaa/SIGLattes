package com.uem.extrator.service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UemLdapService {


    // logger
    private static final Logger logger = LoggerFactory.getLogger(UemLdapService.class);

    // LDAP configs
    private static final String LDAP_URL = "ldap://ldap.uem.br:389";
    private static final String LDAP_BASE_DN = "ou=People,dc=uem,dc=br";

    public boolean autenticar(String login, String senha) {

        // credenciais nulas
        if (login == null || senha == null || login.trim().isEmpty() || senha.trim().isEmpty()) {
            return false;
        }


        // limpa o domínio (@uem.br)
        String usuarioLimpo = login.split("@")[0].trim();
        String userDN = "uid=" + usuarioLimpo + "," + LDAP_BASE_DN;

        // PESQUISA ANÓNIMA (Para impedir o Silent Anonymous Bind)
        Properties envAnon = new Properties();
        envAnon.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        envAnon.put(Context.PROVIDER_URL, LDAP_URL);
        envAnon.put(Context.SECURITY_AUTHENTICATION, "none"); // Entramos sem senha

        try {
            DirContext ctxAnon = new InitialDirContext(envAnon);

            // Força a pesquisa pelo usuário
            SearchControls sc = new SearchControls();
            sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
            sc.setReturningAttributes(new String[]{"uid"});
            NamingEnumeration<SearchResult> results = ctxAnon.search(LDAP_BASE_DN, "uid=" + usuarioLimpo, sc);


            // se não existe na base da UEM
            if (!results.hasMore()) {
                logger.warn("LDAP Bloqueado: Utilizador não existe na base da UEM: {}", usuarioLimpo);
                ctxAnon.close();
                return false; // Utilizador fantasma bloqueado!
            }
            ctxAnon.close();

            // caso contrário: existe na base da uem, verifica a senha

            // TESTE DE SENHA REAL
            Properties envAuth = new Properties();
            envAuth.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            envAuth.put(Context.PROVIDER_URL, LDAP_URL);
            envAuth.put(Context.SECURITY_AUTHENTICATION, "simple");
            envAuth.put(Context.SECURITY_PRINCIPAL, userDN);
            envAuth.put(Context.SECURITY_CREDENTIALS, senha);

            DirContext ctxAuth = new InitialDirContext(envAuth);

            // Teste de Fogo final: Tenta extrair os atributos. Se o servidor
            // "mentiu" sobre o login, ele vai atirar uma exceção agora.
            ctxAuth.getAttributes(userDN);
            ctxAuth.close();

            logger.info("LDAP Autenticado: Acesso legítimo para: {}", usuarioLimpo);
            return true;

        } catch (javax.naming.AuthenticationException e) {
            logger.warn("LDAP Bloqueado: Senha incorreta para o utilizador: {}", usuarioLimpo);
            return false; // Palavra-passe errada bloqueada!
        } catch (Exception e) {
            logger.error("LDAP Falha Técnica: Erro de infraestrutura ao validar: {}", usuarioLimpo, e);
            return false;
        }
    }
}