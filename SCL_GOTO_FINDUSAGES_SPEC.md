# ESPECIFICAÇÃO: Go to Definition + Find Usages
**Documento para Claude Code CLI — Fase 6**

---

## 0. Comportamento Esperado

### Go to Definition (Ctrl+Click ou Ctrl+B)

```scl
VAR
    s_BlinkTimer : TON;     ← declaração
    s_BlinkState : Bool;
END_VAR

BEGIN
    s_BlinkTimer(IN := ...); ← Ctrl+Click aqui → salta para declaração acima
    s_BlinkState := TRUE;    ← Ctrl+Click aqui → salta para declaração acima

    IF s_BlinkState THEN     ← Ctrl+Click aqui → salta para declaração acima
```

**Cenários suportados:**

| Cursor em | Navega para |
|---|---|
| `s_BlinkTimer` no código | `s_BlinkTimer : TON` na VAR section |
| `"FB_TankControl_DB"` no OB | `FUNCTION_BLOCK "FB_TankControl"` |
| `i_State` em FB call | `i_State : Int` no VAR_INPUT |
| `s_BlinkTimer.Q` | `s_BlinkTimer : TON` na VAR section |

---

### Find Usages (Alt+F7)

```
Alt+F7 em s_BlinkTimer na declaração mostra:

Usages of 's_BlinkTimer' (3 usages)
  ├── FB_StackLight.scl
  │   ├── line 42: s_BlinkTimer(IN := NOT s_BlinkState, PT := T#500MS);
  │   ├── line 43: IF s_BlinkTimer.Q THEN
  │   └── line 44:     s_BlinkState := NOT s_BlinkTimer.Q;
```

---

## 1. Arquitetura — 5 Componentes

```
src/main/kotlin/com/scl/plugin/
├── psi/
│   ├── SclNamedElement.kt          ← interface PsiNamedElement para variáveis
│   ├── SclNamedElementImpl.kt      ← implementação base
│   └── SclVarDeclMixin.kt          ← mixin para SclVarDecl implementar PsiNamedElement
├── reference/
│   ├── SclReference.kt             ← PsiReference — resolve uso → declaração
│   └── SclReferenceContributor.kt  ← registra referências em identificadores SCL
└── findUsages/
    └── SclFindUsagesProvider.kt    ← FindUsagesProvider — configura busca
```

---

## 2. SclNamedElement — Interface Base

```kotlin
// src/main/kotlin/com/scl/plugin/psi/SclNamedElement.kt

interface SclNamedElement : PsiNamedElement, NavigatablePsiElement
```

---

## 3. SclNamedElementImpl — Implementação Base

```kotlin
// src/main/kotlin/com/scl/plugin/psi/SclNamedElementImpl.kt

abstract class SclNamedElementImpl(node: ASTNode) :
    ASTWrapperPsiElement(node), SclNamedElement {

    override fun getName(): String? =
        node.findChildByType(SclTypes.IDENTIFIER)?.text
            ?: node.findChildByType(SclTypes.QUOTED_IDENTIFIER)?.text
                ?.removeSurrounding("\"")

    override fun setName(name: String): PsiElement {
        val newNameNode = SclPsiElementFactory.createIdentifier(project, name)
        val nameNode = node.findChildByType(SclTypes.IDENTIFIER)
        if (nameNode != null) {
            node.replaceChild(nameNode, newNameNode.node)
        }
        return this
    }

    override fun getNameIdentifier(): PsiElement? =
        node.findChildByType(SclTypes.IDENTIFIER)?.psi
            ?: node.findChildByType(SclTypes.QUOTED_IDENTIFIER)?.psi

    override fun getTextOffset(): Int =
        nameIdentifier?.textOffset ?: super.getTextOffset()
}
```

---

## 4. SclVarDeclMixin — VarDecl como Named Element

Adicionar ao BNF para que `SclVarDeclImpl` implemente `SclNamedElement`:

