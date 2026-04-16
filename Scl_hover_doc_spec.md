# ESPECIFICAÇÃO: Hover Documentation para Variáveis SCL
**Documento para Claude Code CLI — implementar Quick Documentation on Hover**

---

## 0. Comportamento Esperado

Ao passar o mouse sobre qualquer identificador SCL que seja uma variável declarada,
deve aparecer um tooltip com:

```
┌──────────────────────────────────────┐
│ STOP                                 │
│ ──────────────────────────────────── │
│ botão de emergencia                  │
│                                      │
│ Type:    BOOL                        │
│ Section: VAR_INPUT                   │
│ Block:   FB_CentralControl           │
└──────────────────────────────────────┘
```

**Formato mínimo (hover rápido):** `botão de emergencia: BOOL`
**Formato completo (Ctrl+Q / View → Quick Documentation):** HTML completo com seção, bloco e valor inicial.

---

## 1. De onde vem a descrição da variável

SCL aceita comentários em duas posições relativas à declaração:

### Padrão 1 — Comentário de linha ao final (mais comum no TIA Portal)
```scl
VAR_INPUT
    STOP      : BOOL;   // botão de emergencia
    SETPOINT  : REAL;   // valor alvo de pressão em bar
END_VAR
```

### Padrão 2 — Comentário de bloco na linha anterior
```scl
VAR_INPUT
    (* botão de emergencia *)
    STOP : BOOL;
END_VAR
```

### Padrão 3 — Sem comentário (mostrar apenas tipo e seção)
```scl
VAR_STATIC
    s_nState : INT;
END_VAR
```
→ Hover mostra: `s_nState: INT  [STATIC]`

A lógica de extração deve tentar **Padrão 1 primeiro**, depois **Padrão 2**,
e silenciosamente omitir descrição se nenhum existir.

---

## 2. API Correta para IntelliJ IDEA 2026.1

**USAR:** `Documentation Target API` (introduzida em 2023.1 — NÃO usar o deprecated `DocumentationProvider`)

Três peças necessárias:
1. `SclDocumentationTargetProvider` — encontra o PSI element sob o cursor e retorna o Target
2. `SclVariableDocumentationTarget` — gera o HTML da documentação
3. Registro no `plugin.xml`

---

## 3. SclDocumentationTargetProvider

```kotlin
// src/main/kotlin/com/yourplugin/scl/documentation/SclDocumentationTargetProvider.kt

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class SclDocumentationTargetProvider : PsiDocumentationTargetProvider {

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        // Só processar elementos SCL
        if (element.language != SclLanguage.INSTANCE) return null

        // Caso 1: O element JÁ É uma declaração de variável
        if (element is SclVariableDeclaration) {
            return SclVariableDocumentationTarget(element)
        }

        // Caso 2: O element é um IDENTIFIER sendo usado como referência
        // → resolver para a declaração de origem
        if (element.node?.elementType == SclTypes.IDENTIFIER) {
            val declaration = resolveToDeclaration(element) ?: return null
            return SclVariableDocumentationTarget(declaration)
        }

        return null
    }

    /**
     * Sobe na PSI tree do arquivo para encontrar a declaração da variável
     * referenciada pelo identificador sob o cursor.
     *
     * Estratégia: busca primeiro no bloco pai (FB/FC/OB),
     * depois no arquivo inteiro (para DBs globais).
     */
    private fun resolveToDeclaration(identifier: PsiElement): SclVariableDeclaration? {
        val name = identifier.text ?: return null

        // Buscar no bloco pai mais próximo primeiro (escopo local)
        val containingBlock = PsiTreeUtil.getParentOfType(
            identifier,
            SclFunctionBlock::class.java,
            SclFunction::class.java,
            SclOrganizationBlock::class.java
        )

        if (containingBlock != null) {
            val found = findVarDeclByName(containingBlock, name)
            if (found != null) return found
        }

        // Fallback: buscar no arquivo inteiro (variáveis globais / DB)
        return findVarDeclByName(identifier.containingFile, name)
    }

    private fun findVarDeclByName(scope: PsiElement, name: String): SclVariableDeclaration? {
        return PsiTreeUtil.findChildrenOfType(scope, SclVariableDeclaration::class.java)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
        // SCL é case-insensitive por spec IEC 61131-3
    }
}
```

