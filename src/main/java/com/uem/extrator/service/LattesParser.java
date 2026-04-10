package com.uem.extrator.service;

import com.uem.extrator.model.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.mail.Message;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.uem.extrator.model.Curriculo;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LattesParser {

    // instância logger
    private static final Logger logger = LoggerFactory.getLogger(LattesParser.class);

    public Curriculo parse(String xmlConteudo, String idLattes) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

        // BLINDAGEM CONTRA XXE //

        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setXIncludeAware(false);
        dbFactory.setExpandEntityReferences(false);

        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        InputSource is = new InputSource(new StringReader(xmlConteudo));
        Document doc = dBuilder.parse(is);
        doc.getDocumentElement().normalize();

        Curriculo curriculo = new Curriculo();
        curriculo.setIdLattes(idLattes);

        extrairDadosGerais(doc, curriculo);
        extrairFormacao(doc, curriculo);
        extrairProducoesBibliograficas(doc, curriculo);
        extrairAtuacoes(doc, curriculo);
        extrairAtividades(doc, curriculo);
        extrairOrientacoes(doc, curriculo);
        extrairBancas(doc, curriculo);

        ordenarAtividades(curriculo);


        return curriculo;
    }

    private void extrairDadosGerais(Document doc, Curriculo cv) {
        try {
            NodeList nList = doc.getElementsByTagName("DADOS-GERAIS");
            if (nList.getLength() > 0) {
                Element dados = (Element) nList.item(0);
                cv.setNomeCompleto(dados.getAttribute("NOME-COMPLETO"));
                // proteção de tamanho
                String nomeCitacao = dados.getAttribute("NOME-EM-CITACOES-BIBLIOGRAFICAS");
                if (nomeCitacao != null && nomeCitacao.length() > 1000) {
                    nomeCitacao = nomeCitacao.substring(0, 995) + "...";
                }
                //                          //
                cv.setNomeCitacao(nomeCitacao);

                String orcidBruto = dados.getAttribute("ORCID-ID");
                if (orcidBruto != null && !orcidBruto.trim().isEmpty()) {
                    // padrão oficial do orcid, 4 blocos de 4 dígitos (o ultimo pode se X)
                    Matcher m = Pattern
                            .compile("([0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{3}[0-9X])")
                            .matcher(orcidBruto.toUpperCase());

                    if (m.find()) {
                        cv.setOrcid(m.group(1));    // salva apenas os 16 dígitos limpos
                    }
                }

                NodeList nResumo = dados.getElementsByTagName("RESUMO-CV");
                if (nResumo.getLength() > 0) {
                    Element resumo = (Element) nResumo.item(0);
                    String texto = resumo.getAttribute("TEXTO-RESUMO");
                    if (texto == null || texto.trim().isEmpty()) {
                        texto = resumo.getAttribute("TEXTO-RESUMO-CV-RH");
                    }
                    cv.setResumo(texto);
                }
            }

            // Data de Atualização
            String dataStr = doc.getDocumentElement().getAttribute("DATA-ATUALIZACAO");
            if (dataStr != null && dataStr.length() == 8) {
                SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy");
                cv.setDataAtualizacao(sdf.parse(dataStr));
            } else {
                cv.setDataAtualizacao(new Date());
            }
        } catch (Exception e) {
            logger.error("Erro crítico ao extrair dados gerais do Lattes ID: {}", cv.getIdLattes(), e);
            cv.setDataAtualizacao(new Date());
        }
    }

    private void extrairFormacao(Document doc, Curriculo cv) {
        NodeList nListFormacao = doc.getElementsByTagName("FORMACAO-ACADEMICA-TITULACAO");
        if (nListFormacao.getLength() > 0) {
            Element formacaoAcademica = (Element) nListFormacao.item(0);
            NodeList tiposFormacao = formacaoAcademica.getChildNodes();
            for (int i = 0; i < tiposFormacao.getLength(); i++) {
                Node node = tiposFormacao.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element elementoFormacao = (Element) node;
                    String tagName = elementoFormacao.getTagName();
                    Formacao formacao = new Formacao();
                    formacao.setTipoFormacao(tagName);
                    String nomeCursoStr = elementoFormacao.getAttribute("NOME-CURSO");
                    String nomeInstStr = elementoFormacao.getAttribute("NOME-INSTITUICAO");
                    formacao.setNomeInstituicao(tratarInstituicao(nomeInstStr));
                    if ((nomeCursoStr == null || nomeCursoStr.isEmpty()) && tagName.equals("POS-DOUTORADO")) {
                        nomeCursoStr = "Pós-Doutorado em Pesquisa";
                    }
                    formacao.setNomeCurso(tratarCurso(nomeCursoStr, elementoFormacao));
                    formacao.setStatus(elementoFormacao.getAttribute("STATUS-DO-CURSO"));
                    formacao.setAnoInicio(parseIntSafe(elementoFormacao.getAttribute("ANO-DE-INICIO")));
                    formacao.setAnoConclusao(parseIntSafe(elementoFormacao.getAttribute("ANO-DE-CONCLUSAO")));
                    cv.adicionarFormacao(formacao);
                }
            }
        }
    }

    private void extrairProducoesBibliograficas(Document doc, Curriculo cv) {
        NodeList grupoProducao = doc.getElementsByTagName("PRODUCAO-BIBLIOGRAFICA");
        if (grupoProducao.getLength() == 0) return;
        Element raizProd = (Element) grupoProducao.item(0);

        // ARTIGOS
        NodeList artigos = raizProd.getElementsByTagName("ARTIGO-PUBLICADO");
        for (int i = 0; i < artigos.getLength(); i++) {
            Element item = (Element) artigos.item(i);
            Element dados = (Element) item.getElementsByTagName("DADOS-BASICOS-DO-ARTIGO").item(0);
            Element detalhe = (Element) item.getElementsByTagName("DETALHAMENTO-DO-ARTIGO").item(0);
            if (dados != null) {
                Producao p = new Producao();
                p.setTipo("ARTIGO");
                p.setTitulo(dados.getAttribute("TITULO-DO-ARTIGO"));
                p.setHashTitulo(gerarHash(dados.getAttribute("TITULO-DO-ARTIGO")));
                p.setAno(parseIntSafe(dados.getAttribute("ANO-DO-ARTIGO")));
                p.setPais(dados.getAttribute("PAIS-DE-PUBLICACAO"));
                p.setIdioma(dados.getAttribute("IDIOMA"));
                // Tratamento de DOIs gigantes
                String doiLattes = dados.getAttribute("DOI");
                if (doiLattes != null && doiLattes.length() > 200) {
                    doiLattes = doiLattes.substring(0, 195) + "...";
                }
                p.setDoi(doiLattes);
                //                            //
                if (detalhe != null) {
                    p.setNomeVeiculo(detalhe.getAttribute("TITULO-DO-PERIODICO-OU-REVISTA"));
                    p.setIsbnIssn(detalhe.getAttribute("ISSN"));
                    String vol = detalhe.getAttribute("VOLUME");
                    String pag = detalhe.getAttribute("PAGINA-INICIAL") + "-" + detalhe.getAttribute("PAGINA-FINAL");
                    p.setVolumePaginas("Vol: " + vol + ", p.: " + pag);
                }
                cv.adicionarProducao(p);
            }
        }

        // LIVROS
        NodeList livros = raizProd.getElementsByTagName("LIVRO-PUBLICADO-OU-ORGANIZADO");
        for (int i = 0; i < livros.getLength(); i++) {
            Element item = (Element) livros.item(i);
            Element dados = (Element) item.getElementsByTagName("DADOS-BASICOS-DO-LIVRO").item(0);
            Element detalhe = (Element) item.getElementsByTagName("DETALHAMENTO-DO-LIVRO").item(0);
            if (dados != null) {
                Producao p = new Producao();
                p.setTipo("LIVRO");
                p.setTitulo(dados.getAttribute("TITULO-DO-LIVRO"));
                p.setHashTitulo(gerarHash(dados.getAttribute("TITULO-DO-LIVRO")));
                p.setAno(parseIntSafe(dados.getAttribute("ANO")));
                p.setPais(dados.getAttribute("PAIS-DE-PUBLICACAO"));
                p.setIdioma(dados.getAttribute("IDIOMA"));
                if (detalhe != null) {
                    p.setNomeVeiculo(detalhe.getAttribute("NOME-DA-EDITORA"));
                    p.setIsbnIssn(detalhe.getAttribute("ISBN"));
                    p.setVolumePaginas(detalhe.getAttribute("NUMERO-DE-PAGINAS") + " págs");
                }
                cv.adicionarProducao(p);
            }
        }

        // EVENTOS
        NodeList eventos = raizProd.getElementsByTagName("TRABALHO-EM-EVENTOS");
        for (int i = 0; i < eventos.getLength(); i++) {
            Element item = (Element) eventos.item(i);
            Element dados = (Element) item.getElementsByTagName("DADOS-BASICOS-DO-TRABALHO").item(0);
            Element detalhe = (Element) item.getElementsByTagName("DETALHAMENTO-DO-TRABALHO").item(0);
            if (dados != null) {
                Producao p = new Producao();
                p.setTipo("EVENTO");
                p.setTitulo(dados.getAttribute("TITULO-DO-TRABALHO"));
                p.setHashTitulo(gerarHash(dados.getAttribute("TITULO-DO-TRABALHO")));
                p.setAno(parseIntSafe(dados.getAttribute("ANO-DO-TRABALHO")));
                p.setPais(dados.getAttribute("PAIS-DO-EVENTO"));
                p.setNatureza(dados.getAttribute("NATUREZA"));
                if (detalhe != null) {
                    p.setNomeEvento(detalhe.getAttribute("NOME-DO-EVENTO"));
                    p.setNomeVeiculo(detalhe.getAttribute("TITULO-DOS-ANAIS-OU-PROCEEDINGS"));
                }
                cv.adicionarProducao(p);
            }
        }
    }

    private void extrairAtuacoes(Document doc, Curriculo cv) {
        NodeList listaAtuacoes = doc.getElementsByTagName("ATUACAO-PROFISSIONAL");
        for (int i = 0; i < listaAtuacoes.getLength(); i++) {
            Element elAtuacao = (Element) listaAtuacoes.item(i);
            Atuacao atuacao = new Atuacao();
            String nomeInst = elAtuacao.getAttribute("NOME-INSTITUICAO");
            atuacao.setInstituicao(tratarInstituicao(nomeInst));

            // CORREÇÃO AQUI: Trocado Integer.parseInt por parseIntSafe
            // Isso evita o crash se o campo vier sujo ou com texto
            String seqStr = elAtuacao.getAttribute("SEQUENCIA-IMPORTANCIA");
            atuacao.setSequencia(parseIntSafe(seqStr));

            NodeList listaVinculos = elAtuacao.getElementsByTagName("VINCULOS");
            for (int j = 0; j < listaVinculos.getLength(); j++) {
                Element elVinculo = (Element) listaVinculos.item(j);
                Vinculo vinculo = new Vinculo();
                vinculo.setTipoVinculo(elVinculo.getAttribute("OUTRO-VINCULO-INFORMADO"));
                // passa o conteudo por um metodo de sanitização de dados antes de setar //
                vinculo.setDescEnquadramento(limparTextoLongo(elVinculo.getAttribute("OUTRAS-INFORMACOES"), 2000));
                // --------------------------------------------------------------------- //
                vinculo.setDescCargaHoraria(elVinculo.getAttribute("CARGA-HORARIA-SEMANAL"));
                String flag = elVinculo.getAttribute("FLAG-VINCULO-E" +
                        "MPREGATICIO");
                vinculo.setFlagVinculoEmpregaticio("SIM".equalsIgnoreCase(flag) ? "S" : "N");
                vinculo.setAnoInicio(parseIntSafe(elVinculo.getAttribute("ANO-INICIO")));
                vinculo.setMesInicio(parseIntSafe(elVinculo.getAttribute("MES-INICIO")));
                vinculo.setAnoFim(parseIntSafe(elVinculo.getAttribute("ANO-FIM")));
                vinculo.setMesFim(parseIntSafe(elVinculo.getAttribute("MES-FIM")));
                atuacao.adicionarVinculo(vinculo);
            }
            cv.adicionarAtuacao(atuacao);
        }
    }

    private void extrairAtividades(Document doc, Curriculo cv) {
        NodeList nPesquisa = doc.getElementsByTagName("PROJETO-DE-PESQUISA");
        processarProjetos(nPesquisa, cv, "PESQUISA");
        NodeList nExtensao = doc.getElementsByTagName("PROJETO-DE-EXTENSAO");
        processarProjetos(nExtensao, cv, "EXTENSAO");
    }

    private void extrairOrientacoes(Document doc, Curriculo cv) {
        String[][] tipos = {
                {"ORIENTACAO-CONCLUIDA-DE-MESTRADO", "ORIENTACAO_MESTRADO"},
                {"ORIENTACAO-CONCLUIDA-DE-DOUTORADO", "ORIENTACAO_DOUTORADO"},
                {"OUTRAS-ORIENTACOES-CONCLUIDAS", "ORIENTACAO_OUTRA"}
        };
        for (String[] tipo : tipos) {
            String tagXml = tipo[0];
            String nomeAtividadeBanco = tipo[1];
            NodeList nList = doc.getElementsByTagName(tagXml);
            if (nList == null || nList.getLength() == 0) continue;
            for (int i = 0; i < nList.getLength(); i++) {
                Element el = (Element) nList.item(i);
                Element dadosBasicos = (Element) el.getElementsByTagName("DADOS-BASICOS-DA-" + tagXml).item(0);
                Element detalhamento = (Element) el.getElementsByTagName("DETALHAMENTO-DA-" + tagXml).item(0);
                if (dadosBasicos == null || detalhamento == null) continue;
                Atividade atv = new Atividade();
                atv.setTipoAtividades(nomeAtividadeBanco);
                atv.setAnoInicio(parseIntSafe(dadosBasicos.getAttribute("ANO")));
                atv.setMesInicio(12);
                atv.setAnoFim(atv.getAnoInicio());
                String titulo = dadosBasicos.getAttribute("TITULO");
                AtividadeItem itemTitulo = new AtividadeItem();
                itemTitulo.setTipoItem("TITULO_ORIENTACAO");
                itemTitulo.setDescricaoItem(titulo);
                itemTitulo.setAtividade(atv);
                atv.getItens().add(itemTitulo);
                String nomeInst = detalhamento.getAttribute("NOME-DA-INSTITUICAO");
                Atuacao atuacaoPai = encontrarOuCriarAtuacao(cv, nomeInst, atv.getAnoInicio(), false);
                atv.setAtuacao(atuacaoPai);
                atuacaoPai.getAtividades().add(atv);
                AtividadeItem itemAluno = new AtividadeItem();
                itemAluno.setTipoItem("ALUNO_ORIENTADO");
                String nomeAluno = detalhamento.getAttribute("NOME-DO-ORIENTADO");
                itemAluno.setDescricaoItem(nomeAluno);
                String agencia = detalhamento.getAttribute("NOME-DA-AGENCIA");
                if (agencia != null && !agencia.isEmpty()) {
                    itemAluno.setDetalhe("Bolsista: " + agencia);
                }
                itemAluno.setAtividade(atv);
                atv.getItens().add(itemAluno);
            }
        }
    }

    private void extrairBancas(Document doc, Curriculo cv) {
        String[][] tipos = {
                {"PARTICIPACAO-EM-BANCA-DE-MESTRADO", "BANCA_MESTRADO"},
                {"PARTICIPACAO-EM-BANCA-DE-DOUTORADO", "BANCA_DOUTORADO"},
                {"PARTICIPACAO-EM-BANCA-DE-EXAME-QUALIFICACAO", "BANCA_QUALIFICACAO"},
                {"PARTICIPACAO-EM-BANCA-DE-APERFEICOAMENTO-ESPECIALIZACAO", "BANCA_ESPECIALIZACAO"},
                {"PARTICIPACAO-EM-BANCA-DE-GRADUACAO", "BANCA_GRADUACAO"}
        };
        for (String[] tipo : tipos) {
            String tagXml = tipo[0];
            String nomeAtividadeBanco = tipo[1];
            NodeList nList = doc.getElementsByTagName(tagXml);
            if (nList == null || nList.getLength() == 0) continue;
            for (int i = 0; i < nList.getLength(); i++) {
                Element el = (Element) nList.item(i);
                Element dadosBasicos = (Element) el.getElementsByTagName("DADOS-BASICOS-DA-" + tagXml).item(0);
                Element detalhamento = (Element) el.getElementsByTagName("DETALHAMENTO-DA-" + tagXml).item(0);
                if (dadosBasicos == null || detalhamento == null) continue;
                Atividade atv = new Atividade();
                atv.setTipoAtividades(nomeAtividadeBanco);
                atv.setAnoInicio(parseIntSafe(dadosBasicos.getAttribute("ANO")));
                atv.setMesInicio(12);
                atv.setAnoFim(atv.getAnoInicio());
                String titulo = dadosBasicos.getAttribute("TITULO");
                AtividadeItem itemTitulo = new AtividadeItem();
                itemTitulo.setTipoItem("TITULO_BANCA");
                itemTitulo.setDescricaoItem(titulo);
                itemTitulo.setAtividade(atv);
                atv.getItens().add(itemTitulo);
                String nomeCandidato = detalhamento.getAttribute("NOME-DO-CANDIDATO");
                if (nomeCandidato != null && !nomeCandidato.isEmpty()) {
                    AtividadeItem itemCandidato = new AtividadeItem();
                    itemCandidato.setTipoItem("CANDIDATO");
                    itemCandidato.setDescricaoItem(nomeCandidato);
                    itemCandidato.setAtividade(atv);
                    atv.getItens().add(itemCandidato);
                }
                String nomeInst = detalhamento.getAttribute("NOME-DA-INSTITUICAO");
                Atuacao atuacaoPai = encontrarOuCriarAtuacao(cv, nomeInst, atv.getAnoInicio(), false);
                atv.setAtuacao(atuacaoPai);
                atuacaoPai.getAtividades().add(atv);
            }
        }
    }

    private void processarProjetos(NodeList nList, Curriculo cv, String tipo) {
        if (nList == null || nList.getLength() == 0) return;
        for (int i = 0; i < nList.getLength(); i++) {
            Element el = (Element) nList.item(i);
            Atividade atv = new Atividade();
            atv.setTipoAtividades(tipo);
            atv.setAnoInicio(parseIntSafe(el.getAttribute("ANO-INICIO")));
            atv.setMesInicio(parseIntSafe(el.getAttribute("MES-INICIO")));
            String anoFim = el.getAttribute("ANO-FIM");
            if (anoFim != null && !anoFim.isEmpty()) {
                atv.setAnoFim(parseIntSafe(anoFim));
                atv.setMesFim(parseIntSafe(el.getAttribute("MES-FIM")));
            }

            // Areas
            NodeList nAreas = el.getElementsByTagName("AREAS-DO-CONHECIMENTO");
            if (nAreas != null && nAreas.getLength() > 0) {
                Element areaRoot = (Element) nAreas.item(0);
                NodeList areasList = areaRoot.getElementsByTagName("AREA-DO-CONHECIMENTO-1");
                if (areasList != null && areasList.getLength() > 0) {
                    Element areaEl = (Element) areasList.item(0);
                    atv.setNomeGrandeArea(areaEl.getAttribute("NOME-GRANDE-AREA-DO-CONHECIMENTO"));
                    atv.setNomeArea(areaEl.getAttribute("NOME-DA-AREA-DO-CONHECIMENTO"));
                }
            }

            AtividadeItem itemNome = new AtividadeItem();
            itemNome.setTipoItem("NOME_PROJETO");
            itemNome.setDescricaoItem(el.getAttribute("NOME-DO-PROJETO"));
            itemNome.setDetalhe(el.getAttribute("DESCRICAO-DO-PROJETO"));
            itemNome.setAtividade(atv);
            atv.getItens().add(itemNome);

            NodeList nEquipe = el.getElementsByTagName("INTEGRANTES-DO-PROJETO");
            if (nEquipe != null) {
                for (int k = 0; k < nEquipe.getLength(); k++) {
                    Element eq = (Element) nEquipe.item(k);
                    AtividadeItem itemInt = new AtividadeItem();
                    itemInt.setTipoItem("INTEGRANTE");
                    String nome = eq.getAttribute("NOME-COMPLETO");
                    if (nome.isEmpty()) nome = eq.getAttribute("NOME-PARA-CITACAO");
                    itemInt.setDescricaoItem(nome);
                    itemInt.setDetalhe("ID CNPq: " + eq.getAttribute("NRO-ID-CNPQ"));
                    itemInt.setAtividade(atv);
                    atv.getItens().add(itemInt);
                }
            }

            Atuacao atuacaoPai = encontrarOuCriarAtuacao(cv, "PROJETOS (" + tipo + ")", 2025, true);
            atv.setAtuacao(atuacaoPai);
            atuacaoPai.getAtividades().add(atv);
        }
    }

    // METODO IMPORTANTE: BLINDA A CONVERSÃO
    private Integer parseIntSafe(String valor) {
        if (valor != null && !valor.isEmpty()) {
            try {
                return Integer.parseInt(valor.trim());
            } catch (NumberFormatException e) {
                // Se der erro (ex: vier texto), apenas ignora e retorna null
                // Isso evita que o sistema quebre.
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

    private Curso tratarCurso(String nomeCurso, Element elementoFormacao) {
        if (nomeCurso == null || nomeCurso.trim().isEmpty()) {
            return null;
        }

        String nomeLimpo = limparString(nomeCurso);

        // se após a limpeza sobrou nada ou se era só um texto gigante
        if (nomeLimpo.isEmpty() || nomeLimpo.length() >= 150) {
            return new Curso("Outros / Nome não padronizado");
        }
        Curso curso = new Curso(nomeLimpo);

        // --- NOVA LÓGICA: EXTRAIR AS ÁREAS DE CONHECIMENTO ---
        if (elementoFormacao != null) {
            NodeList nAreas = elementoFormacao.getElementsByTagName("AREAS-DO-CONHECIMENTO");
            if (nAreas != null && nAreas.getLength() > 0) {
                Element areaRoot = (Element) nAreas.item(0);
                NodeList areasList = areaRoot.getElementsByTagName("AREA-DO-CONHECIMENTO-1");

                if (areasList != null && areasList.getLength() > 0) {
                    Element areaEl = (Element) areasList.item(0);

                    String grandeArea = areaEl.getAttribute("NOME-GRANDE-AREA-DO-CONHECIMENTO");
                    String area = areaEl.getAttribute("NOME-DA-AREA-DO-CONHECIMENTO");
                    String subArea = areaEl.getAttribute("NOME-DA-SUB-AREA-DO-CONHECIMENTO");
                    String especialidade = areaEl.getAttribute("NOME-DA-ESPECIALIDADE");

                    // Atribui os valores ao curso caso eles existam no XML
                    if (grandeArea != null && !grandeArea.isEmpty()) curso.setNomeGrandeArea(capitalizarPalavras(grandeArea));
                    if (area != null && !area.isEmpty()) curso.setNomeArea(capitalizarPalavras(area));
                    if (subArea != null && !subArea.isEmpty()) curso.setNomeSubArea(capitalizarPalavras(subArea));
                    if (especialidade != null && !especialidade.isEmpty()) curso.setNomeEspecialidade(capitalizarPalavras(especialidade));
                }
            }
        }
        return curso;
    }

    // -- METODO DE HIGIENIZAÇÃO DE STRINGS --
    private String limparString(String texto) {
        String limpo = texto;

        // resolve entidades HTML comuns
        limpo = limpo.replace("&quot;", "\"")
                .replace("&#09;", " ") // TABs
                .replace("&amp;", "&")
                .replace("&#10;", " ") // quebra de linhas
                .replace("&apos;", "'");

        // remove sujeiras (ex: ?Preceptoria...?)
        limpo = limpo.replace("?", "");

        // regex: remove pontuações estranhas, pípes, ou números de listas soltos NO INICIO da string
        // Explicando a Regex: ^ significa "no início". Vai remover números soltos, hífens, pipes, aspas ou pontos no começo.
        limpo = limpo.replaceAll("^(?:\\d+[a-zA-Zº°\\.\\-]*\\s*)+", "") // Remove coisas como "1.5 ", "2a ", "8 º "
                .replaceAll("^['\"\\|\\-\\.\\s]+", ""); // Remove ', ", |, -, . ou espaços soltos no começo

        // regex: remove aspas ou espaços isoladas NO FINAL da string
        limpo = limpo.replaceAll("['\"\\s]+$", "");

        // ajuste de capitalização (para padrozinar "DESIGN" ou "design" para "Design"
        limpo = capitalizarPalavras(limpo);

        return limpo.trim();
    }

    private String limparTextoLongo(String texto, int limiteMaximo) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }

        // converte entidades HTML e remove quebras de linha/tabs
        String limpo = texto.replace("&#10;", " ")   // Quebra de linha (Enter)
                .replace("&#09;", " ")   // Tabulação (Tab)
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");

        // colapsa múltiplos espaços gerados pela limpeza acima para apenas 1 espaço
        limpo = limpo.replaceAll("\\s+", " ").trim();

        // aplica limite de caracteres de segurança para não quebrar o banco de dados
        if (limpo.length() > limiteMaximo) {
            limpo = limpo.substring(0, limiteMaximo - 5) + "...";
        }

        return limpo;
    }

    private String capitalizarPalavras(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null; // Retorna nulo se for só espaço em branco para não sujar o banco
        }

        // 1. Substitui os sublinhados irritantes do CNPq por espaços normais
        texto = texto.replace("_", " ");

        StringBuilder resultado = new StringBuilder();
        boolean capitalizarProxima = true;

        for (char c : texto.toLowerCase().toCharArray()) {
            // Se for espaço ou hífen, a próxima letra será maiúscula
            if (Character.isSpaceChar(c) || c == '-') {
                capitalizarProxima = true;
            } else if (capitalizarProxima) {
                // Força a letra a ficar maiúscula
                c = Character.toTitleCase(c);
                capitalizarProxima = false;
            }
            resultado.append(c);
        }

        // Retorna o texto formatado e sem espaços sobrando nas pontas
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
                if (at.getInstituicao() != null &&
                        at.getInstituicao().getNomeInstituicao() != null &&
                        at.getInstituicao().getNomeInstituicao().equalsIgnoreCase(nomeInstituicao)) {
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
        v.setTipoVinculo("VÍNCULO INSTITUCIONAL");
        if (isProjeto) {
            v.setTipoVinculo("PROJETOS / ATIVIDADES");
            v.setDescEnquadramento("Agrupamento Automático");
        }
        v.setAnoInicio(anoReferencia);
        v.setAnoFim(anoReferencia);
        v.setFlagVinculoEmpregaticio("N");
        nova.getVinculos().add(v);
        cv.getAtuacoes().add(nova);
        return nova;
    }

    private String gerarHash(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        try {
            String limpo = texto.toLowerCase().replaceAll("\\s+", "");
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] messageDigest = md.digest(limpo.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            String hashText = no.toString(16);
            while (hashText.length() < 64) {
                hashText = "0" + hashText;
            }
            return hashText;
        } catch (Exception e) {
            return null;
        }
    }
}