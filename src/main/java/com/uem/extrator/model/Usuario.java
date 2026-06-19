package com.uem.extrator.model;

import org.zkoss.bind.annotation.Command;

import javax.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.mindrot.jbcrypt.*;

@Entity
@Table(name = "SEL_USUARIO", indexes = {
        @Index(name = "idx_usuario_login", columnList = "nm_login", unique = true)
})
public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @SequenceGenerator(name = "seqUsuario", sequenceName = "SEL.SEQ_USUARIO", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqUsuario")
    @Column(name = "id_usuario")
    private Long id;

    @Column(name = "nm_login", nullable = false, unique = true, length = 50)
    private String login;

    @Column(name = "se_usuario", nullable = false, length = 64) // SHA-256 gera 64 caracteres hex
    private String senha;

    @Column(name = "nm_completo", nullable = false, length = 60)
    private String nome;

    @Column(name = "fg_admin")
    private boolean admin = false;

    @Column(name = "fg_gestor")
    private boolean gestor = false;

    public Usuario() {}

    public Usuario(String login, String senhaOriginal, String nome, boolean admin) {
        this.login = login;
        this.senha = BCrypt.hashpw(senhaOriginal,BCrypt.gensalt(12));
        this.nome = nome;
        this.admin = admin;
    }

    // auxiliares de segurança

    /**
     * Gera o Hash SHA-256 da senha.
     * Metodo estático para poder ser usado no Login também.
     */

    /**
     *
     * ^^^ metodo acima é antigo, apenas gerava hash SHA-256 cru, sem salt.
     * gerando vulnerabilidades (hashes iguais para senhas iguais por exemplo)

     * Novo metodo aplica salting (caracteres aleatorios) em todas as senhas criptografadas, logo após a aplicação
     * do SHA-256, criando uma cryptpass forte e praticamente invulnerável. (usando BCrpyt)


     * Permite retrocompatibilidade com o sha-256 puro, possibilitando aos usuarios com
     * senha antiga que utilizem o sistema normalmente, entretanto, se for feito uma
     * alteração da senha (recomendado) a atualização ja ira aplicar o salting automaticamente.
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

        // retrocompatibilidade: se detectar um hash sha-256 antigo (64 caracteres) usa a
        // verificação antiga.
        if (this.senha.length() == 64) {
            return this.senha.equals(criptografar(senhaDigitada));
        }

        // se for uma "senha moderna", usa a verificação do Bcrypt
        return BCrypt.checkpw(senhaDigitada, this.senha);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getSenha() { return senha; }
    public void setSenha(String senhaOriginal) {
        this.senha = BCrypt.hashpw(senhaOriginal, BCrypt.gensalt(12));
    }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public boolean isAdmin() { return admin; }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isGestor() { return gestor; }

    public void setGestor(boolean gestor) {
        this.gestor = gestor;
    }
}
