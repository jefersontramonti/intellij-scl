# DIAGNÓSTICO: Formatter Ctrl+Alt+L não funciona
# Passe este arquivo para o Claude Code ANTES de qualquer correção

---

## PASSO 1 — Identificar o sintoma exato

Qual dos casos abaixo descreve o problema?

**Caso A:** `Ctrl+Alt+L` não faz absolutamente nada (nem mensagem de erro)
**Caso B:** Aparece erro/exceção no Event Log ou idea.log
**Caso C:** Roda mas não muda nada no arquivo (indentação permanece igual)
**Caso D:** Roda mas produz resultado errado (indentação quebrada, espaços errados)
**Caso E:** Funciona em parte do arquivo mas quebra em outra

---

## PASSO 2 — Verificação mais rápida: o EP está registrado?

Abrir `plugin.xml` e confirmar que existe **exatamente**:

```xml
<extensions defaultExtensionNs="com.intellij">
    <lang.formatter
        language="SCL"
        implementationClass="com.seupackage.scl.formatting.SclFormattingModelBuilder"/>
</extensions>
```

### Erros comuns de registro:

```xml
<!-- ERRADO 1 — language ID errado (tem que ser o mesmo de SclLanguage.getID()) -->
<lang.formatter language="Scl" .../>      <!-- case-sensitive! -->
<lang.formatter language="scl" .../>      <!-- errado também -->

<!-- ERRADO 2 — namespace errado -->
<extensions defaultExtensionNs="com.seupackage">
    <lang.formatter .../>
</extensions>

<!-- ERRADO 3 — classe não compila / nome errado -->
<lang.formatter
    language="SCL"
    implementationClass="...SclFormattingModelBuilder"/>
<!-- verificar: esse é exatamente o nome da classe? pacote correto? -->
```

### Como descobrir o language ID correto:
```kotlin
// Em qualquer lugar do código, imprimir:
println(SclLanguage.INSTANCE.id)
// Ou verificar na definição da classe SclLanguage:
class SclLanguage private constructor() : Language("SCL") { ... }
//                                                   ^^^^^ esse é o ID
```

O `language="SCL"` no plugin.xml tem que ser **idêntico** ao string passado para `Language("SCL")`.

---

## CASO A — Ctrl+Alt+L não faz nada

### Causa mais comum: language ID não bate

Adicionar log para confirmar se o builder é chamado:

```kotlin
class SclFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        // LOG TEMPORÁRIO
        val log = com.intellij.openapi.diagnostic.Logger
            .getInstance(SclFormattingModelBuilder::class.java)
        log.warn("=== SCL FORMATTER createModel CALLED ===")
        log.warn("  file: ${formattingContext.containingFile.name}")
        log.warn("  lang: ${formattingContext.containingFile.language.id}")
        // FIM LOG

        val settings = formattingContext.codeStyleSettings
        val spacingBuilder = SclSpacingBuilder.create(settings)
        val rootBlock = SclBlock(
            node           = formattingContext.node,
            wrap           = Wrap.createWrap(WrapType.NONE, false),
            alignment      = null,
            settings       = settings,
            spacingBuilder = spacingBuilder,
            indent         = Indent.getNoneIndent()
        )
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            rootBlock,
            settings
        )
    }
}
```

Rodar `./gradlew runIde`, abrir arquivo `.scl`, pressionar `Ctrl+Alt+L`.
Verificar idea.log por `=== SCL FORMATTER createModel CALLED ===`.

- **Não aparece** → problema de registro (EP errado, language ID errado)
- **Aparece** → o builder é chamado, problema está em outro lugar (ir para Caso C/D)

---

## CASO B — Exceção no log

Procurar no `idea.log` por:
```
com.intellij.openapi.util.TextRange
NullPointerException
IndexOutOfBoundsException
```

### Causa mais comum: `getIndent()` retornando `null`

```kotlin
// ERRADO — nunca retornar null em getIndent()
override fun getIndent(): Indent? = null

// CORRETO — sempre retornar um Indent válido
override fun getIndent(): Indent = indent  // onde indent é o campo da classe
// ou
override fun getIndent(): Indent = Indent.getNoneIndent()
```

