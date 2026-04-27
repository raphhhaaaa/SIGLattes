package modelo;

public class Cor {

    // atributos
    private static final Cor BRANCA = new Cor("BRANCA");
    private static final Cor PRETA = new Cor("PRETA");
    private String nome;

    // construtor
    private Cor(String nome) {
        this.nome = nome;
    }

    // getters & setters
    public String getNome() {
        return nome;
    }

    // métodos auxiliares
    public Cor oposta() {

        if (this == BRANCA) {
            return PRETA;
        } else {
            return BRANCA;
        }

    }
}