---

## 4. SclVariableDocumentationTarget

```kotlin
// src/main/kotlin/com/yourplugin/scl/documentation/SclVariableDocumentationTarget.kt

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.createSmartPointer

class SclVariableDocumentationTarget(
    private val declaration: SclVariableDeclaration
) : DocumentationTarget {

    // ── Pointer para sobreviver a invalidação de PSI (obrigatório pela API) ──
    override fun createPointer() = declaration.createSmartPointer().let { ptr ->
        com.intellij.platform.backend.documentation.DocumentationTargetPointer {
            val restored = ptr.dereference() ?: return@DocumentationTargetPointer null
            SclVariableDocumentationTarget(restored)
        }
    }

    // ── Apresentação no Documentation Tool Window ──
    override fun computePresentation() =
        com.intellij.platform.backend.presentation.TargetPresentation.builder(declaration.name ?: "")
            .icon(resolveIcon())
            .presentation()

    // ─────────────────────────────────────────────────────────────────────────
    // HOVER RÁPIDO (Ctrl+hover ou "Show quick documentation on hover" ativado)
    // Retorna string simples: "descrição: TIPO  [SECAO]"
    // ─────────────────────────────────────────────────────────────────────────
    override fun computeDocumentationHint(): String {
        val name     = declaration.name ?: return ""
        val dataType = declaration.dataType?.text ?: "?"
        val section  = resolveSectionLabel()
        val comment  = extractComment()

        return buildString {
            if (comment.isNotBlank()) {
                append("<b>$name</b> — $comment")
            } else {
                append("<b>$name</b>")
            }
            append(": <code>$dataType</code>")
            append("  <i>[$section]</i>")
        }
        // Exemplo de saída: "<b>STOP</b> — botão de emergencia: <code>BOOL</code>  <i>[INPUT]</i>"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCUMENTAÇÃO COMPLETA (Ctrl+Q / View → Quick Documentation)
    // Retorna HTML formatado com DocumentationMarkup
    // ─────────────────────────────────────────────────────────────────────────
    override fun computeDocumentation(): DocumentationResult {
        val name        = declaration.name ?: return DocumentationResult.EMPTY
        val dataType    = declaration.dataType?.text ?: "?"
        val section     = resolveSectionLabel()
        val blockName   = resolveContainingBlockName()
        val initValue   = declaration.initialValue?.text
        val comment     = extractComment()

        val html = buildString {
            // ── Cabeçalho: nome da variável ──
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$name</b>")
            append(DocumentationMarkup.DEFINITION_END)

            // ── Corpo: descrição (se existir) ──
            if (comment.isNotBlank()) {
                append(DocumentationMarkup.CONTENT_START)
                append(comment)
                append(DocumentationMarkup.CONTENT_END)
            }

            // ── Seção de detalhes ──
            append(DocumentationMarkup.SECTIONS_START)

            append(DocumentationMarkup.SECTION_HEADER_START)
            append("Type")
            append(DocumentationMarkup.SECTION_SEPARATOR)
            append("<code>$dataType</code>")
            append(DocumentationMarkup.SECTION_END)

            append(DocumentationMarkup.SECTION_HEADER_START)
            append("Section")
            append(DocumentationMarkup.SECTION_SEPARATOR)
            append(section)
            append(DocumentationMarkup.SECTION_END)

            if (blockName != null) {
                append(DocumentationMarkup.SECTION_HEADER_START)
                append("Block")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append(blockName)
                append(DocumentationMarkup.SECTION_END)
            }

            if (initValue != null) {
                append(DocumentationMarkup.SECTION_HEADER_START)
                append("Default")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append("<code>$initValue</code>")
                append(DocumentationMarkup.SECTION_END)
            }

            append(DocumentationMarkup.SECTIONS_END)
        }

        return DocumentationResult.documentation(html)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extrai o comentário associado à declaração.
     * Tenta Padrão 1 (fim de linha) → Padrão 2 (linha anterior).
     */
    private fun extractComment(): String {
        // Padrão 1: comentário de linha "//" ao final da mesma linha
        val inlineComment = findInlineComment()
        if (inlineComment != null) return inlineComment

        // Padrão 2: comentário de bloco "(* ... *)" na linha ANTERIOR
        val precedingComment = findPrecedingBlockComment()
        if (precedingComment != null) return precedingComment

        return ""
    }

    /**
     * Padrão 1: busca o próximo sibling de tipo LINE_COMMENT na mesma linha.
     * SCL: STOP : BOOL;   // botão de emergencia
     */
    private fun findInlineComment(): String? {
        var sibling = declaration.nextSibling
        while (sibling != null) {
            val elementType = sibling.node?.elementType

            // Parou na próxima linha — não há comentário inline
            if (elementType == SclTypes.CRLF || elementType == SclTypes.NEWLINE) break

            if (elementType == SclTypes.LINE_COMMENT) {
                return sibling.text
                    .removePrefix("//")
                    .trim()
            }
            sibling = sibling.nextSibling
        }
        return null
    }

    /**
     * Padrão 2: busca o sibling ANTERIOR de tipo BLOCK_COMMENT.
     * SCL:
     *   (* botão de emergencia *)
     *   STOP : BOOL;
     */
    private fun findPrecedingBlockComment(): String? {
        var sibling = declaration.prevSibling
        while (sibling != null) {
            val elementType = sibling.node?.elementType

            // Ignorar whitespace/newlines ao subir
            if (elementType == SclTypes.WHITE_SPACE ||
                elementType == SclTypes.CRLF ||
                elementType == SclTypes.NEWLINE) {
                sibling = sibling.prevSibling
                continue
            }

            if (elementType == SclTypes.BLOCK_COMMENT) {
                return sibling.text
                    .removePrefix("(*")
                    .removeSuffix("*)")
                    .trim()
            }

            break // Outro elemento — parar busca
        }
        return null
    }

    /**
     * Resolve qual seção VAR contém esta declaração.
     * Retorna label legível para exibição.
     */
    private fun resolveSectionLabel(): String {
        val parent = declaration.parent ?: return "VAR"
        return when (parent) {
            is SclVarInputSection  -> "VAR_INPUT"
            is SclVarOutputSection -> "VAR_OUTPUT"
            is SclVarInOutSection  -> "VAR_IN_OUT"
            is SclVarStaticSection -> "VAR (STATIC)"
            is SclVarTempSection   -> "VAR_TEMP"
            is SclConstSection     -> "CONST"
            else                   -> "VAR"
        }
    }

    /**
     * Retorna o nome do bloco (FB/FC/OB) que contém a variável.
     * Ex: "FB_CentralControl"
     */
    private fun resolveContainingBlockName(): String? {
        val block = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            declaration,
            SclFunctionBlock::class.java,
            SclFunction::class.java,
            SclOrganizationBlock::class.java
        ) ?: return null

        return when (block) {
            is SclFunctionBlock      -> block.name
            is SclFunction           -> block.name
            is SclOrganizationBlock  -> block.name
            else                     -> null
        }
    }

    /** Ícone baseado na seção VAR (consistente com o completion) */
    private fun resolveIcon(): javax.swing.Icon {
        val parent = declaration.parent ?: return com.intellij.icons.AllIcons.Nodes.Variable
        return when (parent) {
            is SclVarInputSection  -> com.intellij.icons.AllIcons.Nodes.Parameter
            is SclVarOutputSection -> com.intellij.icons.AllIcons.Nodes.Property
            is SclVarInOutSection  -> com.intellij.icons.AllIcons.Nodes.PropertyRead
            is SclVarStaticSection -> com.intellij.icons.AllIcons.Nodes.Field
            is SclVarTempSection   -> com.intellij.icons.AllIcons.Nodes.Variable
            is SclConstSection     -> com.intellij.icons.AllIcons.Nodes.Constant
            else                   -> com.intellij.icons.AllIcons.Nodes.Variable
        }
    }
}
```