### Causa: `buildChildren()` incluindo WHITE_SPACE

```kotlin
// ERRADO — inclui tokens de espaço, o formatter fica confuso
override fun buildChildren(): List<Block> {
    val blocks = mutableListOf<Block>()
    var child = myNode.firstChildNode
    while (child != null) {
        blocks.add(SclBlock(child, ...))   // ← inclui WHITE_SPACE
        child = child.treeNext
    }
    return blocks
}

// CORRETO — pular WHITE_SPACE explicitamente
override fun buildChildren(): List<Block> {
    val blocks = mutableListOf<Block>()
    var child = myNode.firstChildNode
    while (child != null) {
        if (child.elementType != TokenType.WHITE_SPACE) {  // ← ESSENCIAL
            blocks.add(SclBlock(child, ...))
        }
        child = child.treeNext
    }
    return blocks
}
```

---

## CASO C — Roda mas não muda nada

### Causa mais comum 1: PSI types não batem com os gerados pelo Grammar-Kit

O `SclBlock.computeChildIndent()` usa tipos como `SclTypes.VAR_INPUT_SECTION`,
`SclTypes.CODE_SECTION` etc. Se esses nomes não existirem no Grammar-Kit gerado,
o `when()` cai sempre no `else -> Indent.getNoneIndent()` e nada muda.

**Verificação:**
```kotlin
// Adicionar em buildChildren() para ver quais tipos realmente existem:
var child = myNode.firstChildNode
while (child != null) {
    if (child.elementType != TokenType.WHITE_SPACE) {
        log.warn("  child type: ${child.elementType} | text: '${child.text.take(20)}'")
    }
    child = child.treeNext
}
```

Rodar e ver no log quais `elementType` realmente aparecem.
Comparar com o que está no `when()` em `computeChildIndent()`.

**Fix: mapear os tipos reais**

Os nomes gerados pelo Grammar-Kit seguem o padrão do BNF. Por exemplo:
- Se a regra BNF é `varInputSection ::= ...` → tipo é `SclTypes.VAR_INPUT_SECTION`
- Se a regra BNF é `var_input_section ::= ...` → tipo pode ser diferente

Verificar em `SclTypes.kt` (gerado pelo Grammar-Kit) quais constantes existem
para seções VAR, blocos IF, FOR, CASE, etc.

### Causa mais comum 2: SpacingBuilder com tokens errados

O `SclSpacingBuilder.create()` usa `SclTypes.ASSIGN`, `SclTypes.COLON` etc.
Se esses tokens não existirem com esses nomes, as regras são silenciosamente ignoradas.

**Verificação:**
```kotlin
// Verificar quais tokens de operador existem em SclTypes
// Abrir SclTypes.kt e procurar por ASSIGN, COLON, SEMICOLON, etc.
// Os nomes DEVEM existir como constantes IElementType
```

**Fix típico de nomes de tokens:**
```kotlin
// Nomes comuns gerados pelo Grammar-Kit para SCL:
SclTypes.ASSIGN        // para :=
SclTypes.COLON         // para :
SclTypes.SEMICOLON     // para ;
SclTypes.COMMA         // para ,
SclTypes.LPAREN        // para (
SclTypes.RPAREN        // para )
SclTypes.EQ            // para =
SclTypes.DOT           // para .

// Se o Grammar-Kit gerou nomes diferentes, ajustar o SpacingBuilder
// para usar os nomes reais
```

---

## CASO D — Indentação produz resultado errado

### Causa: `getChildAttributes()` retornando indent errado para Enter

```kotlin
// Verificar se getChildAttributes() está implementado
// Se não estiver, Enter após THEN não indenta corretamente

override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    return when (myNode.elementType) {
        SclTypes.VAR_INPUT_SECTION,
        SclTypes.VAR_STATIC_SECTION,
        SclTypes.VAR_TEMP_SECTION,
        SclTypes.VAR_OUTPUT_SECTION -> ChildAttributes(Indent.getNormalIndent(), null)

        SclTypes.CODE_SECTION       -> ChildAttributes(Indent.getNormalIndent(), null)
        SclTypes.IF_STATEMENT       -> ChildAttributes(Indent.getNormalIndent(), null)
        SclTypes.FOR_STATEMENT      -> ChildAttributes(Indent.getNormalIndent(), null)
        SclTypes.CASE_STATEMENT     -> ChildAttributes(Indent.getNormalIndent(), null)

        else -> ChildAttributes(Indent.getNoneIndent(), null)
    }
}
```

