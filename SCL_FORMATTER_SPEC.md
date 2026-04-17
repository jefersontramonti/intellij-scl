# ESPECIFICAÇÃO: Code Formatter SCL — `Ctrl+Alt+L`
**Documento para Claude Code CLI — implementar FormattingModelBuilder**

---

## 0. Comportamento Esperado

Pressionar `Ctrl+Alt+L` em qualquer arquivo `.scl` transforma código desformatado
(exportado do TIA Portal) em código padronizado, seguindo as convenções Siemens.

### Antes (saída bruta do TIA Portal):
```scl
FUNCTION_BLOCK FB_Control
VAR_INPUT
i_bStart:BOOL;
i_rSetpoint:REAL:=0.0;
END_VAR
VAR
s_nState:INT:=0;
s_tTimer:TON;
END_VAR
BEGIN
IF i_bStart=TRUE THEN
s_nState:=1;
s_tTimer(IN:=TRUE,PT:=T#5S);
IF s_tTimer.Q=TRUE THEN
s_nState:=0;
END_IF;
END_IF;
END_FUNCTION_BLOCK
```

### Depois (após `Ctrl+Alt+L`):
```scl
FUNCTION_BLOCK FB_Control
VAR_INPUT
    i_bStart   : BOOL;
    i_rSetpoint : REAL := 0.0;
END_VAR
VAR
    s_nState : INT := 0;
    s_tTimer : TON;
END_VAR
BEGIN
    IF i_bStart = TRUE THEN
        s_nState := 1;
        s_tTimer(IN := TRUE, PT := T#5S);
        IF s_tTimer.Q = TRUE THEN
            s_nState := 0;
        END_IF;
    END_IF;
END_FUNCTION_BLOCK
```

---

## 1. Regras de Formatação SCL — Especificação Completa

### 1.1 Indentação

| Contexto                                    | Regra                          |
|---------------------------------------------|--------------------------------|
| Nível de bloco (FUNCTION_BLOCK, FUNCTION, OB) | 0 espaços (raiz)             |
| Declarações dentro de VAR_xxx...END_VAR     | +4 espaços (1 nível)          |
| Código dentro de BEGIN...END_FUNCTION_BLOCK | +4 espaços (1 nível)          |
| Corpo do IF (entre THEN e END_IF/ELSE)      | +4 espaços por nível          |
| Corpo do ELSE / ELSIF                       | +4 espaços por nível          |
| Corpo do FOR/WHILE/REPEAT (entre DO e END)  | +4 espaços por nível          |
| Branches do CASE (entre OF e END_CASE)      | +4 espaços + rótulo no nível  |
| Código dentro do CASE branch                | +8 espaços (2 níveis)         |
| STRUCT...END_STRUCT (dentro de TYPE)        | +4 espaços por nível          |

**Padrão:** 4 espaços, nunca tabs.

### 1.2 Espaçamento em Operadores

| Token / Contexto                | Regra                              | Exemplo                     |
|---------------------------------|------------------------------------|-----------------------------|
| Atribuição `:=`                 | 1 espaço antes e depois            | `s_nState := 1`             |
| Comparação `=`, `<>`, `<`, `>`, `<=`, `>=` | 1 espaço antes e depois | `i_bStart = TRUE`           |
| Operadores aritméticos `+`, `-`, `*`, `/` | 1 espaço antes e depois | `nResult := a + b * 2`    |
| Operadores lógicos `AND`, `OR`, `NOT`, `XOR` | 1 espaço antes e depois | `IF a AND NOT b THEN`  |
| Dois-pontos em declaração `: TIPO` | 1 espaço antes e depois       | `i_bStart : BOOL`           |
| Ponto-e-vírgula `;`             | 0 espaços antes, nova linha depois | `s_nState := 1;⏎`           |
| Vírgula `,` em chamada de FB    | 0 antes, 1 depois                  | `TON(IN := x, PT := T#5S)` |
| Parênteses em chamadas `(`  `)` | 0 espaço após `(`, 0 antes `)`     | `ABS(valor)`                |
| Intervalo de array `..`         | 0 espaços                          | `ARRAY[1..10]`              |

### 1.3 Quebras de Linha

