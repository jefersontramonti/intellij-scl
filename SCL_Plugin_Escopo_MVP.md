# SCL LANGUAGE SUPPORT — IntelliJ Platform Plugin
**com Agente IA via Claude Code CLI**

| Campo | Valor |
|---|---|
| Documento | Escopo do Projeto (v2.0) |
| Versão do Plugin | MVP 1.0.0 |
| Target IDE | IntelliJ IDEA 2026.1+ (build 253+) |
| Data | Abril 2026 |

---

## 1. Visão Geral do Projeto

### 1.1 Objetivo

Desenvolver um plugin nativo para a plataforma IntelliJ que adicione suporte completo à linguagem SCL (Structured Control Language) conforme IEC 61131-3 e extensões Siemens TIA Portal V19, integrando um agente de IA contextualizado com o manual oficial SCL da Siemens para auxiliar engenheiros de automação industrial diretamente no editor de código.

O agente de IA opera via **Claude Code CLI** (`claude`), sem necessidade de API Key — a autenticação é gerenciada pelo próprio CLI instalado na máquina do usuário.

### 1.2 Problema Resolvido

- TIA Portal é o único editor SCL disponível — lento, sem autocomplete moderno e sem IA
- Desenvolvedores perdem tempo consultando PDFs de manuais de forma manual
- Erros de compatibilidade entre S7-1200 e S7-1500 só são descobertos na compilação
- Não existe nenhum plugin SCL com consciência de hardware e IA no JetBrains Marketplace

### 1.3 Proposta de Valor

O único plugin SCL do mundo com agente IA embutido, treinado diretamente sobre o manual oficial Siemens, com consciência de hardware e integrado ao fluxo de trabalho TIA Portal.

---

## 2. Escopo — O que está DENTRO do MVP

### 2.1 Suporte à Linguagem SCL

#### 2.1.1 Lexer e Tokenização
- Reconhecimento de todas as palavras-chave IEC 61131-3
- Extensões Siemens: `FUNCTION_BLOCK`, `DATA_BLOCK`, `ORGANIZATION_BLOCK`
- Todos os tipos nativos: `BOOL`, `INT`, `DINT`, `REAL`, `LREAL`, `TIME`, `STRING`, `BYTE`, `WORD`, `DWORD`
- Tipos de timer Siemens: `TON`, `TOF`, `TP`, `TONR`
- Operadores lógicos: `AND`, `OR`, `NOT`, `XOR`, `MOD`
- Literais: booleanos, inteiros, reais, strings, constantes de tempo (`T#5S`)
- Comentários: linha (`//`) e bloco (`(* ... *)`)

#### 2.1.2 Parser e Árvore Sintática (PSI)
- Estrutura de blocos: FB, FC, OB, DB com delimitadores `BEGIN/END`
- Seções de variáveis: `VAR`, `VAR_INPUT`, `VAR_OUTPUT`, `VAR_IN_OUT`, `VAR_STATIC`, `VAR_TEMP`
- Estruturas de controle: `IF/THEN/ELSIF/ELSE/END_IF`
- Estruturas de controle: `CASE/OF/END_CASE`
- Loops: `FOR/TO/BY/DO/END_FOR`, `WHILE/END_WHILE`, `REPEAT/UNTIL/END_REPEAT`
- Chamada de blocos e instâncias

#### 2.1.3 Syntax Highlighting
- Cores distintas por categoria: keywords, tipos, comentários, literais, operadores
- Página de configuração de cores em `Settings > Editor > Color Scheme > SCL`

### 2.2 Autocomplete e Produtividade

#### 2.2.1 Code Completion
- Keywords SCL com bold e categoria visível
- Tipos de dados com cor diferenciada
- Funções built-in Siemens: `ABS`, `SQRT`, `LIMIT`, `MIN`, `MAX`, `INT_TO_REAL`, etc.
- Completar `END_` automaticamente ao fechar blocos

#### 2.2.2 Live Templates (Snippets)

| Atalho | Gera |
|---|---|
| `fsm` | Estrutura CASE completa com estados IDLE/RUNNING/FAULT |
| `fb` | Esqueleto completo de FUNCTION_BLOCK com VAR_INPUT/OUTPUT/STATIC |
| `fc` | Esqueleto de FUNCTION com Return value |
| `ton` | Instância TON com IN, PT, Q e ET prontos |
| `tof` | Instância TOF |
| `iff` | Bloco IF/THEN/ELSIF/ELSE/END_IF |
| `forr` | Loop FOR com variável de controle |
| `udt` | Esqueleto de TYPE ... END_TYPE |

#### 2.2.3 Folding de Código
- Recolher/expandir: `FUNCTION_BLOCK...END_FUNCTION_BLOCK`
- Recolher/expandir: `VAR...END_VAR`
- Recolher/expandir: `IF...END_IF`, `CASE...END_CASE`, `FOR...END_FOR`

#### 2.2.4 Comentário Rápido
- `Ctrl+/` para comentar/descomentar linha com `//`
- `Ctrl+Shift+/` para comentar/descomentar bloco com `(* ... *)`

