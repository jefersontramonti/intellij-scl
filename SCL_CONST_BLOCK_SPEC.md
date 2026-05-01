# SCL_CONST_BLOCK_SPEC.md
## Suporte a bloco CONST em FB / FC / OB
**Plugin**: intellij-scl · **Fase**: 4B (parser gap fix)

---

## 0. Comportamento Esperado

### Situação atual ❌
```scl
FUNCTION_BLOCK FB_ConveyorControl
{ S7_Optimized_Access := 'TRUE' }

VAR_TEMP
    xEdge : Bool;
END_VAR

CONST                          ← ERRO: unexpected token
    STATE_IDLE    : INT := 0;
    STATE_RUNNING : INT := 1;
    TARGET_COUNT  : INT := 3;
END_CONST

BEGIN
    ...
END_FUNCTION_BLOCK
```
**Erro reportado**: `<var section>, SCL:BEGIN or SCL:END_FUNCTION_BLOCK expected, got 'CONST'`

### Comportamento esperado ✅

O bloco `CONST...END_CONST` deve ser aceito como seção de declaração válida
em **FB**, **FC** e **OB**, em qualquer posição entre as seções VAR e o BEGIN,
sem ordem obrigatória entre si.

Duas sintaxes suportadas conforme a documentação:

```scl
// Estilo TIA Portal V19 / S7-1500 (com tipo explícito) — prioritário
CONST
    STATE_IDLE   : INT  := 0;
    TARGET_COUNT : INT  := 3;
    MAX_SPEED    : REAL := 100.0;
    LABEL_NAME   : STRING := 'OK';
END_CONST

// Estilo SCLV4 clássico (sem tipo) — compatibilidade S7-300/400
CONST
    FIGURE := 10;
    TIME1  := TIME#1D_1H_10M_22S_2MS;
    FIG2   := 2 * 5 + 10 * 4;
END_CONST
```

### Folding esperado ✅
```
CONST ▶ (recolhido)       ← clicável para expandir
    STATE_IDLE   : INT := 0;
    ...
END_CONST
```

### Sem alteração no plugin.xml
O `SclFoldingBuilder` já está registrado — apenas adicionar o novo nó PSI
à lógica de folding existente.

---

## 1. Arquitetura — 3 Arquivos Modificados

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `src/main/kotlin/com/scl/plugin/lang/Scl.flex` | Modificar | Verificar/adicionar tokens `CONST` e `END_CONST` |
| `src/main/kotlin/com/scl/plugin/lang/Scl.bnf` | Modificar | Adicionar regra `const_section` + incluir nos blocos |
| `src/main/kotlin/com/scl/plugin/editor/SclFoldingBuilder.kt` | Modificar | Adicionar folding para `SclConstSection` |

Após modificar `.flex` e `.bnf`, regenerar:
```bash
./gradlew generateSclLexer generateSclParser
```

---

## 2. Scl.flex — Tokens CONST / END_CONST

Verificar se já existem. Se não existirem, adicionar no bloco de keywords:

```flex
/* Dentro do bloco de palavras-chave reservadas — manter ordem alfabética */
"CONST"         { return SclTypes.CONST; }
"END_CONST"     { return SclTypes.END_CONST; }
```

> **Atenção**: o flex do projeto provavelmente já tem esses tokens pois
> `CONST` aparece na lista de reserved words. Confirmar antes de adicionar
> para evitar duplicata.

---

## 3. Scl.bnf — Regra const_section

### 3.1 Adicionar a regra nova

```bnf
// Bloco CONST conforme documentação SCLV4 e TIA Portal V19
// Suporta ambas as sintaxes:
//   - TIA Portal: NAME : TYPE := expression ;
//   - SCLV4:      NAME := expression ;
const_section ::= CONST const_decl* END_CONST

const_decl ::= IDENTIFIER (COLON type_ref)? ASSIGN expression SEMICOLON
```

### 3.2 Incluir nos blocos FB, FC e OB

Localizar as regras existentes de `function_block_decl`, `function_decl` e
`org_block_decl`. Adicionar `const_section?` junto com as demais seções VAR.

**Exemplo — antes (function_block_decl):**
```bnf
function_block_decl ::= FUNCTION_BLOCK block_name block_attribute?
                        var_section*
                        BEGIN statement_list END_FUNCTION_BLOCK
```

**Depois:**
```bnf
function_block_decl ::= FUNCTION_BLOCK block_name block_attribute?
                        block_decl_section*
                        BEGIN statement_list END_FUNCTION_BLOCK

// Seção de declaração genérica: qualquer VAR ou CONST, em qualquer ordem
private block_decl_section ::= var_section | const_section
```

Aplicar o mesmo padrão para `function_decl` (FC) e `org_block_decl` (OB).

> **Nota sobre `private`**: usar `private block_decl_section` garante que
> o Grammar-Kit inline a regra no PSI pai, evitando nó intermediário
> desnecessário (mesmo padrão já usado para `statement`).

### 3.3 Verificar se CONST / END_CONST estão em SclTypes.kt

Após rodar `generateSclParser`, confirmar que `SclTypes.CONST` e
`SclTypes.END_CONST` existem em `SclTypes.kt`. Se não aparecerem, o
problema está no `.flex` (passo 2).