---

## 5. Registro no plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">

    <!-- Hover documentation (API moderna 2023.1+) -->
    <platform.backend.documentation.psiTargetProvider
        implementation="com.yourplugin.scl.documentation.SclDocumentationTargetProvider"/>

</extensions>
```

> **ATENÇÃO:** Não registrar como `com.intellij.lang.documentationProvider` —
> esse é o EP deprecated. Usar somente `platform.backend.documentation.psiTargetProvider`.

---

## 6. Ativar "Show quick documentation on hover" automaticamente

Para que o hover funcione sem o usuário precisar pressionar Ctrl, o hover popup
já aparece por padrão se o usuário tiver ativado:

**Settings → Editor → Code Editing → Show quick documentation on hover**

Nenhuma configuração extra no plugin é necessária. O `computeDocumentationHint()`
é exatamente o que aparece nesse tooltip rápido.

---

## 7. Exemplos Concretos de Saída

### Cenário A — VAR_INPUT com comentário inline
```scl
VAR_INPUT
    STOP : BOOL;  // botão de emergencia
END_VAR
```
**Hover rápido:**
```html
<b>STOP</b> — botão de emergencia: <code>BOOL</code>  <i>[VAR_INPUT]</i>
```

**Ctrl+Q (popup completo):**
```
STOP
──────────────────
botão de emergencia