### 2.3 Linter com Consciência de Hardware

#### 2.3.1 Hardware Target
- Seleção na barra de status: **S7-1200** ou **S7-1500**
- Configuração persistente por projeto

#### 2.3.2 Regras de Validação

| Regra | S7-1200 | S7-1500 |
|---|---|---|
| `TIME_TO_DINT` direto | Erro — não suportado | OK |
| `LREAL` | Aviso — precisão limitada | OK |
| Acesso direto `%MDx/%MWx` no mesmo endereço | Erro — conflito | Erro — conflito |
| `IN := NOT Timer.Q` (blink bug) | Aviso — 1 scan cycle | Aviso — 1 scan cycle |
| Estado CASE sem transição de saída | Aviso — deadlock potencial | Aviso — deadlock potencial |

### 2.4 Agente IA — Claude Code CLI

> **Decisão arquitetural:** O agente não usa a Claude API diretamente.  
> Ele invoca o `claude` CLI como subprocesso via `ProcessBuilder`, sem necessidade de API Key.

#### 2.4.1 Indexação do Manual SCL
- Leitura e extração de texto do PDF do SCL Reference (Apache PDFBox)
- Chunking por seção (heading → próximo heading)
- Índice local leve salvo em JSON (~5MB)
- Indexação executada uma vez na abertura do projeto
- Busca por palavras-chave para recuperar chunks relevantes

#### 2.4.2 Integração com Claude Code CLI

```kotlin
// Exemplo de chamada ao agente
val prompt = buildPrompt(selectedCode, hardwareTarget, linterIssues, manualChunks)
val process = ProcessBuilder("claude", "-p", prompt)
    .redirectErrorStream(true)
    .start()
val response = process.inputStream.bufferedReader().readText()
```

- Plugin detecta se `claude` está no PATH ao inicializar
- Aviso visível no painel se CLI não encontrado
- Sem configuração de API Key — autenticação gerenciada pelo CLI

#### 2.4.3 Painel do Agente
- Painel lateral: **SCL Assistant** (Tool Window)
- Campo de chat com histórico de sessão (multi-turn)
- Contexto injetado automaticamente: trecho de código selecionado, hardware target, issues do linter
- Chunks do manual SCL enviados junto ao prompt (top 3 mais relevantes)
- Respostas com código SCL formatado e citação de seção do manual
- Botão **"Inserir no código"** para aplicar sugestão diretamente no editor

#### 2.4.4 Ações de IA no Editor
- Ação no menu de contexto: **Explain with SCL Assistant**
- Ação no menu de contexto: **Fix with SCL Assistant**
- Suggestion popup quando linter detecta erro — "Sugerir correção via IA"

---

## 3. Fora do Escopo — MVP

| Feature | Justificativa da Exclusão |
|---|---|
| Sync bidirecional com TIA Portal | Depende de API TIA Portal — complexidade alta |
| FSM Visual (diagrama de estados) | Feature avançada — Fase 2 |
| Factory.IO Bridge (live watch) | Requer protocolo TCP customizado — Fase 2 |
| RAG com embeddings vetoriais locais | Busca por keyword é suficiente para MVP |
| Go to Definition / Find Usages | Requer análise semântica completa — Fase 2 |
| Outros manuais Siemens (S7-1200, S7-1500) | Somente manual SCL no MVP |
| Publicação no JetBrains Marketplace | Após validação do MVP |
| Suporte a AWL / LAD / FBD | Somente SCL/ST no escopo |

---

## 4. Stack Técnica

| Camada | Tecnologia | Versão |
|---|---|---|
| Linguagem do plugin | Kotlin | **2.2.20** |
| Build | IntelliJ Platform Gradle Plugin | **2.10.5** |
| Gradle | Gradle Wrapper | **9.2.1** |
| JVM | JVM Toolchain | **21** |
| Target IDE | IntelliJ IDEA | **2026.1+** (build 253+) |
| Lexer/Parser | Grammar-Kit (BNF + JFlex) | 2024.x |
| UI extra | Compose UI (Jewel) | via Kotlin plugin 2.2.20 |
| LSP | com.intellij.modules.lsp | bundled |
| Leitura de PDF | Apache PDFBox | 3.0.x |
| Agente IA | **Claude Code CLI** (`claude`) | CLI local |
| HTTP Client | **não necessário** | — |
| Extensão de arquivo | `.scl`, `.awl` (read-only) | — |

> **Nota:** OkHttp e Kotlin Coroutines para HTTP foram removidos do stack pois não são necessários com a abordagem Claude Code CLI.

---

## 5. Fases de Desenvolvimento

### Fase 1 — Fundação da Linguagem
**Objetivo:** IntelliJ reconhece e colore arquivos `.scl` corretamente.

1. Configurar projeto Gradle com IntelliJ Platform SDK
2. Implementar `SclLanguage`, `SclFileType`, `SclIcons`
3. Escrever gramática JFlex (`.flex`) com todos os tokens
4. Gerar Lexer via Grammar-Kit
5. Implementar `SclSyntaxHighlighter` com todas as categorias
6. Registrar no `plugin.xml` e validar com `./gradlew runIde`