| Contexto                        | Regra                                          |
|---------------------------------|------------------------------------------------|
| Após `VAR_INPUT`, `VAR_OUTPUT`, etc. | Nova linha imediatamente                  |
| Cada declaração de variável     | 1 por linha, terminando com `;`                |
| `END_VAR`                       | Linha própria, no mesmo nível que `VAR_xxx`    |
| `BEGIN`                         | Linha própria, no nível 0                      |
| `END_FUNCTION_BLOCK`            | Linha própria, no nível 0                      |
| Entre seções VAR diferentes     | 0 linha em branco extra (consecutivas)         |
| Entre instruções na seção BEGIN | 0 linhas em branco extras (preservar 1 se existir) |
| Após `THEN`, `DO`, `REPEAT`     | Nova linha antes do corpo indentado            |
| `ELSE`, `ELSIF`                 | Linha própria, recuado ao mesmo nível do `IF`  |
| `END_IF`, `END_FOR`, `END_WHILE`, `END_CASE` | Linha própria, mesmo nível do bloco pai |

### 1.4 O que o Formatter NÃO muda

- **Comentários** `//` e `(* *)` — preservados exatamente como estão
- **Case de keywords** — o formatter não muda maiúsculas/minúsculas
  (SCL é case-insensitive, mas o usuário pode preferir seu estilo)
- **Strings** `'...'` — conteúdo preservado
- **Literais de tempo** `T#5S`, `T#500MS` — preservados
- **Linhas em branco** extras no código (preserva até 1 linha em branco entre blocos)

---

## 2. Arquitetura — 4 Classes Necessárias

```
src/main/kotlin/com/yourplugin/scl/formatting/
├── SclFormattingModelBuilder.kt  ← entry point, registrado no plugin.xml
├── SclBlock.kt                   ← lógica central de indentação e espaçamento
├── SclSpacingBuilder.kt          ← todas as regras de espaço entre tokens
└── SclCodeStyleSettings.kt       ← configurações customizáveis pelo usuário
```

---

## 3. SclFormattingModelBuilder

```kotlin
// src/main/kotlin/com/yourplugin/scl/formatting/SclFormattingModelBuilder.kt

class SclFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings

        // 1. Criar SpacingBuilder com todas as regras SCL
        val spacingBuilder = SclSpacingBuilder.create(settings)

        // 2. Criar bloco raiz que cobre o arquivo inteiro
        val rootBlock = SclBlock(
            node          = formattingContext.node,
            wrap          = Wrap.createWrap(WrapType.NONE, false),
            alignment     = null,
            settings      = settings,
            spacingBuilder = spacingBuilder,
            indent        = Indent.getNoneIndent()
        )

        // 3. Retornar modelo PSI-based (padrão recomendado pela JetBrains)
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            rootBlock,
            settings
        )
    }
}
```

**Registro no plugin.xml:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <lang.formatter
        language="SCL"
        implementationClass="com.yourplugin.scl.formatting.SclFormattingModelBuilder"/>
</extensions>
```

---

## 4. SclSpacingBuilder — Todas as Regras de Espaço

```kotlin
// src/main/kotlin/com/yourplugin/scl/formatting/SclSpacingBuilder.kt

object SclSpacingBuilder {