### Causa: `indent` calculado incorretamente para nós filhos

O `indent` de um `SclBlock` é calculado **no momento da construção do pai**,
não pelo próprio bloco. Se o pai não calcular o indent correto para os filhos,
o resultado fica errado.

```kotlin
// Em buildChildren() do pai:
val childIndent = computeChildIndent(child)  // ← calculado pelo PAI
blocks.add(
    SclBlock(
        node   = child,
        indent = childIndent,   // ← passado no construtor
        ...
    )
)
```

Se o pai não estiver calculando o indent dos filhos corretamente,
adicionar log:
```kotlin
log.warn("  parent: ${myNode.elementType} | child: ${child.elementType} | indent: $childIndent")
```

---

## ABORDAGEM ALTERNATIVA — Implementação mínima garantida

Se após o diagnóstico ainda não funcionar, trocar para uma implementação
**mínima e simples** que funciona com certeza, depois incrementar:

```kotlin
// SclFormattingModelBuilder.kt — versão mínima
class SclFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        val spacing = SpacingBuilder(settings, SclLanguage.INSTANCE)
            // Apenas as regras mais simples — expandir depois
            .before(findToken("SEMICOLON")).none()
            .after(findToken("COMMA")).spaces(1)
            .before(findToken("COMMA")).none()

        val root = SclBlockMinimal(formattingContext.node, spacing)

        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            root,
            settings
        )
    }

    // Helper: encontrar token pelo nome sem crashar se não existir
    private fun findToken(name: String): IElementType? =
        SclTypes::class.java.fields
            .firstOrNull { it.name == name }
            ?.get(null) as? IElementType
}
```

```kotlin
// SclBlockMinimal.kt — bloco mínimo que pelo menos funciona
class SclBlockMinimal(
    node: ASTNode,
    private val spacing: SpacingBuilder
) : AbstractBlock(node, Wrap.createWrap(WrapType.NONE, false), null) {

    override fun buildChildren(): List<Block> {
        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode
        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                blocks.add(SclBlockMinimal(child, spacing))
            }
            child = child.treeNext
        }
        return blocks
    }

    // Sem indentação por enquanto — só espaçamento
    override fun getIndent(): Indent = Indent.getNoneIndent()

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacing.getSpacing(this, child1, child2)

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    override fun getChildAttributes(newChildIndex: Int) =
        ChildAttributes(Indent.getNoneIndent(), null)
}
```

**Esta versão mínima faz apenas espaçamento, sem indentação.**
Se funcionar, significa que a estrutura básica está certa e o problema
era nos tipos PSI usados em `computeChildIndent()`.
Depois incrementar adicionando indentação bloco por bloco.

---

## CHECKLIST DE DIAGNÓSTICO — executar nesta ordem

```
[ ] 1. Verificar language ID: SclLanguage.INSTANCE.id == "SCL" (case-sensitive)
[ ] 2. Verificar plugin.xml: <lang.formatter language="SCL" .../>
[ ] 3. Adicionar log em createModel() e rodar Ctrl+Alt+L
[ ] 4. Log aparece? Se não → problema de registro
[ ] 5. Log aparece? Verificar idea.log por exceções
[ ] 6. Sem exceção mas sem mudança → adicionar log em buildChildren()
[ ] 7. Ver quais elementType realmente existem nos filhos do arquivo
[ ] 8. Comparar com os nomes usados em computeChildIndent() e SpacingBuilder
[ ] 9. Corrigir nomes de tipos para bater com o que Grammar-Kit gerou
[ ] 10. Se ainda falhar → usar implementação mínima e incrementar
```

---

## COMO VER O idea.log

```powershell
# Windows — filtrar por SCL FORMATTER
Get-Content "$env:APPDATA\JetBrains\IdeaIC2026.1\log\idea.log" -Wait |
    Select-String "SCL FORMATTER"
```

Ou: `Help → Show Log in Explorer → abrir idea.log`
