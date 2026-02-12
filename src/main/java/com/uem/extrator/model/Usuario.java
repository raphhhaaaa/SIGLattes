package com.uem.extrator.model;

import org.zkoss.bind.annotation.Command;

import javax.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Entity
@Table(name = "USUARIO", indexes = {
        @Index(name = "idx_usuario_login", columnList = "login", unique = true)
})
public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cd_usuario")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String login;

    @Column(nullable = false, length = 64) // SHA-256 gera 64 caracteres hex
    private String senha;

    @Column(name = "nome_completo", nullable = false, length = 30)
    private String nome;

    @Column(name = "admin")
    private boolean admin = false;

    public Usuario() {}

    public Usuario(String login, String senhaOriginal, String nome, boolean admin) {
        this.login = login;
        this.senha = criptografar(senhaOriginal);
        this.nome = nome;
        this.admin = admin;
    }

    // auxiliares de segurança

    /**
     * Gera o Hash SHA-256 da senha.
     * Metodo estático para poder ser usado no Login também.
     */

    public static String criptografar(String senhaOriginal) {
        if (senhaOriginal == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(senhaOriginal.getBytes(StandardCharsets.UTF_8));

            // converte byte para hex
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Erro ao criptografar senha: Algoritimo SHA-256 não encontrado.", e);
        }
    }

    // verifica se a senha digitada bate com a senha criptografada do usuario
    public boolean validarSenha(String senhaDigitada) {
        if (this.senha == null || senhaDigitada == null) return false;
        return this.senha.equals(criptografar(senhaDigitada));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public boolean isAdmin() { return admin; }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }
}