---

## 4. SclFoldingBuilder.kt — Folding para CONST

Localizar o arquivo `SclFoldingBuilder.kt` e adicionar o case para
`SclConstSection`:

```kotlin
// Dentro do método buildFoldRegions() ou visitElement()
// O padrão exato depende de como os outros blocos (VAR_INPUT, etc.) estão implementados.
// Seguir o mesmo padrão já existente para var_section.

// Exemplo típico para nó PSI que tem token de abertura e fechamento:
is SclConstSection -> {
    val start = node.findChildByType(SclTypes.CONST) ?: return
    val end   = node.findChildByType(SclTypes.END_CONST) ?: return
    val range = TextRange(start.startOffset, end.startOffset + end.textLength)
    descriptors.add(
        FoldingDescriptor(node, range, null, "CONST ... END_CONST", false)
    )
}
```

> **Importante**: o nome exato do PSI type gerado será `SclConstSection`
> (Grammar-Kit converte `const_section` → `SclConstSection`). Confirmar
> no PSI Viewer após regenerar o parser.

---

## Checklist de Testes

Após `./gradlew buildPlugin` + `./gradlew runIde`:

### Parser (sem erro vermelho no editor)
- [ ] `CONST...END_CONST` sozinho dentro de FB, após `END_VAR` → sem erro
- [ ] `CONST...END_CONST` antes de qualquer `VAR` section → sem erro
- [ ] `CONST...END_CONST` entre duas seções VAR → sem erro
- [ ] Sintaxe TIA Portal com tipo: `NAME : INT := 0;` → sem erro
- [ ] Sintaxe SCLV4 sem tipo: `NAME := 10;` → sem erro
- [ ] Múltiplas declarações no mesmo CONST → sem erro
- [ ] FC com CONST block → sem erro
- [ ] OB com CONST block → sem erro

### Folding
- [ ] Clicar na gutter arrow recolhe o bloco CONST
- [ ] Texto recolhido mostra `CONST ... END_CONST`
- [ ] Reabrir expande corretamente

### Não-regressão
- [ ] `VAR_INPUT`, `VAR_OUTPUT`, `VAR`, `VAR_TEMP` continuam funcionando
- [ ] Arquivo `FB_ConveyorControl.scl` (do projeto de teste) sem erros
- [ ] `CASE` com constantes simbólicas sem erros (`State := STATE_RUNNING`)
- [ ] Completion dentro do `BEGIN` ainda sugere as constantes como identifiers

---

## Problemas Comuns

**`SclConstSection` não aparece no PSI Viewer após regenerar:**
```
CAUSA: tokens CONST/END_CONST faltando no .flex ou duplicados
FIX:   buscar "CONST" no .flex — se já existir com nome diferente,
       usar o nome existente na regra BNF
```

**Erro vermelho persiste após corrigir BNF:**
```
CAUSA: cache do parser antigo — IntelliJ não recarregou
FIX:   File → Invalidate Caches → Invalidate and Restart
```

**`block_decl_section` cria nó PSI extra desnecessário:**
```
CAUSA: regra não está marcada como private
FIX:   private block_decl_section ::= var_section | const_section
       Com private, Grammar-Kit inlina a regra no pai
```

**Folding lança NPE:**
```
CAUSA: SclConstSection não tem CONST ou END_CONST como filhos diretos
       (pode ter wrapper intermediário)
FIX:   Logar node.children no buildFoldRegions() para inspecionar a
       estrutura real gerada pelo Grammar-Kit
```

**Constantes não reconhecidas como referências no CASE:**
```
CAUSA: o linter/resolver não conhece SclConstDecl como source de símbolos
FIX:   fora do escopo desta spec — será tratado na Fase 6 (Go to Definition)
       Por ora as constantes funcionam sintaticamente, sem erro vermelho
```

---

## Prompt para Claude Code

```
Leia docs/SCL_CONST_BLOCK_SPEC.md.
Leia todos os arquivos existentes do projeto antes de codificar.

ANTES de codificar:
1. Verifique se CONST e END_CONST já existem em Scl.flex
   (busque por "CONST" no arquivo)
2. Verifique o padrão atual das regras function_block_decl, function_decl
   e org_block_decl no Scl.bnf para entender a estrutura exata
3. Verifique como SclFoldingBuilder.kt trata os nós VAR_INPUT / VAR_OUTPUT
   para seguir o mesmo padrão

IMPLEMENTAR (nesta ordem):
1. Scl.flex   — adicionar CONST/END_CONST se ausentes
2. Scl.bnf    — adicionar const_section + private block_decl_section
                incluir nos 3 tipos de bloco (FB, FC, OB)
3. Regenerar: ./gradlew generateSclLexer generateSclParser
4. SclFoldingBuilder.kt — adicionar case para SclConstSection

NÃO modificar plugin.xml (nenhum registro novo necessário).

VALIDAR:
./gradlew buildPlugin
Se OK: ./gradlew runIde
Abrir FB_ConveyorControl.scl do projeto de teste.
Verificar: sem erro vermelho no CONST block + folding funcionando.
```