    fun create(settings: CodeStyleSettings): SpacingBuilder {
        return SpacingBuilder(settings, SclLanguage.INSTANCE)

            // ── OPERADORES DE ATRIBUIÇÃO ─────────────────────────────────────
            // := → 1 espaço antes e depois
            .around(SclTypes.ASSIGN)
                .spaces(1)

            // ── OPERADORES DE COMPARAÇÃO ─────────────────────────────────────
            // =, <>, <, >, <=, >= → 1 espaço antes e depois
            .around(SclTypes.EQ)
                .spaces(1)
            .around(SclTypes.NEQ)
                .spaces(1)
            .around(SclTypes.LT)
                .spaces(1)
            .around(SclTypes.GT)
                .spaces(1)
            .around(SclTypes.LE)
                .spaces(1)
            .around(SclTypes.GE)
                .spaces(1)

            // ── OPERADORES ARITMÉTICOS ────────────────────────────────────────
            // +, -, *, / → 1 espaço antes e depois
            .around(SclTypes.PLUS)
                .spaces(1)
            .around(SclTypes.MINUS)
                .spaces(1)
            .around(SclTypes.MUL)
                .spaces(1)
            .around(SclTypes.DIV)
                .spaces(1)
            .around(SclTypes.MOD_KW)
                .spaces(1)

            // ── DECLARAÇÕES DE VARIÁVEIS ──────────────────────────────────────
            // "nomeVar : TIPO" → 1 espaço antes e depois do ":"
            .around(SclTypes.COLON)
                .spaces(1)

            // ── PONTO-E-VÍRGULA ───────────────────────────────────────────────
            // "stmt;" → sem espaço antes do ";"
            .before(SclTypes.SEMICOLON)
                .none()

            // ── VÍRGULAS EM CHAMADAS DE FB ────────────────────────────────────
            // "TON(IN:=x, PT:=T#5S)" → sem espaço antes, 1 depois de ","
            .before(SclTypes.COMMA)
                .none()
            .after(SclTypes.COMMA)
                .spaces(1)

            // ── PARÊNTESES ────────────────────────────────────────────────────
            // "ABS(x)" → sem espaço após "(" e antes de ")"
            .after(SclTypes.LPAREN)
                .none()
            .before(SclTypes.RPAREN)
                .none()

            // ── OPERADORES LÓGICOS ────────────────────────────────────────────
            // AND, OR, XOR, NOT → 1 espaço (são keywords, tratadas como tokens)
            .around(SclTypes.AND_KW)
                .spaces(1)
            .around(SclTypes.OR_KW)
                .spaces(1)
            .around(SclTypes.XOR_KW)
                .spaces(1)
            .before(SclTypes.NOT_KW)
                .spaces(1)
            .after(SclTypes.NOT_KW)
                .spaces(1)

            // ── PONTO (acesso de membro) ──────────────────────────────────────
            // "timer.Q" → sem espaços em volta do "."
            .around(SclTypes.DOT)
                .none()

            // ── RANGE (..) ────────────────────────────────────────────────────
            // "ARRAY[1..10]" → sem espaços em volta de ".."
            .around(SclTypes.RANGE)
                .none()

            // ── COLCHETES ────────────────────────────────────────────────────
            .after(SclTypes.LBRACKET)
                .none()
            .before(SclTypes.RBRACKET)
                .none()
    }
}
```

---

## 5. SclBlock — Lógica de Indentação

```kotlin
// src/main/kotlin/com/yourplugin/scl/formatting/SclBlock.kt

