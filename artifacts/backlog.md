# 📋 Backlog — SIGLattes

---

## ✅ CONCLUÍDOS

### ✅ #1 — Filtro temporal nas produções por instituição
**Implementado em:** 2026-06-09
**O que foi feito:**
- Cruzamento de `p.ano` com o período do `Vinculo` (datas de início/fim da atuação) injetado nas queries.
- Alterado `RelatorioDAO.java`, `ProducaoDAO.java` e `ExtratorVM.java`.
- As produções agora só contam para a instituição se publicadas dentro do período de vínculo ativo do pesquisador.

### ✅ Correção Crítica — NPE em Lote e Bug de Cache do Hibernate
**Implementado em:** 2026-06-09
**O que foi feito:**
- Resolvido `NullPointerException` em `LattesParser.java` que derrubava extração em lote por causa de DOI nulo.
- Restaurado vínculo bidirecional `producaoAtual.setCurriculo(curriculo)` no parser.
- Corrigido erro `Multiple representations of the same entity [Instituicao]` revivendo objetos desanexados do cache via `session.get()` em `InstituicaoDAO.java`.

### ✅ #2 — Deduplicação de produções por similaridade de título (Levenshtein)
**Implementado em:** 2026-06-03
**O que foi feito:**
- `isMesmaProducao()` em `FiltroSimilaridade.java` — tolerância proporcional (`Math.min(8, Math.max(2, len/20))`)
- Cache indexado por primeira palavra (`cacheTitulosPorPrefixo`) — reduz O(55k) para O(~200) por lookup
- Hash recalculado automaticamente sobre o título canônico — `COUNT(DISTINCT hashTitulo)` passa a ser confiável
- `contarProducoesUnicas()` em `ProducaoDAO.java` — deduplicação DOI-first + Levenshtein para relatórios

### ✅ #3 — Integrar `isMesmoCurso()` ao fluxo de normalização
**Implementado em:** 2026-06-03
**O que foi feito:**
- `normalizarNomeCurso()` + `cacheCursos` (LinkedHashSet) em `FiltroSimilaridade.java`

---

## 🔴 PENDENTES

### 🚨 Prioridade 1: Falhas Críticas de Segurança (Risco de Vazamento e Invasão)
- **[SEG-01] Credenciais no Código:** Remover senhas hardcoded do banco de dados no `hibernate.cfg.xml` e migrar para variáveis de ambiente.
- **[SEG-02] Injeção LDAP:** Corrigir Injeção LDAP no `UemLdapService`, que hoje concatena o usuário diretamente na string de busca, permitindo bypass da autenticação.
- **[SEG-03] Execução Arbitrária de SQL:** Remover ou travar totalmente a interface web de execução de SQL (`ConsultaSqlVM`) que confere risco extremo de exfiltração de dados no banco.
- **[SEG-04] Bypass de Filtro:** Corrigir a brecha do `SecurityFilter` para cobrir recursos do framework ZK (`/zkau` e não apenas `*.zul`), evitando bypass da tela de login via chamadas assíncronas diretas.

### 🔴 Prioridade 2: Defeitos Críticos de Qualidade de Dados (Corrupção ou Queda)
- **[DAD-09] NPE Oculto em Log:** Em `LattesService.java` (linha 193), o log tenta acessar `resposta` nula ao tratar ID vazio da API do CNPq, causando uma exceção que mascara o erro original de integração SOAP.

### 🟠 Prioridade 3: Falhas Médias/Altas (Configurações, Comunicação Insegura, XSS)
- **[SEG-05] Comunicação em Texto Claro:** O uso de LDAP e SOAP ocorre em texto livre na rede (`http://` e `ldap://`). Deve-se forçar TLS (`https://` e `ldaps://`).
- **[SEG-06] XSS via ZK JavaScript:** O `RelatorioDinamicoVM` injeta strings direto no `evalJavaScript` por concatenação. É preciso usar serialização segura (Gson/Jackson) para evitar scripts injetados.
- **[SEG-07] Hibernate DDL:** No `hibernate.cfg.xml`, `hbm2ddl.auto` está como `update`. Mudar para `none` ou `validate` para evitar alteração em tabela de produção.
- **[DAD-02] Sujeira em Volume/Página:** O parser de Lattes gera strings sujas (`"-"` ou `"Vol: , p.: "`) se as páginas/volumes não constarem no XML.
- **[DAD-08] Retorno Null perigoso:** `CursoDAO.listarTodos()` retorna `null` caso ocorra falha de comunicação com o DB, em vez de retornar Lista Vazia.
- **[DAD-13] Divisão por Zero no Levenshtein:** A função `FiltroSimilaridade.distanciaRelativa()` gera `NaN` se ambas as strings comparadas forem vazias, quebrando deduplicações de eventos/veículos sem título.

### 🟡 Prioridade 4: Melhorias Menores de Integridade
- **[DAD-10] Falta de Orientação em Andamento:** Adicionar as tags `ORIENTACAO-EM-ANDAMENTO-DE-MESTRADO/DOUTORADO` no fluxo de extração (hoje não são capturadas).
- **[DAD-14] Constraints Físicas:** Adicionar `@UniqueConstraint` para os campos `nm_curso` (`Curso.java`) e `nomeInstituicao` (`Instituicao.java`).
- **[DAD-15] Limites de Tamanho Livres:** Filtrar carga horária e campos de tipo de vínculo para barrar textos longos ou não-identificados (ex: "N/A").

---

### #4 — Normalizar nome do evento (`detalheNomeEvento`)

**Contexto:**
O campo `nomeEvento` de produções do tipo `TRABALHO-EM-EVENTOS` é preenchido manualmente
e não possui nenhum filtro de similaridade. O mesmo evento pode aparecer de formas distintas.

**O que falta:**
- Adicionar `isMesmoEvento()` em `FiltroSimilaridade`
- Criar cache + `normalizarNomeEvento()` 
- Chamar em `LattesParser.java` ao setar `producaoAtual.setNomeEvento(detalheNomeEvento)`

---

## 📊 Mapa completo de cobertura dos filtros de similaridade

| Campo no Lattes | Preenchimento | Método | Cache | Integrado ao parser |
|---|---|---|---|---|
| Nome da instituição | Manual | `isMesmaInstituicao()` | Via `InstituicaoDAO` | ✅ |
| Nome do veículo (periódico/editora/anais) | Manual | `isMesmoVeiculo()` | `cacheVeiculos` | ✅ |
| Título da produção (artigo/livro/evento) | Manual | `isMesmaProducao()` | `cacheTitulosPorPrefixo` | ✅ |
| Nome do curso (formação) | Manual | `isMesmoCurso()` | `cacheCursos` | ✅ |
| **Nome do evento** | Manual | ❌ não existe | ❌ | ❌ **Backlog #4** |
| Nome do orientado | Manual | ❌ não aplicável* | — | — |
| Palavras-chave / área | Controlado† | — | — | Baixo risco |

> \* Nome de pessoas orientadas: variação não causa duplicação de entidade — são items de atividade, não entidades deduplicáveis.
> † Áreas do conhecimento usam tabela controlada do CNPq, risco de variação é baixo.