```
// No Scl.bnf, modificar:
varDecl ::= IDENTIFIER COLON typeRef (ASSIGN expression)? SEMICOLON {
    pin=1
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
}

// Também para functionBlockDecl, functionDecl, orgBlockDecl:
functionBlockDecl ::= FUNCTION_BLOCK (quoted_id | IDENTIFIER) blockAttr? ... {
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
}
```

---

## 5. SclReference — Resolve Uso → Declaração

```kotlin
// src/main/kotlin/com/scl/plugin/reference/SclReference.kt

class SclReference(
    element: PsiElement,
    private val rangeInElement: TextRange
) : PsiReferenceBase<PsiElement>(element, rangeInElement) {

    // Nome que estamos tentando resolver
    private val referencedName: String
        get() = element.text.let { text ->
            rangeInElement.substring(text).trim('"')
        }

    // ── RESOLVE: encontra a declaração ──────────────────────────
    override fun resolve(): PsiElement? {
        val name = referencedName

        // 1. Buscar em VAR sections do bloco atual
        val localDecl = resolveInLocalScope(name)
        if (localDecl != null) return localDecl

        // 2. Buscar FUNCTION_BLOCK com esse nome no projeto
        // (para "FB_TankControl_DB" → busca FB_TankControl)
        val fbDecl = resolveAsGlobalFb(name)
        if (fbDecl != null) return fbDecl

        return null
    }

    private fun resolveInLocalScope(name: String): PsiElement? {
        // Subir na árvore PSI até o bloco pai (FB, FC, OB)
        val block = PsiTreeUtil.getParentOfType(
            element,
            SclFunctionBlockDeclImpl::class.java,
            SclFunctionDeclImpl::class.java,
            SclOrgBlockDeclImpl::class.java
        ) ?: return null

        // Buscar em todas as VAR sections
        return PsiTreeUtil.findChildrenOfType(block, SclVarDeclImpl::class.java)
            .firstOrNull { decl ->
                decl.name?.equals(name, ignoreCase = true) == true
            }
    }

    private fun resolveAsGlobalFb(name: String): PsiElement? {
        // Heurística: "FB_TankControl_DB" → remove "_DB" → "FB_TankControl"
        val fbName = name.removeSuffix("_DB")
            .removeSuffix("_db")
            .removeSurrounding("\"")

        // Buscar em todos os arquivos .scl do projeto
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)

        return FileTypeIndex.getFiles(SclFileType.INSTANCE, scope)
            .asSequence()
            .mapNotNull { vFile ->
                PsiManager.getInstance(project).findFile(vFile)
            }
            .flatMap { psiFile ->
                PsiTreeUtil.findChildrenOfType(
                    psiFile,
                    SclFunctionBlockDeclImpl::class.java
                ).asSequence()
            }
            .firstOrNull { fb ->
                fb.name?.equals(fbName, ignoreCase = true) == true
            }
    }

    // ── IS REFERENCE TO: para Find Usages ────────────────────────
    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is SclNamedElement) return false
        val resolved = resolve() ?: return false
        return resolved.isEquivalentTo(element)
    }

    // ── VARIANTS: para completion (já implementado na Fase 3) ────
    override fun getVariants(): Array<Any> = emptyArray()
}
```

---

## 6. SclReferenceContributor — Registra Referências

```kotlin
// src/main/kotlin/com/scl/plugin/reference/SclReferenceContributor.kt

class SclReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {

        // Referências em identificadores simples (s_BlinkTimer, i_State)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java)
                .withElementType(SclTypes.IDENTIFIER)
                .inFile(PlatformPatterns.psiFile(SclFile::class.java)),
            SclIdentifierReferenceProvider()
        )

        // Referências em quoted identifiers ("FB_TankControl_DB")
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java)
                .withElementType(SclTypes.QUOTED_IDENTIFIER)
                .inFile(PlatformPatterns.psiFile(SclFile::class.java)),
            SclQuotedIdentifierReferenceProvider()
        )
    }
}

// Provider para identificadores simples
class SclIdentifierReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        // Não criar referência para o próprio nó de declaração
        if (element.parent is SclVarDeclImpl) return PsiReference.EMPTY_ARRAY

        // Não criar referência para keywords usadas como nomes de tipo
        val text = element.text
        if (text.uppercase() in SclBuiltinFunctions.ALL_TYPE_NAMES) return PsiReference.EMPTY_ARRAY

        return arrayOf(SclReference(element, TextRange(0, element.textLength)))
    }
}

// Provider para quoted identifiers
class SclQuotedIdentifierReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext
    ): Array<PsiReference> {
        // Range exclui as aspas: "FB_Name" → range do conteúdo
        val text = element.text
        val range = TextRange(1, text.length - 1)
        return arrayOf(SclReference(element, range))
    }
}
```

