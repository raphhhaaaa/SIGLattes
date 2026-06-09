package com.uem.extrator.service;

import com.uem.extrator.model.*;
import com.uem.extrator.util.FiltroSimilaridade;
import net.bytebuddy.asm.Advice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LattesParser {

    // instancia logger
    private static final Logger logger = LoggerFactory.getLogger(LattesParser.class);

    // ATENÇÃO: XMLInputFactory NÃO é thread-safe — NÃO deve ser static.
    // Em extração em lote com múltiplas threads, uma factory estática compartilhada
    // causa NullPointerException por race condition em setProperty/createXMLStreamReader.
    // Cada chamada a parse() cria sua própria instância local (custo negligenciável).

    // metodo de parseamento
    public Curriculo parse(String xmlConteudo, String idLattes) throws Exception {

        // estruturas auxiliares para armazenarem o estado temporario do streaming
        String currentElement = "";
        Atuacao atuacaoAtual = null;
        Atividade atividadeAtual = null;
        Formacao formacaoAtual = null;
        Producao producaoAtual = null;

        // atributos de buffer para detalhes soltos
        String detalhePeriodico = null;
        String detalheISSN = null;
        String detalheVol = null;
        String detalhePag = null;
        String detalheEditora = null;
        String detalheISBN = null;
        String detalheNomeEvento = null;
        String detalheAnais = null;
        String detalheAgencia = null;
        String detalheCandidato = null;
        String detalheOrientado = null;
        String detalheInstituicao = null;

        Curriculo curriculo = new Curriculo();
        curriculo.setIdLattes(idLattes);

        // BLINDAGEM CONTRA XXE — instância local, thread-safe
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

        // cursor
        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlConteudo));

        while (reader.hasNext()) {
            int event = reader.next();

            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    currentElement = reader.getLocalName();

                    /**
                     * DADOS GERAIS, RESUMO E DATA DE ATUALIZAÇÃO
                      */

                    // data de atualização do curriculo
                    if ("CURRICULO-VITAE".equals(currentElement)) {
                        String dataStr = getAttributeValueSafe(reader, "DATA-ATUALIZACAO");
                        if (dataStr.length() == 8) {
                            curriculo.setDataAtualizacao(new SimpleDateFormat("ddMMyyyy").parse(dataStr));
                        } else {
                            curriculo.setDataAtualizacao(new Date());
                        }
                    }

                    // dados gerais
                    else if ("DADOS-GERAIS".equals(currentElement)) {

                        String nome = getAttributeValueSafe(reader, "NOME-COMPLETO");
                        if (nome.trim().isEmpty()) {
                            logger.warn("Currículo {} sem nome completo - usando fallback.", idLattes);
                            nome = "Pesquisador ID " + idLattes;
                        }
                        curriculo.setNomeCompleto(nome);

                        // NOME-CITACAO
                        String nomeCitacao = getAttributeValueSafe(reader, "NOME-EM-CITACOES-BIBLIOGRAFICAS");
                        if (nomeCitacao.length() > 1000) {
                            nomeCitacao = nomeCitacao.substring(0, 995) + "...";
                        }
                        curriculo.setNomeCitacao(nomeCitacao);

                        // ORCID
                        String orcidBruto = getAttributeValueSafe(reader, "ORCID-ID");
                        if (orcidBruto != null && !orcidBruto.trim().isEmpty()) {
                            // padrão oficial do orcid, 4 blocos de 4 dígitos (o ultimo pode se X)
                            Matcher m = Pattern
                                    .compile("([0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X])")
                                    .matcher(orcidBruto.toUpperCase());
                            if (m.find()) {
                                curriculo.setOrcid(m.group(1));    // salva apenas os 16 dígitos limpos
                            }
                        }
                    }

                    // resumo curriculo
                    else if ("RESUMO-CV".equals(currentElement)) {
                        String resumo = getAttributeValueSafe(reader, "TEXTO-RESUMO");
                        if (resumo.trim().isEmpty()) {
                            resumo = getAttributeValueSafe(reader, "TEXTO-RESUMO-CV-RH");
                        }
                        curriculo.setResumo(resumo);
                    }


                    /**
                     * FORMAÇÃO ACADÊMICA E ÁREAS
                     */

                    // tipo de formação e nome
                    else if (isNivelAcademicoValido(currentElement)) {
                        formacaoAtual = new Formacao();
                        formacaoAtual.setTipoFormacao(currentElement);
                        formacaoAtual.setNomeInstituicao(tratarInstituicao(getAttributeValueSafe(reader, "NOME-INSTITUICAO")));

                        String nomeCursoStr = getAttributeValueSafe(reader, "NOME-CURSO");
                        if (nomeCursoStr.trim().isEmpty() && "POS-DOUTORADO".equals(currentElement)) {
                            nomeCursoStr = "Pós-Doutorado em Pesquisa";
                        }

                        // instancia o curso limpo (as areas sao injetadas se a tag filha aparecer a seguir)
                        formacaoAtual.setNomeCurso(tratarCursoBase(nomeCursoStr));
                        formacaoAtual.setStatus(getAttributeValueSafe(reader, "STATUS-DO-CURSO"));
                        formacaoAtual.setAnoInicio(parseIntSafe(getAttributeValueSafe(reader, "ANO-DE-INICIO")));
                        formacaoAtual.setAnoConclusao(parseIntSafe(getAttributeValueSafe(reader, "ANO-DE-CONCLUSAO")));
                    }

                    // areas
                    else if (currentElement.startsWith("AREA-DO-CONHECIMENTO-")) {
                        String grandeArea = getAttributeValueSafe(reader, "NOME-GRANDE-AREA-DO-CONHECIMENTO");
                        String area = getAttributeValueSafe(reader, "NOME-DA-AREA-DO-CONHECIMENTO");
                        String subArea = getAttributeValueSafe(reader, "NOME-DA-SUB-AREA-DO-CONHECIMENTO");
                        String especialidade = getAttributeValueSafe(reader, "NOME-DA-ESPECIALIDADE");

                        if (formacaoAtual != null && formacaoAtual.getNomeCurso() != null) {
                            Curso curso = formacaoAtual.getNomeCurso();

                            if (!grandeArea.isEmpty() && curso.getNomeGrandeArea() == null) curso.setNomeGrandeArea(capitalizarPalavras(grandeArea));
                            if (!area.isEmpty() && curso.getNomeArea() == null) curso.setNomeArea(capitalizarPalavras(area));
                            if (!subArea.isEmpty() && curso.getNomeSubArea() == null) curso.setNomeSubArea(capitalizarPalavras(subArea));
                            if (!especialidade.isEmpty() && curso.getNomeEspecialidade() == null) curso.setNomeEspecialidade(capitalizarPalavras(especialidade));

                        } else if (atividadeAtual != null) {
                            if (!grandeArea.isEmpty() && atividadeAtual.getNomeGrandeArea() == null)
                                atividadeAtual.setNomeGrandeArea(grandeArea);
                            if (!area.isEmpty() && atividadeAtual.getNomeArea() == null)
                                atividadeAtual.setNomeArea(area);
                        }
                    }

                    /**
                     * PRODUÇÕES BIBLIOGRÁFICAS
                     */

                    // artigos
                    else if ("ARTIGO-PUBLICADO".equals(currentElement)) {
                        producaoAtual = new Producao();
                        producaoAtual.setCurriculo(curriculo);
                        producaoAtual.setTipo("ARTIGO");

                        detalhePeriodico = null;
                        detalheISSN = null;
                        detalheVol = null;
                        detalhePag = null;
                        detalheEditora = null;
                        detalheISBN = null;
                        detalheNomeEvento = null;
                        detalheAnais = null;




                    } else if ("DADOS-BASICOS-DO-ARTIGO".equals(currentElement) && producaoAtual != null) {
                        producaoAtual.setTitulo(FiltroSimilaridade.normalizarTituloProducao(
                                getAttributeValueSafe(reader, "TITULO-DO-ARTIGO")));
                        producaoAtual.setHashTitulo(gerarHash(producaoAtual.getTitulo()));
                        Integer ano_Artigo = parseIntSafe(getAttributeValueSafe(reader, "ANO-DO-ARTIGO"));
                        producaoAtual.setAno(validarAno(ano_Artigo));
                        producaoAtual.setPais(getAttributeValueSafe(reader, "PAIS-DE-PUBLICACAO"));
                        producaoAtual.setIdioma(getAttributeValueSafe(reader, "IDIOMA"));

                        String doiLattes = limparDoiValido(getAttributeValueSafe(reader, "DOI"));
                        if (doiLattes != null && doiLattes.length() > 200) doiLattes = doiLattes.substring(0, 195) + "...";
                        producaoAtual.setDoi(doiLattes);

                    } else if ("DETALHAMENTO-DO-ARTIGO".equals(currentElement) && producaoAtual != null) {
                        detalhePeriodico = getAttributeValueSafe(reader, "TITULO-DO-PERIODICO-OU-REVISTA");
                        detalheISSN = getAttributeValueSafe(reader, "ISSN");
                        detalheVol = getAttributeValueSafe(reader, "VOLUME");
                        detalhePag = getAttributeValueSafe(reader, "PAGINA-INICIAL") + "-" + getAttributeValueSafe(reader, "PAGINA-FINAL");
                    }

                    // livros
                    else if ("LIVRO-PUBLICADO-OU-ORGANIZADO".equals(currentElement)) {
                        producaoAtual = new Producao();
                        producaoAtual.setTipo("LIVRO");

                        detalhePeriodico = null;
                        detalheISSN = null;
                        detalheVol = null;
                        detalhePag = null;
                        detalheEditora = null;
                        detalheISBN = null;
                        detalheNomeEvento = null;
                        detalheAnais = null;

                    } else if ("DADOS-BASICOS-DO-LIVRO".equals(currentElement) && producaoAtual != null) {
                        producaoAtual.setTitulo(FiltroSimilaridade.normalizarTituloProducao(
                                getAttributeValueSafe(reader, "TITULO-DO-LIVRO")));
                        producaoAtual.setHashTitulo(gerarHash(producaoAtual.getTitulo()));
                        producaoAtual.setAno(validarAno(parseIntSafe(getAttributeValueSafe(reader, "ANO"))));
                        producaoAtual.setPais(getAttributeValueSafe(reader, "PAIS-DE-PUBLICACAO"));
                        producaoAtual.setIdioma(getAttributeValueSafe(reader, "IDIOMA"));

                    } else if ("DETALHAMENTO-DO-LIVRO".equals(currentElement) && producaoAtual != null) {
                        detalheEditora = getAttributeValueSafe(reader , "NOME-DA-EDITORA");
                        detalheISBN = getAttributeValueSafe(reader, "ISBN");
                        detalhePag = getAttributeValueSafe(reader, "NUMERO-DE-PAGINAS");
                    }

                    // eventos
                    else if ("TRABALHO-EM-EVENTOS".equals(currentElement)) {
                        producaoAtual = new Producao();
                        producaoAtual.setTipo("EVENTO");

                        detalhePeriodico = null;
                        detalheISSN = null;
                        detalheVol = null;
                        detalhePag = null;
                        detalheEditora = null;
                        detalheISBN = null;
                        detalheNomeEvento = null;
                        detalheAnais = null;

                    } else if ("DADOS-BASICOS-DO-TRABALHO".equals(currentElement) && producaoAtual != null) {
                        producaoAtual.setTitulo(FiltroSimilaridade.normalizarTituloProducao(
                                getAttributeValueSafe(reader, "TITULO-DO-TRABALHO")));
                        producaoAtual.setHashTitulo(gerarHash(producaoAtual.getTitulo()));
                        producaoAtual.setAno(validarAno(parseIntSafe(getAttributeValueSafe(reader, "ANO-DO-TRABALHO"))));
                        producaoAtual.setPais(getAttributeValueSafe(reader, "PAIS-DO-EVENTO"));
                        producaoAtual.setNatureza(getAttributeValueSafe(reader, "NATUREZA"));

                    } else if ("DETALHAMENTO-DO-TRABALHO".equals(currentElement) && producaoAtual != null) {
                        detalheNomeEvento = getAttributeValueSafe(reader, "NOME-DO-EVENTO");
                        detalheAnais = getAttributeValueSafe(reader, "TITULO-DOS-ANAIS-OU-PROCEEDINGS");
                    }

                    /**
                     * ATUAÇÕES E VÍNCULOS
                     */

                    else if ("ATUACAO-PROFISSIONAL".equals(currentElement)) {
                        atuacaoAtual = new Atuacao();
                        atuacaoAtual.setInstituicao(tratarInstituicao(getAttributeValueSafe(reader, "NOME-INSTITUICAO")));
                        atuacaoAtual.setSequencia(parseIntSafe(getAttributeValueSafe(reader, "SEQUENCIA-IMPORTANCIA")));

                    } else if ("VINCULOS".equals(currentElement) && atuacaoAtual != null) {
                        Vinculo vinculo = new Vinculo();
                        vinculo.setTipoVinculo(getAttributeValueSafe(reader, "OUTRO-VINCULO-INFORMADO"));
                        vinculo.setDescEnquadramento(limparTextoLongo(getAttributeValueSafe(reader, "OUTRAS-INFORMACOES"), 2000));
                        vinculo.setDescCargaHoraria(getAttributeValueSafe(reader, "CARGA-HORARIA-SEMANAL"));

                        String flag = getAttributeValueSafe(reader, "FLAG-VINCULO-EMPREGATICIO");
                        vinculo.setFlagVinculoEmpregaticio("SIM".equalsIgnoreCase(flag) ? "S" : "N");
                        vinculo.setAnoInicio(parseIntSafe(getAttributeValueSafe(reader, "ANO-INICIO")));
                        vinculo.setMesInicio(parseIntSafe(getAttributeValueSafe(reader, "MES-INICIO")));
                        vinculo.setAnoFim(parseIntSafe(getAttributeValueSafe(reader, "ANO-FIM")));
                        vinculo.setMesFim(parseIntSafe(getAttributeValueSafe(reader, "MES-FIM")));

                        atuacaoAtual.adicionarVinculo(vinculo);
                    }

                    /**
                     * PROJETOS (PESQUISA / EXTENSÃO)
                     */

                    else if ("PROJETO-DE-PESQUISA".equals(currentElement) || "PROJETO-DE-EXTENSAO".equals(currentElement)) {
                        atividadeAtual = new Atividade();
                        atividadeAtual.setTipoAtividades("PROJETO-DE-PESQUISA".equals(currentElement) ? "PESQUISA" : "EXTENSAO");
                        atividadeAtual.setAnoInicio(parseIntSafe(getAttributeValueSafe(reader, "ANO-INICIO")));
                        atividadeAtual.setMesInicio(parseIntSafe(getAttributeValueSafe(reader, "MES-INICIO")));

                        String anoFim = getAttributeValueSafe(reader, "ANO-FIM");
                        if (!anoFim.isEmpty()) {
                            atividadeAtual.setAnoFim(parseIntSafe(anoFim));
                            atividadeAtual.setMesFim(parseIntSafe(getAttributeValueSafe(reader, "MES-FIM")));
                        }

                        AtividadeItem itemNome = new AtividadeItem();
                        itemNome.setTipoItem("NOME_PROJETO");
                        itemNome.setDescricaoItem(getAttributeValueSafe(reader, "NOME-DO-PROJETO"));
                        itemNome.setDetalhe(getAttributeValueSafe(reader, "DESCRICAO-DO-PROJETO"));
                        itemNome.setAtividade(atividadeAtual);
                        atividadeAtual.getItens().add(itemNome);
                    } else if ("INTEGRANTES-DO-PROJETO".equals(currentElement) && atividadeAtual != null) {
                        AtividadeItem itemInt = new AtividadeItem();
                        itemInt.setTipoItem("INTEGRANTE");
                        String nome = getAttributeValueSafe(reader, "NOME-COMPLETO");
                        if (nome.isEmpty()) {
                            nome = getAttributeValueSafe(reader, "NOME-PARA-CITACAO");
                        }
                        itemInt.setDescricaoItem(nome);
                        itemInt.setDetalhe("ID CNPq: " + getAttributeValueSafe(reader, "NRO-ID-CNPQ"));
                        itemInt.setAtividade(atividadeAtual);
                        atividadeAtual.getItens().add(itemInt);
                    }

                    /**
                     *  ORIENTAÇÕES E BANCAS
                     */

                    // orientações
                    else if (isTagOrientacao(currentElement) || isTagBanca(currentElement)) {
                        atividadeAtual = new Atividade();
                        atividadeAtual.setTipoAtividades(getAtividadeBancoName(currentElement));

                        detalheInstituicao = null;
                        detalheOrientado = null;
                        detalheAgencia = null;
                        detalheCandidato = null;
                    }

                    else if (currentElement.startsWith("DADOS-BASICOS-") && atividadeAtual != null && !isProjeto(atividadeAtual.getTipoAtividades())) {
                        atividadeAtual.setAnoInicio(parseIntSafe(getAttributeValueSafe(reader, "ANO")));
                        atividadeAtual.setMesInicio(12);
                        atividadeAtual.setAnoFim(atividadeAtual.getAnoInicio());

                        AtividadeItem itemTitulo = new AtividadeItem();
                        itemTitulo.setTipoItem(atividadeAtual.getTipoAtividades().startsWith("ORIENTACAO") ? "TITULO_ORIENTACAO" : "TITULO_BANCA");
                        itemTitulo.setDescricaoItem(getAttributeValueSafe(reader, "TITULO"));
                        itemTitulo.setAtividade(atividadeAtual);
                        atividadeAtual.getItens().add(itemTitulo);

                    } else if (currentElement.startsWith("DETALHAMENTO-") && atividadeAtual != null && !isProjeto(atividadeAtual.getTipoAtividades())) {
                        detalheInstituicao = getAttributeValueSafe(reader, "NOME-DA-INSTITUICAO");
                        detalheOrientado = getAttributeValueSafe(reader, "NOME-DO-ORIENTADO");
                        detalheAgencia = getAttributeValueSafe(reader, "NOME-DA-AGENCIA");
                        detalheCandidato = getAttributeValueSafe(reader, "NOME-DO-CANDIDATO");
                    }

                    break;

                case XMLStreamConstants.END_ELEMENT:
                    String endElement = reader.getLocalName();

                    if ("DADOS-GERAIS".equals(endElement)) {
                    }
                    // FECHA FORMAÇÃO
                    else if (formacaoAtual != null && formacaoAtual.getTipoFormacao().equals(endElement)) {
                        curriculo.adicionarFormacao(formacaoAtual);
                        formacaoAtual = null;
                    }
                    // FECHA ATUAÇÃO
                    else if ("ATUACAO-PROFISSIONAL".equals(endElement) && atuacaoAtual != null) {
                        curriculo.adicionarAtuacao(atuacaoAtual);
                        atuacaoAtual = null;
                    }
                    // FECHA PRODUÇÕES
                    else if ("ARTIGO-PUBLICADO".equals(endElement) && producaoAtual != null) {
                        producaoAtual.setNomeVeiculo(FiltroSimilaridade.verificaVeiculo(detalhePeriodico));
                        producaoAtual.setIsbnIssn(validarISSN(detalheISSN));
                        if (detalheVol == null) detalheVol = "";
                        if (detalhePag == null) detalhePag = "";
                        producaoAtual.setVolumePaginas("Vol: " + detalheVol + ", p.: " + detalhePag);
                        curriculo.adicionarProducao(producaoAtual);
                        producaoAtual = null;
                    } else if ("LIVRO-PUBLICADO-OU-ORGANIZADO".equals(endElement) && producaoAtual != null) {
                        producaoAtual.setNomeVeiculo(FiltroSimilaridade.verificaVeiculo(detalheEditora));
                        producaoAtual.setIsbnIssn(detalheISBN);
                        producaoAtual.setVolumePaginas(detalhePag + " págs");
                        curriculo.adicionarProducao(producaoAtual);
                        producaoAtual = null;
                    } else if ("TRABALHO-EM-EVENTOS".equals(endElement) && producaoAtual != null) {
                        producaoAtual.setNomeEvento(detalheNomeEvento);
                        producaoAtual.setNomeVeiculo(FiltroSimilaridade.verificaVeiculo(detalheAnais));
                        curriculo.adicionarProducao(producaoAtual);
                        producaoAtual = null;
                    }
                    // FECHA PROJETOS (Mantendo o fallback temporal exato da auditoria anterior)
                    else if (("PROJETO-DE-PESQUISA".equals(endElement) || "PROJETO-DE-EXTENSAO".equals(endElement)) && atividadeAtual != null) {
                        Integer anoProjeto = atividadeAtual.getAnoInicio() != null ? atividadeAtual.getAnoInicio() : LocalDate.now().getYear();
                        Atuacao atuacaoPai = encontrarOuCriarAtuacao(curriculo, "PROJETOS (" + atividadeAtual.getTipoAtividades() + ")", anoProjeto, true);
                        atividadeAtual.setAtuacao(atuacaoPai);
                        atuacaoPai.getAtividades().add(atividadeAtual);
                        atividadeAtual = null;
                    }
                    // FECHA ORIENTAÇÕES
                    else if (isTagOrientacao(endElement) && atividadeAtual != null) {
                        Atuacao atuacaoPai = encontrarOuCriarAtuacao(curriculo, detalheInstituicao, atividadeAtual.getAnoInicio(), false);
                        atividadeAtual.setAtuacao(atuacaoPai);
                        atuacaoPai.getAtividades().add(atividadeAtual);

                        AtividadeItem itemAluno = new AtividadeItem();
                        itemAluno.setTipoItem("ALUNO_ORIENTADO");
                        itemAluno.setDescricaoItem(detalheOrientado);
                        if (detalheAgencia != null && !detalheAgencia.isEmpty()) itemAluno.setDetalhe("Bolsista: " + detalheAgencia);
                        itemAluno.setAtividade(atividadeAtual);
                        atividadeAtual.getItens().add(itemAluno);
                        atividadeAtual = null;
                    }
                    // FECHA BANCAS
                    else if (isTagBanca(endElement) && atividadeAtual != null) {
                        if (detalheCandidato != null && !detalheCandidato.isEmpty()) {
                            AtividadeItem itemCandidato = new AtividadeItem();
                            itemCandidato.setTipoItem("CANDIDATO");
                            itemCandidato.setDescricaoItem(detalheCandidato);
                            itemCandidato.setAtividade(atividadeAtual);
                            atividadeAtual.getItens().add(itemCandidato);
                        }
                        Atuacao atuacaoPai = encontrarOuCriarAtuacao(curriculo, detalheInstituicao, atividadeAtual.getAnoInicio(), false);
                        atividadeAtual.setAtuacao(atuacaoPai);
                        atuacaoPai.getAtividades().add(atividadeAtual);
                        atividadeAtual = null;
                    }
                    break;
            }
        }
        // fecha/encerra o ponteiro/cursor
        reader.close();

        // ordena as atividades por data (mais recente primeiro)
        ordenarAtividades(curriculo);

        return curriculo;
    }

    // --- MÉTODOS AUXILIARES E DE HIGIENIZAÇÃO ---

    private String getAttributeValueSafe(XMLStreamReader reader, String attributeName) {
        String val = reader.getAttributeValue(null, attributeName);
        return val != null ? val : "";
    }


    private String validarISSN(String issn) {
        if (issn == null) return null;
        String limpo = issn.replaceAll("[^0-9X]", "");
        if (limpo.length() == 8) {
            return limpo.substring(0,4) + "-" + limpo.substring(4);
        }
        return null; // issn invalido, não salvar
    }

    private Integer validarAno(Integer ano) {
        int anoAtual = LocalDate.now().getYear();
        if (ano == null || ano < 1900 || ano > anoAtual) return anoAtual;
        else {
            return ano;
        }
    }

    private String limparDoiValido(String doi) {
        if (doi == null || doi.trim().isEmpty()) return null;
        String limpo = doi.replaceAll("(?i)https?://(dx\\.)?doi\\.org/", "")
                .replaceAll("(?i)doi:", "")
                .trim();
        if (limpo.startsWith("10.") && limpo.contains("/")) {
            return limpo;
        }
        return null;
    }

    private boolean isTagOrientacao(String tag) {
        return "ORIENTACAO-CONCLUIDA-DE-MESTRADO".equals(tag) ||
                "ORIENTACAO-CONCLUIDA-DE-DOUTORADO".equals(tag) ||
                "OUTRAS-ORIENTACOES-CONCLUIDAS".equals(tag);
    }

    private boolean isTagBanca(String tag) {
        return "PARTICIPACAO-EM-BANCA-DE-MESTRADO".equals(tag) ||
                "PARTICIPACAO-EM-BANCA-DE-DOUTORADO".equals(tag) ||
                "PARTICIPACAO-EM-BANCA-DE-EXAME-QUALIFICACAO".equals(tag) ||
                "PARTICIPACAO-EM-BANCA-DE-APERFEICOAMENTO-ESPECIALIZACAO".equals(tag) ||
                "PARTICIPACAO-EM-BANCA-DE-GRADUACAO".equals(tag);
    }

    private String getAtividadeBancoName(String tag) {
        switch (tag) {
            case "ORIENTACAO-CONCLUIDA-DE-MESTRADO": return "ORIENTACAO_MESTRADO";
            case "ORIENTACAO-CONCLUIDA-DE-DOUTORADO": return "ORIENTACAO_DOUTORADO";
            case "OUTRAS-ORIENTACOES-CONCLUIDAS": return "ORIENTACAO_OUTRA";
            case "PARTICIPACAO-EM-BANCA-DE-MESTRADO": return "BANCA_MESTRADO";
            case "PARTICIPACAO-EM-BANCA-DE-DOUTORADO": return "BANCA_DOUTORADO";
            case "PARTICIPACAO-EM-BANCA-DE-EXAME-QUALIFICACAO": return "BANCA_QUALIFICACAO";
            case "PARTICIPACAO-EM-BANCA-DE-APERFEICOAMENTO-ESPECIALIZACAO": return "BANCA_ESPECIALIZACAO";
            case "PARTICIPACAO-EM-BANCA-DE-GRADUACAO": return "BANCA_GRADUACAO";
            default: return "OUTRO";
        }
    }

    private boolean isProjeto(String tipo) {
        return "PESQUISA".equals(tipo) || "EXTENSAO".equals(tipo);
    }

    // METODO IMPORTANTE: BLINDA A CONVERSÃO
    private Integer parseIntSafe(String valor) {
        if (valor != null && !valor.isEmpty()) {
            try {
                return Integer.parseInt(valor.trim());
            } catch (NumberFormatException e) {
                logger.debug("Falha na conversão númerica ignorada. Valor recebido: '{}'", valor);
                return null;
            }
        }
        return null;
    }

    private Instituicao tratarInstituicao(String nomeInst) {
        if (nomeInst == null || nomeInst.trim().isEmpty()) {
            nomeInst = "INSTITUIÇÃO NÃO INFORMADA";
        }
        Instituicao inst = new Instituicao();
        inst.setNomeInstituicao(nomeInst);
        return inst;
    }

    private Curso tratarCursoBase(String nomeCurso) {
        if (nomeCurso == null || nomeCurso.trim().isEmpty()) {
            return null;
        }

        String nomeLimpo = limparString(nomeCurso);

        if (nomeLimpo.isEmpty() || nomeLimpo.length() >= 150) {
            return new Curso("Outros / Nome não padronizado");
        }

        // Normaliza contra o cache: se um nome similar já existe, usa o canônico
        String nomeCanônico = FiltroSimilaridade.normalizarNomeCurso(nomeLimpo);
        return new Curso(nomeCanônico);
    }

    private String limparString(String texto) {
        String limpo = texto;

        limpo = limpo.replace("&quot;", "\"")
                .replace("&#09;", " ")
                .replace("&amp;", "&")
                .replace("&#10;", " ")
                .replace("&apos;", "'");

        limpo = limpo.replace("?", "");

        limpo = limpo.replaceAll("^(?:\\d+[a-zA-Zº°\\.\\-]*\\s*)+", "")
                .replaceAll("^['\"\\|\\-\\.\\s]+", "");

        limpo = limpo.replaceAll("['\"\\s]+$", "");
        limpo = capitalizarPalavras(limpo);

        return limpo.trim();
    }

    private String limparTextoLongo(String texto, int limiteMaximo) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }

        String limpo = texto.replace("&#10;", " ")
                .replace("&#09;", " ")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");

        limpo = limpo.replaceAll("\\s+", " ").trim();

        if (limpo.length() > limiteMaximo) {
            limpo = limpo.substring(0, limiteMaximo - 5) + "...";
        }

        return limpo;
    }

    private String capitalizarPalavras(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }

        texto = texto.replace("_", " ");

        StringBuilder resultado = new StringBuilder();
        boolean capitalizarProxima = true;

        for (char c : texto.toLowerCase().toCharArray()) {
            if (Character.isSpaceChar(c) || c == '-') {
                capitalizarProxima = true;
            } else if (capitalizarProxima) {
                c = Character.toTitleCase(c);
                capitalizarProxima = false;
            }
            resultado.append(c);
        }

        return resultado.toString().trim();
    }

    private void ordenarAtividades(Curriculo cv) {
        if (cv.getAtuacoes() != null) {
            for (Atuacao at : cv.getAtuacoes()) {
                if (at.getAtividades() != null && !at.getAtividades().isEmpty()) {
                    at.getAtividades().sort((a1, a2) -> {
                        int ano1 = a1.getAnoInicio() != null ? a1.getAnoInicio() : 0;
                        int ano2 = a2.getAnoInicio() != null ? a2.getAnoInicio() : 0;
                        int comp = Integer.compare(ano2, ano1);
                        if (comp != 0) return comp;
                        int mes1 = a1.getMesInicio() != null ? a1.getMesInicio() : 0;
                        int mes2 = a2.getMesInicio() != null ? a2.getMesInicio() : 0;
                        return Integer.compare(mes2, mes1);
                    });
                }
            }
        }
    }

    private Atuacao encontrarOuCriarAtuacao(Curriculo cv, String nomeInstituicao, Integer anoReferencia, boolean isProjeto) {
        if (nomeInstituicao == null || nomeInstituicao.trim().isEmpty()) {
            nomeInstituicao = "INSTITUIÇÃO NÃO INFORMADA";
        }

        if (cv.getAtuacoes() != null) {
            for (Atuacao at : cv.getAtuacoes()) {
                if (FiltroSimilaridade.isMesmaInstituicao(
                        at.getInstituicao().getNomeInstituicao(), nomeInstituicao)) {
                    return at;
                }
            }
        }
        Atuacao nova = new Atuacao();
        nova.setCurriculo(cv);
        nova.setSequencia(isProjeto ? 99 : 1);
        Instituicao inst = new Instituicao();
        inst.setNomeInstituicao(nomeInstituicao);
        nova.setInstituicao(inst);

        Vinculo v = new Vinculo();
        v.setAtuacao(nova);
        v.setTipoVinculo(isProjeto ? "PROJETOS / ATIVIDADES" : "VÍNCULO INSTITUCIONAL");
        if (isProjeto) v.setDescEnquadramento("Agrupamento Automático");

        v.setAnoInicio(anoReferencia);
        v.setAnoFim(anoReferencia);
        v.setFlagVinculoEmpregaticio("N");

        nova.getVinculos().add(v);
        cv.getAtuacoes().add(nova);
        return nova;
    }

    private boolean isNivelAcademicoValido(String tagName) {
        return "GRADUACAO".equals(tagName) ||
                "MESTRADO".equals(tagName) ||
                "DOUTORADO".equals(tagName) ||
                "POS-DOUTORADO".equals(tagName) ||
                "ESPECIALIZACAO".equals(tagName);
    }

    private String gerarHash(String texto) {
        if (texto == null || texto.isEmpty()) return null;

        try {
            String limpo = texto.toLowerCase().replaceAll("\\s+", "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(limpo.getBytes(StandardCharsets.UTF_8));

            return String.format("%064x", new BigInteger(1, messageDigest));
        } catch (Exception e) {
            return null;
        }
    }
}

