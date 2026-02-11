package com.uem.extrator.service;

import java.net.*;

public class TesteConexao {

    // PREENCHA COM SEUS DADOS
    private static final String IP_PROXY = "186.233.154.49"; // Se for tunel local
    private static final int PORTA_PROXY = 9122;        // Sua porta

    public static void main(String[] args) {
        System.out.println("=== INICIANDO DIAGNÓSTICO DE REDE ===");
        System.out.println("Alvo: http://servicosweb.cnpq.br/srvcurriculo/WSCurriculo?wsdl");
        System.out.println("Proxy: " + IP_PROXY + ":" + PORTA_PROXY);

        // TESTE 1: TENTATIVA COMO PROXY HTTP COMUM
        System.out.println("\n--- TENTATIVA 1: Tipo HTTP ---");
        try {
            testarConexao(Proxy.Type.HTTP);
            System.out.println("✅ SUCESSO! Seu proxy é do tipo HTTP. Configure o LattesService com Proxy.Type.HTTP.");
            return; // Se funcionou, para aqui.
        } catch (Exception e) {
            System.out.println("❌ FALHA HTTP: " + e.getMessage());
        }

        // TESTE 2: TENTATIVA COMO PROXY SOCKS (TÚNEL SSH)
        System.out.println("\n--- TENTATIVA 2: Tipo SOCKS (SSH) ---");
        try {
            testarConexao(Proxy.Type.SOCKS);
            System.out.println("✅ SUCESSO! Seu proxy é do tipo SOCKS. Configure o LattesService com Proxy.Type.SOCKS.");
        } catch (Exception e) {
            System.out.println("❌ FALHA SOCKS: " + e.getMessage());
            System.out.println("\n CONCLUSÃO: O proxy não está acessível ou o firewall está bloqueando o Java.");
        }
    }

    public static void testarConexao(Proxy.Type tipo) throws Exception {
        Proxy proxy = new Proxy(tipo, new InetSocketAddress(IP_PROXY, PORTA_PROXY));
        URL url = new URL("http://servicosweb.cnpq.br/srvcurriculo/WSCurriculo?wsdl");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
        conn.setConnectTimeout(3000); // 3 segundos timeout
        conn.connect();

        System.out.println("Resposta do Servidor: " + conn.getResponseCode());
        if (conn.getResponseCode() != 200) {
            throw new RuntimeException("Código HTTP inesperado: " + conn.getResponseCode());
        }
    }
}