---

## 7. SclFindUsagesProvider — Configura a Busca

```kotlin
// src/main/kotlin/com/scl/plugin/findUsages/SclFindUsagesProvider.kt

class SclFindUsagesProvider : FindUsagesProvider {

    // ── WordsScanner: indexa palavras em arquivos .scl ────────────
    override fun getWordsScanner(): WordsScanner =
        DefaultWordsScanner(
            SclLexerAdapter(),
            // Tokens tratados como identificadores (buscáveis)
            TokenSet.create(
                SclTypes.IDENTIFIER,
                SclTypes.QUOTED_IDENTIFIER
            ),
            // Tokens tratados como comentários (ignorados na busca por padrão)
            TokenSet.create(
                SclTypes.LINE_COMMENT,
                SclTypes.BLOCK_COMMENT
            ),
            // Tokens tratados como literais
            TokenSet.create(SclTypes.STRING_LITERAL)
        )

    // ── Quais elementos têm "Find Usages" ────────────────────────
    override fun canFindUsagesFor(psiElement: PsiElement): Boolean =
        psiElement is SclNamedElement

    // ── Tipo exibido no diálogo "Find Usages of X (type)" ────────
    override fun getType(element: PsiElement): String = when {
        element is SclVarDeclImpl -> {
            val section = PsiTreeUtil.getParentOfType(element, SclVarSectionImpl::class.java)
            when (section?.firstChild?.node?.elementType) {
                SclTypes.VAR_INPUT  -> "input variable"
                SclTypes.VAR_OUTPUT -> "output variable"
                SclTypes.VAR_IN_OUT -> "in/out variable"
                SclTypes.VAR_TEMP   -> "temp variable"
                else                -> "variable"
            }
        }
        element is SclFunctionBlockDeclImpl -> "function block"
        element is SclFunctionDeclImpl      -> "function"
        element is SclOrgBlockDeclImpl      -> "organization block"
        else -> "element"
    }

    // ── Nome descritivo no painel de resultados ───────────────────
    override fun getDescriptiveName(element: PsiElement): String =
        (element as? SclNamedElement)?.name ?: element.text.take(30)

    // ── Texto da linha no painel de resultados ────────────────────
    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        element.text.trim()

    override fun getHelpId(psiElement: PsiElement): String? = null
}
```

---

## 8. Registrar no plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">

    <!-- Go to Definition + Find Usages -->
    <psi.referenceContributor
        language="SCL"
        implementation="com.scl.plugin.reference.SclReferenceContributor"/>

    <lang.findUsagesProvider
        language="SCL"
        implementationClass="com.scl.plugin.findUsages.SclFindUsagesProvider"/>

</extensions>
```

---

## 9. Ajuste no BNF — mixin para PsiNamedElement

No arquivo `Scl.bnf`, adicionar `mixin` e `implements` nas regras principais:

```bnf
varDecl ::= IDENTIFIER COLON typeRef (ASSIGN expression)? SEMICOLON {
    pin=1
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
    methods=[getName setName getNameIdentifier getTextOffset]
}

functionBlockDecl ::= FUNCTION_BLOCK (quoted_id | IDENTIFIER) blockAttr? varSection* BEGIN statementList END_FUNCTION_BLOCK {
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
    methods=[getName setName getNameIdentifier]
}

functionDecl ::= FUNCTION (quoted_id | IDENTIFIER) COLON typeRef varSection* BEGIN statementList END_FUNCTION {
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
    methods=[getName setName getNameIdentifier]
}