class SclBlock(
    node:           ASTNode,
    wrap:           Wrap?,
    alignment:      Alignment?,
    private val settings:       CodeStyleSettings,
    private val spacingBuilder: SpacingBuilder,
    private val indent:         Indent
) : AbstractBlock(node, wrap, alignment) {

    // ── Constrói os blocos filhos recursivamente ──────────────────────────
    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode

        while (child != null) {
            // Ignorar whitespace — o formatter vai recalcular
            if (child.elementType != TokenType.WHITE_SPACE) {
                val childIndent    = computeChildIndent(child)
                val childAlignment = computeChildAlignment(child)

                blocks.add(
                    SclBlock(
                        node           = child,
                        wrap           = Wrap.createWrap(WrapType.NONE, false),
                        alignment      = childAlignment,
                        settings       = settings,
                        spacingBuilder = spacingBuilder,
                        indent         = childIndent
                    )
                )
            }
            child = child.treeNext
        }
        return blocks
    }

    // ── Indentação deste bloco ────────────────────────────────────────────
    override fun getIndent(): Indent = indent

    // ── Espaçamento entre filhos — delegado ao SpacingBuilder ────────────
    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacingBuilder.getSpacing(this, child1, child2)

    // ── Bloco é folha se não tem filhos ──────────────────────────────────
    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    // ── Indentação automática ao pressionar Enter ─────────────────────────
    // Chamado quando o bloco antes do cursor está incompleto
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return when (myNode.elementType) {
            // Dentro de VAR_xxx → nova declaração indentada
            SclTypes.VAR_INPUT_SECTION,
            SclTypes.VAR_OUTPUT_SECTION,
            SclTypes.VAR_INOUT_SECTION,
            SclTypes.VAR_STATIC_SECTION,
            SclTypes.VAR_TEMP_SECTION ->
                ChildAttributes(Indent.getNormalIndent(), null)

            // Dentro do corpo BEGIN → nova instrução indentada
            SclTypes.CODE_SECTION ->
                ChildAttributes(Indent.getNormalIndent(), null)

            // Dentro de IF → corpo indentado
            SclTypes.IF_STATEMENT ->
                ChildAttributes(Indent.getNormalIndent(), null)

            // Dentro de FOR/WHILE/REPEAT → corpo indentado
            SclTypes.FOR_STATEMENT,
            SclTypes.WHILE_STATEMENT,
            SclTypes.REPEAT_STATEMENT ->
                ChildAttributes(Indent.getNormalIndent(), null)

            // Dentro de CASE → branches indentados
            SclTypes.CASE_STATEMENT ->
                ChildAttributes(Indent.getNormalIndent(), null)

            // Dentro de STRUCT → campos indentados
            SclTypes.STRUCT_TYPE ->
                ChildAttributes(Indent.getNormalIndent(), null)

            else -> ChildAttributes(Indent.getNoneIndent(), null)
        }
    }

    // ── Bloco está incompleto? (afeta qual pai recebe Enter indent) ───────
    override fun isIncomplete(): Boolean {
        val lastChild = myNode.lastChildNode ?: return false
        return when (myNode.elementType) {
            // IF incompleto se não tiver END_IF
            SclTypes.IF_STATEMENT ->
                lastChild.elementType != SclTypes.END_IF_KW

            // FOR incompleto se não tiver END_FOR
            SclTypes.FOR_STATEMENT ->
                lastChild.elementType != SclTypes.END_FOR_KW

            // WHILE incompleto se não tiver END_WHILE
            SclTypes.WHILE_STATEMENT ->
                lastChild.elementType != SclTypes.END_WHILE_KW

            // CASE incompleto se não tiver END_CASE
            SclTypes.CASE_STATEMENT ->
                lastChild.elementType != SclTypes.END_CASE_KW

            // VAR section incompleto se não tiver END_VAR
            SclTypes.VAR_INPUT_SECTION,
            SclTypes.VAR_OUTPUT_SECTION,
            SclTypes.VAR_STATIC_SECTION,
            SclTypes.VAR_TEMP_SECTION ->
                lastChild.elementType != SclTypes.END_VAR_KW

            else -> false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Calcula o Indent correto para um nó filho baseado no tipo do pai
     * e no tipo do próprio nó filho.
     */
    private fun computeChildIndent(child: ASTNode): Indent {
        val parentType = myNode.elementType
        val childType  = child.elementType

        return when {
            // ── Declarações dentro de seções VAR ──────────────────────────
            parentType in VAR_SECTIONS && childType == SclTypes.VARIABLE_DECLARATION ->
                Indent.getNormalIndent()

            // END_VAR sempre no mesmo nível da seção VAR pai (sem indent)
            childType in END_KEYWORDS ->
                Indent.getNoneIndent()

            // ── Corpo do bloco principal (BEGIN...END) ────────────────────
            parentType == SclTypes.CODE_SECTION && childType != SclTypes.BEGIN_KW ->
                Indent.getNormalIndent()

            // ── IF: corpo entre THEN e END_IF/ELSE/ELSIF ──────────────────
            parentType == SclTypes.IF_THEN_PART ->
                Indent.getNormalIndent()

            parentType == SclTypes.ELSE_PART ->
                Indent.getNormalIndent()

            // ELSE, ELSIF, END_IF no mesmo nível que o IF pai
            childType in IF_CLOSE_KEYWORDS ->
                Indent.getNoneIndent()

            // ── FOR, WHILE, REPEAT: corpo do loop ─────────────────────────
            parentType == SclTypes.FOR_BODY ||
            parentType == SclTypes.WHILE_BODY ||
            parentType == SclTypes.REPEAT_BODY ->
                Indent.getNormalIndent()

            // END_FOR, END_WHILE, END_REPEAT no mesmo nível
            childType in LOOP_CLOSE_KEYWORDS ->
                Indent.getNoneIndent()

            // ── CASE: rótulos e código dos branches ───────────────────────
            parentType == SclTypes.CASE_BRANCH ->
                Indent.getNormalIndent()

            // END_CASE no mesmo nível
            childType == SclTypes.END_CASE_KW ->
                Indent.getNoneIndent()

            // ── STRUCT: campos ─────────────────────────────────────────────
            parentType == SclTypes.STRUCT_TYPE &&
            childType == SclTypes.STRUCT_FIELD ->
                Indent.getNormalIndent()

            // Default: sem indentação
            else -> Indent.getNoneIndent()
        }
    }

    /**
     * Alinhamento para declarações de variáveis:
     * Alinha os ":" de todas as declarações na mesma seção VAR.
     *
     * Exemplo:
     *   i_bStart    : BOOL    <- alinhado
     *   i_rSetpoint : REAL    <- alinhado
     */
    private fun computeChildAlignment(child: ASTNode): Alignment? {
        // Alinhamento de ":" nas declarações VAR é opcional
        // Habilitado via SclCodeStyleSettings.alignVariableDeclarations
        val customSettings = settings.getCustomSettings(SclCodeStyleSettings::class.java)
        if (!customSettings.ALIGN_VARIABLE_COLONS) return null

        if (myNode.elementType in VAR_SECTIONS &&
            child.elementType == SclTypes.COLON) {
            return sectionColonAlignment
        }
        return null
    }

    // Alignment compartilhado por todos os ":" da mesma seção VAR
    private val sectionColonAlignment: Alignment by lazy { Alignment.createAlignment() }

    companion object {
        // Token sets para facilitar os when() acima

        val VAR_SECTIONS = TokenSet.create(
            SclTypes.VAR_INPUT_SECTION,
            SclTypes.VAR_OUTPUT_SECTION,
            SclTypes.VAR_INOUT_SECTION,
            SclTypes.VAR_STATIC_SECTION,
            SclTypes.VAR_TEMP_SECTION,
            SclTypes.CONST_SECTION
        )

        val END_KEYWORDS = TokenSet.create(
            SclTypes.END_VAR_KW,
            SclTypes.END_FUNCTION_BLOCK_KW,
            SclTypes.END_FUNCTION_KW,
            SclTypes.END_ORGANIZATION_BLOCK_KW
        )

        val IF_CLOSE_KEYWORDS = TokenSet.create(
            SclTypes.ELSE_KW,
            SclTypes.ELSIF_KW,
            SclTypes.END_IF_KW
        )

        val LOOP_CLOSE_KEYWORDS = TokenSet.create(
            SclTypes.END_FOR_KW,
            SclTypes.END_WHILE_KW,
            SclTypes.END_REPEAT_KW,
            SclTypes.UNTIL_KW   // UNTIL encerra REPEAT
        )
    }
}
```

---

## 6. SclCodeStyleSettings — Configurações do Usuário

```kotlin
// src/main/kotlin/com/yourplugin/scl/formatting/SclCodeStyleSettings.kt

class SclCodeStyleSettings(container: CodeStyleSettings)
    : CustomCodeStyleSettings("SclCodeStyleSettings", container) {

    // Alinhar ":" de declarações na mesma seção VAR
    // i_bStart    : BOOL   ← alinhado
    // i_rSetpoint : REAL
    @JvmField
    var ALIGN_VARIABLE_COLONS: Boolean = true

    // Preservar linhas em branco entre instruções (máx 1)
    @JvmField
    var KEEP_BLANK_LINES_IN_CODE: Int = 1

    // Espaço dentro dos parênteses de chamada de FB
    // TON( IN := x ) vs TON(IN := x)
    @JvmField
    var SPACE_WITHIN_FB_CALL_PARENS: Boolean = false
}
```

**Registro no plugin.xml:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <codeStyleSettingsProvider
        implementation="com.yourplugin.scl.formatting.SclCodeStyleSettingsProvider"/>
    <langCodeStyleSettingsProvider
        implementation="com.yourplugin.scl.formatting.SclLanguageCodeStyleSettingsProvider"/>
</extensions>
```

---

## 7. Casos de Formatação — Exemplos Detalhados

### 7.1 CASE statement (mais complexo)

```scl
// ENTRADA (não formatado):
CASE s_nState OF
0: s_nState:=1;
1:
s_tTimer(IN:=TRUE,PT:=T#5S);
IF s_tTimer.Q THEN s_nState:=2; END_IF;
ELSE:
s_nState:=0;
END_CASE;

// SAÍDA (após Ctrl+Alt+L):
CASE s_nState OF
    0:
        s_nState := 1;
    1:
        s_tTimer(IN := TRUE, PT := T#5S);
        IF s_tTimer.Q THEN
            s_nState := 2;
        END_IF;
    ELSE:
        s_nState := 0;
END_CASE;
```

### 7.2 Declaração TYPE / STRUCT

```scl
// ENTRADA:
TYPE UDT_Motor:
STRUCT
bRunning:BOOL;
rSpeed:REAL:=0.0;
nFaultCode:INT;
END_STRUCT;
END_TYPE

// SAÍDA:
TYPE UDT_Motor :
    STRUCT
        bRunning   : BOOL;
        rSpeed     : REAL := 0.0;
        nFaultCode : INT;
    END_STRUCT;
END_TYPE
```

### 7.3 Chamada de FB com parâmetros

```scl
// ENTRADA:
s_tTimer(IN:=i_bStart,PT:=T#10S,Q=>q_bDone,ET=>s_tElapsed);

// SAÍDA:
s_tTimer(IN := i_bStart, PT := T#10S, Q => q_bDone, ET => s_tElapsed);
```

---

## 8. Trecho do PSI Tree Necessário

Para que o formatter funcione, os seguintes PSI types devem existir na gramática BNF.
Verifique se estão presentes antes de implementar:

```
SclFunctionBlock          → FUNCTION_BLOCK name VAR_sections BEGIN code END_FUNCTION_BLOCK
SclVarInputSection        → VAR_INPUT varDecl* END_VAR
SclVarOutputSection       → VAR_OUTPUT varDecl* END_VAR
SclVarStaticSection       → VAR varDecl* END_VAR
SclVarTempSection         → VAR_TEMP varDecl* END_VAR
SclVariableDeclaration    → name COLON dataType (ASSIGN expr)? SEMICOLON
SclCodeSection            → BEGIN statement* END_FUNCTION_BLOCK
SclIfStatement            → IF expr THEN body (ELSIF expr THEN body)* (ELSE body)? END_IF
SclIfThenPart             → THEN statement+
SclElsePart               → ELSE statement+
SclForStatement           → FOR var ASSIGN expr TO expr (BY expr)? DO body END_FOR
SclForBody                → DO statement+
SclWhileStatement         → WHILE expr DO body END_WHILE
SclRepeatStatement        → REPEAT body UNTIL expr END_REPEAT
SclCaseStatement          → CASE expr OF branch+ (ELSE body)? END_CASE
SclCaseBranch             → label COLON statement*
SclStructType             → STRUCT field* END_STRUCT
SclStructField            → name COLON dataType (ASSIGN expr)? SEMICOLON
```

---

## 9. plugin.xml — Registro Completo do Formatter

```xml
<extensions defaultExtensionNs="com.intellij">

    <!-- Formatter principal (Ctrl+Alt+L) -->
    <lang.formatter
        language="SCL"
        implementationClass="com.yourplugin.scl.formatting.SclFormattingModelBuilder"/>

    <!-- Configurações customizáveis em Settings → Editor → Code Style → SCL -->
    <codeStyleSettingsProvider
        implementation="com.yourplugin.scl.formatting.SclCodeStyleSettingsProvider"/>

    <langCodeStyleSettingsProvider
        implementation="com.yourplugin.scl.formatting.SclLanguageCodeStyleSettingsProvider"/>

</extensions>
```

---

## 10. Checklist de Testes

- [ ] `Ctrl+Alt+L` em arquivo sem formatação → produz saída idêntica ao "Depois" do item 0
- [ ] Declarações `VAR_INPUT` recuam 4 espaços, `END_VAR` volta ao nível 0
- [ ] `IF/THEN/ELSIF/ELSE/END_IF` indentam corretamente em múltiplos níveis
- [ ] `CASE/OF/END_CASE` indenta rótulos 4 espaços, código 8 espaços
- [ ] `:=` tem 1 espaço antes e depois em todos os contextos
- [ ] `: TIPO` em declaração tem espaço antes e depois do `:`
- [ ] `,` em chamada de FB: sem espaço antes, 1 depois
- [ ] Comentários `//` e `(* *)` são preservados sem modificação
- [ ] Literais `T#5S`, `T#500MS` não são alterados
- [ ] `Ctrl+Alt+L` é idempotente: formatar 2× dá o mesmo resultado que 1×
- [ ] Enter após `THEN` indenta automaticamente 4 espaços
- [ ] Enter dentro de `VAR_INPUT` indenta para nova declaração

---

## 11. Observação Importante: Não Normalizar Case das Keywords

O formatter **NÃO deve** converter `if` para `IF` ou vice-versa.
Motivo: SCL é case-insensitive por spec, e alguns engenheiros preferem lowercase.
Esse comportamento pode ser feature separada via `PostFormatProcessor` opcional,
mas **não faz parte do formatter básico**.

Se quiser implementar no futuro, criar uma classe separada:
```kotlin
class SclKeywordCasePostFormatProcessor : PostFormatProcessor {
    // Opcional — converte keywords para UPPERCASE após o formatter rodar
}
```