### Fase 2 — Parser e Estrutura
**Objetivo:** IntelliJ entende a estrutura hierárquica do código.

1. Escrever gramática BNF completa (`.bnf`)
2. Gerar Parser e classes PSI via Grammar-Kit
3. Implementar `SclParserDefinition`
4. Implementar Code Folding (FB, VAR, IF, CASE, FOR)
5. Implementar Commenter (`Ctrl+/` e `Ctrl+Shift+/`)

### Fase 3 — Produtividade
**Objetivo:** Developer experience profissional no editor.

1. Implementar `SclCompletionContributor` (keywords, tipos, built-ins)
2. Criar todos os Live Templates (`fsm`, `fb`, `fc`, `ton`, `tof`, `iff`, `forr`, `udt`)
3. Implementar configuração de Color Scheme
4. Implementar seleção de hardware target na status bar

### Fase 4 — Linter HW-Aware
**Objetivo:** Detectar erros antes de abrir o TIA Portal.

1. Implementar `SclAnnotator` com regras por hardware
2. Adicionar todas as regras da tabela da seção 2.3.2
3. Implementar quick-fix suggestions para cada regra

### Fase 5 — Agente IA
**Objetivo:** Assistente contextualizado com o manual SCL via Claude Code CLI.

1. Implementar `SclManualIndexer` (PDFBox + chunking por seção)
2. Implementar busca por keyword no índice
3. Verificar presença do `claude` CLI no PATH
4. Implementar `SclAssistantPanel` (Tool Window)
5. Integrar Claude Code CLI com contexto: código + manual + hardware + linter
6. Implementar multi-turn com histórico de sessão
7. Adicionar ações de contexto no editor (Explain, Fix)
8. Implementar botão "Inserir no código"

---

## 6. Critérios de Aceite do MVP

| # | Critério | Prioridade | Status |
|---|---|---|---|
| 1 | Arquivo `.scl` abre com syntax highlighting correto no IntelliJ | MUST | Pendente |
| 2 | Autocomplete sugere keywords e tipos ao digitar | MUST | Pendente |
| 3 | Live template `fsm` gera estrutura CASE completa | MUST | Pendente |
| 4 | Linter aponta `TIME_TO_DINT` como erro no S7-1200 | MUST | Pendente |
| 5 | Linter avisa sobre blink timer bug (`IN := NOT Timer.Q`) | MUST | Pendente |
| 6 | Painel SCL Assistant responde pergunta sobre sintaxe SCL via `claude` CLI | MUST | Pendente |
| 7 | Resposta do agente cita seção do manual SCL | MUST | Pendente |
| 8 | Botão "Inserir no código" insere sugestão na posição correta | SHOULD | Pendente |
| 9 | Code folding funciona para FB, VAR e IF | SHOULD | Pendente |
| 10 | Plugin carrega sem erros em `./gradlew runIde` | MUST | Pendente |
| 11 | Plugin exibe aviso claro se `claude` CLI não estiver no PATH | MUST | Pendente |

---

## 7. Restrições e Dependências

### 7.1 Restrições Técnicas
- `claude` CLI deve estar instalado e autenticado na máquina do usuário
- Manual SCL em PDF (`docs/SCLV4_e.pdf`) já está presente no projeto
- Requer conexão com internet apenas para chamadas ao Claude Code CLI — linter e editor funcionam offline
- Compatibilidade garantida com IntelliJ IDEA 2026.1+ (build 253+)

### 7.2 Dependências Externas
- **Apache PDFBox 3.0.x** — extração de texto do PDF
- **Grammar-Kit** — geração de Lexer/Parser (dependência de desenvolvimento)
- **Claude Code CLI** (`claude`) — processamento de IA (instalado localmente)

### 7.3 O que o Usuário Precisa Fornecer
- IntelliJ IDEA 2026.1 ou superior instalado
- JDK 21 ou superior
- **Claude Code CLI instalado e autenticado** (`claude --version` deve funcionar)
- PDF do SCL Reference Siemens (já presente em `docs/SCLV4_e.pdf`)

---

## 8. Próximos Passos Imediatos

| # | Fase | Entregável | Dependência |
|---|---|---|---|
| 1 | Fundação | Projeto Gradle + Lexer + Highlighting funcionando | Grammar-Kit adicionado ao build |
| 2 | Parser | Gramática BNF + PSI Tree + Folding | Fase 1 concluída |
| 3 | Produtividade | Completion + Live Templates + Commenter | Fase 2 concluída |
| 4 | Linter | Anotações HW-aware + Quick Fixes | Fase 2 concluída |
| 5 | IA | Indexador PDF + Painel Claude CLI + Insert Action | Fases 3 e 4 concluídas |

**Próxima ação:** Adicionar Grammar-Kit ao `libs.versions.toml` e `build.gradle.kts`, então iniciar a Fase 1.