orgBlockDecl ::= ORGANIZATION_BLOCK (quoted_id | IDENTIFIER) blockAttr? varSection* BEGIN statementList END_ORGANIZATION_BLOCK {
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
    methods=[getName setName getNameIdentifier]
}
```

Após modificar o BNF: `./gradlew generateSclParser`

---

## 10. Checklist de Testes

- [ ] `Ctrl+Click` em `s_BlinkTimer` no código → pula para `s_BlinkTimer : TON` na VAR
- [ ] `Ctrl+Click` em `s_BlinkState` no IF → pula para declaração na VAR
- [ ] `Ctrl+Click` em `"FB_TankControl_DB"` no OB → pula para o FB no arquivo
- [ ] `Ctrl+Click` em `i_State` dentro de FB call → pula para VAR_INPUT
- [ ] `Alt+F7` em declaração de variável → mostra todos os usos no painel
- [ ] `Alt+F7` em `s_BlinkTimer` → lista as 3 linhas de uso no FB
- [ ] Painel de Find Usages mostra tipo: "input variable", "variable", "function block"
- [ ] Clicar no resultado no painel → navega ao uso no editor
- [ ] Variáveis em outros arquivos .scl também são encontradas
- [ ] Hover com Ctrl sobre identificador → mostra tooltip com declaração

---

## 11. Problemas Comuns a Evitar

### ❌ ERRO 1: resolve() retorna o próprio elemento
```kotlin
// ERRADO — cria referência no nó de declaração também
registrar.registerReferenceProvider(
    PlatformPatterns.psiElement(PsiElement::class.java)
        .withElementType(SclTypes.IDENTIFIER), ...
)

// CORRETO — excluir o pai SclVarDeclImpl
if (element.parent is SclVarDeclImpl) return PsiReference.EMPTY_ARRAY
```

### ❌ ERRO 2: isReferenceTo() não implementado
```kotlin
// ERRADO — Find Usages não funciona sem isReferenceTo()
// O IntelliJ usa isReferenceTo() para verificar se uma referência
// aponta para o elemento buscado

// CORRETO:
override fun isReferenceTo(element: PsiElement): Boolean {
    val resolved = resolve() ?: return false
    return resolved.isEquivalentTo(element)
}
```

### ❌ ERRO 3: WordsScanner sem IDENTIFIER no tokenSet
```kotlin
// ERRADO — identificadores não são indexados
DefaultWordsScanner(lexer, TokenSet.EMPTY, comments, literals)

// CORRETO — incluir IDENTIFIER e QUOTED_IDENTIFIER
DefaultWordsScanner(
    lexer,
    TokenSet.create(SclTypes.IDENTIFIER, SclTypes.QUOTED_IDENTIFIER),
    comments,
    literals
)
```

### ❌ ERRO 4: mixin não declarado no BNF
```
// ERRADO — SclVarDeclImpl não implementa PsiNamedElement
// Find Usages nunca é ativado para variáveis

// CORRETO — declarar no BNF:
varDecl ::= ... {
    mixin="com.scl.plugin.psi.SclNamedElementImpl"
    implements="com.scl.plugin.psi.SclNamedElement"
}
```

### ❌ ERRO 5: Scope muito amplo para variáveis locais
```kotlin
// ERRADO — busca em todo o projeto para variável local
// Muito lento para projetos grandes

// CORRETO — para VAR_TEMP e VAR, limitar ao arquivo atual:
override fun getUseScope(): SearchScope {
    if (this is SclVarDeclImpl) {
        val section = PsiTreeUtil.getParentOfType(this, SclVarSectionImpl::class.java)
        if (section?.firstChild?.node?.elementType == SclTypes.VAR_TEMP) {
            // TEMP: escopo apenas do bloco atual
            return LocalSearchScope(
                PsiTreeUtil.getParentOfType(this, SclFunctionBlockDeclImpl::class.java)
                    ?: containingFile
            )
        }
    }
    // VAR_INPUT, VAR_OUTPUT, VAR_STATIC: escopo do arquivo
    return GlobalSearchScope.fileScope(containingFile)
}
```
