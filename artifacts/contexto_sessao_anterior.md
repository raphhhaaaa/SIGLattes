# 🧠 Contexto Completo — Sessão Anterior (SIGLattes)

> Extraído da sessão `5356e837-76b6-4446-b96c-3f1ba4a1f709` (02-03/06/2026) e sessões satélite, com adições da sessão atual (09/06/2026).

---

## 📅 Linha do Tempo da Sessão

- Bug crítico descoberto nos gráficos (`ExtratorVM.java`): Produto cartesiano em `Curriculo c JOIN c.producoes p JOIN c.atuacoes a`. Resolvido com `EXISTS` e `COUNT(DISTINCT hashTitulo)`.
- Discussão técnica de `isMesmaInstituicao()` vs `isMesmoVeiculo()` e implementação de `isMesmaProducao()` proporcional (Backlog #2).

---

### Dia 03/06/2026 — Extração em Lote + Deduplicação
- Bug crítico na extração em lote resolvido (Aumentado timeout de 20s para 60s, retries limitados, sleep aumentado, thread-pool diminuído e `ConcurrentModificationException` curada).
- Implementada deduplicação `DOI-first` em `contarProducoesUnicas()`. O algoritmo Levenshtein não foi colocado no dashboard (O(n²) inaceitável), sendo restrito a relatórios.
- Filtro de similaridade para Títulos e Cursos totalmente implementado, ancorado aos parsers e gerenciamento de cache ativado/invalidado a cada transação (`CurriculoDAO`).

---

### Dia 08/06/2026 — Sessão de Governança, QA e Integração
**Sessão:** `ac7d0651-5c43-4f3b-929a-3284f16368f9`

- Concluída a varredura autônoma via sub-agentes gerando `auditoria_dados.md` e `auditoria_seguranca.md`.
- Adição de `volatile` e métodos `synchronized` no `InstituicaoDAO` (mitigação de Cache Stampede).
- Refatoração do HASH (SHA-256) com `StandardCharsets.UTF_8` cross-OS.
- Inseridas validações ativas de Range de Ano e Regex de limpeza para `DOI`.
- Backup físico do "Brain" para a pasta de artefatos.

---

### Dia 09/06/2026 — Sessão de Correções Críticas e Regras de Negócio
**Sessão:** `ac7d0651-5c43-4f3b-929a-3284f16368f9` (Continuação)

**1. Correção de Queda (NPE) na Extração em Lote:**
Localizado e corrigido um `NullPointerException` oculto no `LattesParser.java:204` decorrente de `.length()` sobre um DOI Nulo. A associação FK bidirecional `producaoAtual.setCurriculo(curriculo)` foi reativada, garantindo integridade e salvamento seguro.

**2. Recuperação de Versionamento (Git):**
Arquivo `hibernate.cfg.xml` acidentalmente dropado, restaurado através do histórico git. Foi realizado um commit `--amend` limpando a árvore git (Hash: `a010b38`).

**3. Implementação do Filtro Temporal por Instituição (Backlog #1):**
Implementada regra de negócio fundamental onde artigos/formações só contabilizam para uma instituição específica (ex: UEM) se lançados **durante o período de atuação** do autor nessa instituição. 
- A cláusula `AND (v.anoInicio IS NULL OR v.anoInicio <= p.ano) AND (v.anoFim IS NULL OR v.anoFim >= p.ano)` foi injetada em 5 queries HQL do `RelatorioDAO.java` e no JOIN de `ProducaoDAO.java`.
- O dashboard hardcoded no `ExtratorVM.java` também foi alinhado para herdar a mesma trava.
- Resultados observados: De 1.909 produções totais no sistema em 2026, 1.571 responderam puramente pelo período letivo em andamento.

**4. Correção de Concorrência de Objetos Detached no Hibernate:**
Foi resolvido o erro `Multiple representations of the same entity [com.uem.extrator.model.Instituicao#162044] are being merged`. 
O `CurriculoDAO` operava transações com currículos que anexavam Instituições originadas do DB (Managed) e do Cache de Levenshtein (Detached). A solução final reconstruiu o lookup no `InstituicaoDAO.java` fazendo `session.get(Instituicao.class, inst.getId())`, uniformizando todos os objetos para a sessão transacional ativa.

**5. Compilação e Git:**
Todas as intervenções renderam `BUILD SUCCESS` via Maven e foram consolidadas com commit: `feat(lattes): filtro temporal de producoes e correcao de merge de instituicao`.

---

## 🗂️ Arquivos Chave do Projeto

| Arquivo | Papel |
|---|---|
| `service/LattesParser.java` | Parser XML principal — ponto de entrada de todos os dados |
| `service/LattesService.java` | Orquestra extração SOAP + inicializa caches |
| `util/FiltroSimilaridade.java` | Todos os filtros Levenshtein + caches |
| `dao/CurriculoDAO.java` | Persistência + invalidação de caches após save |
| `dao/ProducaoDAO.java` | Queries de produção + `contarProducoesUnicas()` |
| `dao/RelatorioDAO.java` | Queries dos relatórios — Filtro Temporal 100% ativo |
| `viewmodel/ExtratorVM.java` | ViewModel principal — gráficos (ajustados p/ temporal), extração em lote |
| `util/ConfigManager.java` | Parâmetros globais (threads, timeout, retries) |

---

## 🔑 Decisões de Design Documentadas

1. **Aprovação obrigatória:** Todo código deve ser mostrado antes de aplicado — o usuário aprova ou recusa.
2. **Sínteses objetivas:** Troca de longas documentações para relatos M2M (Machine-to-Machine) enxutos, preparando terreno para futuras trocas de sessão de IA.
3. **Levenshtein no dashboard = proibido** — O(n²) inaceitável. Somente em relatórios assíncronos.
4. **`contarProducoesUnicas()` fica em `ProducaoDAO`** — semanticamente correto.
5. **Hash recalculado automaticamente** — normalizarTitulo() chamado antes de gerarHash().
6. **Objetos do Cache devem passar pelo contexto transacional** — uso de `session.get()` para instâncias de cache a fim de evitar `Multiple Representations` no Hibernate.

---

## 📋 Status Atual

### ✅ Concluídos
- **#1** — Filtro temporal nas produções por instituição (Backlog original resolvido)
- **#2** — Deduplicação de títulos por Levenshtein (cache por prefixo)
- **#3** — Normalização de nome de curso integrada ao parser
- **NPE e Cache do Hibernate** resolvidos no processamento de lote (CurriculoDAO, LattesParser e InstituicaoDAO).

### 🚨 Pendentes Críticos
- Falhas Críticas de Segurança (Senhas em claro, SQL Injection, Bypass Auth).
- NPE Cego remanescente em respostas nulas do Lattes API (DAD-09).