Type      BOOL
Section   VAR_INPUT
Block     FB_CentralControl
```

---

### Cenário B — VAR_STATIC sem comentário
```scl
VAR
    s_nState : INT := 0;
END_VAR
```
**Hover rápido:**
```html
<b>s_nState</b>: <code>INT</code>  <i>[VAR (STATIC)]</i>
```

**Ctrl+Q (popup completo):**
```
s_nState
────────────
Type      INT
Section   VAR (STATIC)
Block     FB_CentralControl
Default   0
```

---

### Cenário C — Membro de timer (acesso via ponto)
```scl
VAR
    s_tTimer : TON;
END_VAR
BEGIN
    IF s_tTimer.Q THEN  ← hover aqui mostra info de s_tTimer
```
**Hover rápido:**
```html
<b>s_tTimer</b>: <code>TON</code>  <i>[VAR (STATIC)]</i>
```

---

## 8. Checklist de Testes

- [ ] Hover sobre `STOP` → aparece "botão de emergencia: BOOL  [VAR_INPUT]"
- [ ] Hover sobre variável SEM comentário → aparece "nome: TIPO  [SEÇÃO]"
- [ ] Ctrl+Q sobre variável → popup HTML completo com seção e bloco
- [ ] Hover dentro de comentário → NÃO aparece nada (não é variável)
- [ ] Hover sobre keyword (`IF`, `THEN`) → NÃO aparece nada
- [ ] Hover sobre `s_tTimer.Q` → resolve para `s_tTimer` e mostra tipo `TON`
- [ ] Variável SCL é case-insensitive: `stop` e `STOP` resolvem para o mesmo PSI
- [ ] Hover no popup de autocomplete (Ctrl+Q na lista) → mostra a mesma info

---

## 9. Dependência: Settings → Editor → Code Editing

Para garantir que os usuários saibam ativar o hover, considere adicionar uma
notificação de boas-vindas no primeiro uso do plugin:

```kotlin
// Na inicialização do plugin (opcional mas recomendado)
class SclPluginStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val settings = EditorSettingsExternalizable.getInstance()
        if (!settings.isShowQuickDocOnMouseOverElement) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("SCL Plugin")
                .createNotification(
                    "SCL Plugin",
                    "Ative <b>Settings → Editor → Code Editing → Show quick documentation on hover</b> " +
                    "para ver tipo e descrição das variáveis ao passar o mouse.",
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }
}
```

---

## 10. Resumo das Classes a Criar

```
src/main/kotlin/com/yourplugin/scl/documentation/
├── SclDocumentationTargetProvider.kt   ← encontra PSI sob cursor e cria Target
└── SclVariableDocumentationTarget.kt   ← gera HTML do hover e Ctrl+Q

src/main/kotlin/com/yourplugin/scl/startup/
└── SclPluginStartupActivity.kt         ← (opcional) notificação de boas-vindas
```

**plugin.xml:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <platform.backend.documentation.psiTargetProvider
        implementation="...documentation.SclDocumentationTargetProvider"/>
</extensions>

<!-- Opcional: startup notification -->
<extensions defaultExtensionNs="com.intellij">
    <postStartupActivity
        implementation="...startup.SclPluginStartupActivity"/>
</extensions>
```
