# ESPECIFICAÇÃO: Structure View SCL — `Alt+7`
**Documento para Claude Code CLI — implementar PsiStructureViewFactory**

---

## 0. Comportamento Esperado

`Alt+7` (ou `View → Tool Windows → Structure`) abre o painel lateral com a
árvore hierárquica completa do arquivo `.scl` atual.

### Visual da árvore (exemplo com FB + FC + UDT no mesmo arquivo):

```
📄 FB_TankControl.scl
│
├── 🟦 FUNCTION_BLOCK FB_TankControl
│   ├── 📥 VAR_INPUT
│   │   ├── 🟢 i_bStart : BOOL
│   │   ├── 🟢 i_bStop  : BOOL
│   │   └── 🟢 i_rLevel : REAL
│   ├── 📤 VAR_OUTPUT
│   │   ├── 🔴 q_bRunning : BOOL
│   │   └── 🔴 q_rFlow    : REAL
│   ├── 🔄 VAR_IN_OUT
│   │   └── 🟠 io_nMode : INT
│   ├── 💾 VAR (STATIC)
│   │   ├── 🟣 s_nState     : INT
│   │   ├── 🟣 s_tFillTimer : TON
│   │   └── 🟣 s_tDrainTimer: TON
│   └── ⚡ VAR_TEMP
│       └── ⚪ t_rCalc : REAL
│
├── 🟧 FUNCTION FC_CalcFlow
│   ├── 📥 VAR_INPUT
│   │   └── 🟢 i_rPressure : REAL
│   └── ↩️ RETURN: REAL
│
└── 🔷 TYPE UDT_TankConfig
    ├── rMaxLevel   : REAL
    ├── rMinLevel   : REAL
    └── nAlarmDelay : INT
```

**Comportamento de clique:** Clicar em qualquer item navega o cursor
para a declaração correspondente no editor.

**Sync automático:** Ao mover o cursor no editor, o item correspondente
na árvore fica destacado (selecionado) automaticamente.

---

## 1. Arquitetura — 5 Classes

```
src/main/kotlin/com/yourplugin/scl/structure/
├── SclStructureViewFactory.kt    ← entry point, registrado no plugin.xml
├── SclStructureViewModel.kt      ← modelo, define tipos de nós e filtros
├── SclStructureViewElement.kt    ← nó genérico (usado para FB, FC, OB)
├── SclVarSectionElement.kt       ← nó de seção VAR_INPUT / VAR_OUTPUT etc.
└── SclVariableElement.kt         ← nó folha de uma variável individual
```

---

## 2. SclStructureViewFactory — Entry Point

```kotlin
// src/main/kotlin/com/yourplugin/scl/structure/SclStructureViewFactory.kt

class SclStructureViewFactory : PsiStructureViewFactory {

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder {
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                return SclStructureViewModel(editor, psiFile)
            }
        }
    }
}
```

**Registro no plugin.xml:**
```xml
<extensions defaultExtensionNs="com.intellij">
    <lang.psiStructureViewFactory
        language="SCL"
        implementationClass="com.yourplugin.scl.structure.SclStructureViewFactory"/>
</extensions>
```

---

## 3. SclStructureViewModel — Modelo e Filtros

```kotlin
// src/main/kotlin/com/yourplugin/scl/structure/SclStructureViewModel.kt

class SclStructureViewModel(
    editor:  Editor?,
    psiFile: PsiFile
) : StructureViewModelBase(psiFile, editor, SclStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    // ── Tipos que ativam o "auto-select no editor" ───────────────────────
    // Quando o cursor está em qualquer um desses tipos, o nó correspondente
    // na árvore é destacado automaticamente.
    override fun getSuitableClasses(): Array<Class<out PsiElement>> = arrayOf(
        SclFunctionBlock::class.java,
        SclFunction::class.java,
        SclOrganizationBlock::class.java,
        SclTypeBlock::class.java,       // TYPE...END_TYPE
        SclVariableDeclaration::class.java
    )

    // ── Este elemento NUNCA é pai na árvore (é sempre folha) ─────────────
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element is SclVariableElement

    // ── Este elemento SEMPRE tem filhos (expandir por padrão) ────────────
    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
        element is SclStructureViewElement &&
        element.value is SclFunctionBlock

    // ── Filtros disponíveis na toolbar do painel ─────────────────────────
    override fun getFilters(): Array<Filter> = arrayOf(
        SclShowTempVarsFilter,    // toggle: mostrar/ocultar VAR_TEMP
        SclShowConstFilter        // toggle: mostrar/ocultar CONST
    )

    // ── Sorter (alfabético opcional) ─────────────────────────────────────
    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)
}
```

---

## 4. SclStructureViewElement — Nó Genérico (arquivo, FB, FC, OB, TYPE)

```kotlin
// src/main/kotlin/com/yourplugin/scl/structure/SclStructureViewElement.kt

class SclStructureViewElement(
    private val element: PsiElement
) : StructureViewTreeElement, SortableTreeElement {

    // ── Valor PSI associado ───────────────────────────────────────────────
    override fun getValue(): Any = element

    // ── Navegação: clicar no nó move o cursor para este elemento ─────────
    override fun navigate(requestFocus: Boolean) {
        (element as? NavigatablePsiElement)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() == true

    // ── Chave para ordenação alfabética ───────────────────────────────────
    override fun getAlphaSortKey(): String =
        (element as? PsiNamedElement)?.name ?: element.text.take(20)

    // ── Apresentação visual do nó ─────────────────────────────────────────
    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {

            override fun getPresentableText(): String? = when (element) {
                is PsiFile           -> element.name
                is SclFunctionBlock  -> element.name
                is SclFunction       -> element.name
                is SclOrganizationBlock -> element.name
                is SclTypeBlock      -> element.name   // nome do TYPE
                else                 -> element.text.take(30)
            }

            override fun getLocationString(): String? = null  // não mostrar path

            override fun getIcon(unused: Boolean): Icon? = when (element) {
                is PsiFile              -> SclIcons.FILE
                is SclFunctionBlock     -> SclIcons.FUNCTION_BLOCK  // ícone FB azul
                is SclFunction          -> SclIcons.FUNCTION        // ícone FC laranja
                is SclOrganizationBlock -> SclIcons.ORG_BLOCK       // ícone OB verde
                is SclTypeBlock         -> SclIcons.UDT             // ícone TYPE roxo
                else                    -> null
            }
        }
    }

    // ── Filhos na árvore ──────────────────────────────────────────────────
    override fun getChildren(): Array<TreeElement> {
        return when (element) {

            // Arquivo: lista todos os blocos de nível superior
            is PsiFile -> buildChildrenForFile(element)

            // Function Block: lista as seções VAR
            is SclFunctionBlock -> buildChildrenForFB(element)

            // Function: lista parâmetros de entrada + return type
            is SclFunction -> buildChildrenForFC(element)

            // TYPE: lista os campos do STRUCT
            is SclTypeBlock -> buildChildrenForType(element)

            // Organization Block: lista VAR_TEMP
            is SclOrganizationBlock -> buildChildrenForOB(element)

            else -> TreeElement.EMPTY_ARRAY
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // BUILDERS DE FILHOS
    // ─────────────────────────────────────────────────────────────────────

    private fun buildChildrenForFile(file: PsiFile): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()

        // Iterar filhos diretos do arquivo em ordem de aparição
        PsiTreeUtil.getChildrenOfType(file, PsiElement::class.java)?.forEach { child ->
            when (child) {
                is SclFunctionBlock     -> children.add(SclStructureViewElement(child))
                is SclFunction          -> children.add(SclStructureViewElement(child))
                is SclOrganizationBlock -> children.add(SclStructureViewElement(child))
                is SclTypeBlock         -> children.add(SclStructureViewElement(child))
            }
        }
        return children.toTypedArray()
    }

    private fun buildChildrenForFB(fb: SclFunctionBlock): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()

        // Adicionar cada seção VAR que tiver pelo menos 1 variável
        val sections = listOf(
            PsiTreeUtil.findChildOfType(fb, SclVarInputSection::class.java),
            PsiTreeUtil.findChildOfType(fb, SclVarOutputSection::class.java),
            PsiTreeUtil.findChildOfType(fb, SclVarInOutSection::class.java),
            PsiTreeUtil.findChildOfType(fb, SclVarStaticSection::class.java),
            PsiTreeUtil.findChildOfType(fb, SclVarTempSection::class.java),
            PsiTreeUtil.findChildOfType(fb, SclConstSection::class.java)
        )

        sections.filterNotNull().forEach { section ->
            val vars = PsiTreeUtil.findChildrenOfType(section, SclVariableDeclaration::class.java)
            if (vars.isNotEmpty()) {
                children.add(SclVarSectionElement(section))
            }
        }
        return children.toTypedArray()
    }

    private fun buildChildrenForFC(fc: SclFunction): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()

        // FC tem VAR_INPUT e VAR_OUTPUT (sem STATIC)
        val inputSection = PsiTreeUtil.findChildOfType(fc, SclVarInputSection::class.java)
        if (inputSection != null) {
            val vars = PsiTreeUtil.findChildrenOfType(inputSection, SclVariableDeclaration::class.java)
            if (vars.isNotEmpty()) children.add(SclVarSectionElement(inputSection))
        }

        val outputSection = PsiTreeUtil.findChildOfType(fc, SclVarOutputSection::class.java)
        if (outputSection != null) {
            val vars = PsiTreeUtil.findChildrenOfType(outputSection, SclVariableDeclaration::class.java)
            if (vars.isNotEmpty()) children.add(SclVarSectionElement(outputSection))
        }

        // Mostrar tipo de retorno como nó especial
        fc.returnType?.let { retType ->
            children.add(SclReturnTypeElement(fc, retType.text))
        }
        return children.toTypedArray()
    }

    private fun buildChildrenForType(typeBlock: SclTypeBlock): Array<TreeElement> {
        // TYPE: listar campos do STRUCT diretamente (sem agrupamento por seção)
        val structType = PsiTreeUtil.findChildOfType(typeBlock, SclStructType::class.java)
            ?: return TreeElement.EMPTY_ARRAY

        return PsiTreeUtil
            .findChildrenOfType(structType, SclVariableDeclaration::class.java)
            .map { SclVariableElement(it, "STRUCT") }
            .toTypedArray()
    }

    private fun buildChildrenForOB(ob: SclOrganizationBlock): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        val tempSection = PsiTreeUtil.findChildOfType(ob, SclVarTempSection::class.java)
        if (tempSection != null) children.add(SclVarSectionElement(tempSection))
        return children.toTypedArray()
    }
}
```

---

## 5. SclVarSectionElement — Nó Agrupador das Seções VAR

```kotlin
// src/main/kotlin/com/yourplugin/scl/structure/SclVarSectionElement.kt

class SclVarSectionElement(
    private val section: PsiElement   // SclVarInputSection, SclVarStaticSection, etc.
) : StructureViewTreeElement {

    override fun getValue(): Any = section

    override fun navigate(requestFocus: Boolean) {
        (section as? NavigatablePsiElement)?.navigate(requestFocus)
    }
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String = sectionLabel()
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = sectionIcon()
        }
    }

    // ── Filhos: as variáveis declaradas nesta seção ───────────────────────
    override fun getChildren(): Array<TreeElement> {
        val sectionLabel = sectionLabel()
        return PsiTreeUtil
            .findChildrenOfType(section, SclVariableDeclaration::class.java)
            .map { SclVariableElement(it, sectionLabel) }
            .toTypedArray()
    }

    // ── Label legível da seção ────────────────────────────────────────────
    private fun sectionLabel(): String = when (section) {
        is SclVarInputSection  -> "VAR_INPUT"
        is SclVarOutputSection -> "VAR_OUTPUT"
        is SclVarInOutSection  -> "VAR_IN_OUT"
        is SclVarStaticSection -> "VAR (STATIC)"
        is SclVarTempSection   -> "VAR_TEMP"
        is SclConstSection     -> "CONST"
        else                   -> "VAR"
    }

    // ── Ícone da seção (consistente com completion e hover doc) ───────────
    private fun sectionIcon(): Icon = when (section) {
        is SclVarInputSection  -> AllIcons.Nodes.Parameter    // verde
        is SclVarOutputSection -> AllIcons.Nodes.Property     // vermelho
        is SclVarInOutSection  -> AllIcons.Nodes.PropertyRead // laranja
        is SclVarStaticSection -> AllIcons.Nodes.Field        // roxo
        is SclVarTempSection   -> AllIcons.Nodes.Variable     // cinza
        is SclConstSection     -> AllIcons.Nodes.Constant     // ciano
        else                   -> AllIcons.Nodes.Field
    }
}
```

---

## 6. SclVariableElement — Nó Folha de Variável

```kotlin
// src/main/kotlin/com/yourplugin/scl/structure/SclVariableElement.kt

class SclVariableElement(
    private val declaration: SclVariableDeclaration,
    private val sectionLabel: String
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = declaration

    override fun navigate(requestFocus: Boolean) {
        declaration.navigate(requestFocus)
    }
    override fun canNavigate(): Boolean = declaration.canNavigate()
    override fun canNavigateToSource(): Boolean = declaration.canNavigateToSource()

    // Chave para ordenação alfabética (nome da variável)
    override fun getAlphaSortKey(): String = declaration.name ?: ""

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {

            // Texto principal: "i_bStart"
            override fun getPresentableText(): String = declaration.name ?: "?"

            // Texto secundário à direita: ": BOOL"  (em cinza)
            override fun getLocationString(): String =
                declaration.dataType?.text?.let { ": $it" } ?: ""

            // Ícone igual ao da seção pai (consistência visual)
            override fun getIcon(unused: Boolean): Icon = when (sectionLabel) {
                "VAR_INPUT"    -> AllIcons.Nodes.Parameter
                "VAR_OUTPUT"   -> AllIcons.Nodes.Property
                "VAR_IN_OUT"   -> AllIcons.Nodes.PropertyRead
                "VAR (STATIC)" -> AllIcons.Nodes.Field
                "VAR_TEMP"     -> AllIcons.Nodes.Variable
                "CONST"        -> AllIcons.Nodes.Constant
                "STRUCT"       -> AllIcons.Nodes.Field
                else           -> AllIcons.Nodes.Variable
            }
        }
    }

    // Variáveis são sempre folhas — sem filhos
    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY
}
```

---

## 7. SclReturnTypeElement — Nó Especial para Retorno de FC

```kotlin
// Dentro de SclStructureViewElement.kt ou arquivo próprio

class SclReturnTypeElement(
    private val function: SclFunction,
    private val returnTypeName: String
) : StructureViewTreeElement {

    override fun getValue(): Any = function
    override fun navigate(requestFocus: Boolean) = function.navigate(requestFocus)
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String = "↩ $returnTypeName"
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.Type
        }
    }

    override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY
}
```

---

## 8. Filtros da Toolbar do Structure View

```kotlin
// Filtro: mostrar/ocultar VAR_TEMP (útil para focar no que importa)
object SclShowTempVarsFilter : Filter {
    private const val ID = "SCL_SHOW_TEMP"

    override fun getName(): String = ID
    override fun toString(): String = ID

    // Texto do tooltip no botão da toolbar
    override fun getPresentation(): ActionPresentation =
        ActionPresentationData(
            "Show VAR_TEMP",
            "Show or hide temporary variables",
            AllIcons.Nodes.Variable
        )

    // Por padrão: ocultar VAR_TEMP (engenheiros raramente precisam ver)
    override fun isReverted(): Boolean = true

    override fun isVisible(treeNode: TreeElement): Boolean {
        if (treeNode !is SclVarSectionElement) return true
        return treeNode.getValue() !is SclVarTempSection
    }
}

// Filtro: mostrar/ocultar CONST
object SclShowConstFilter : Filter {
    private const val ID = "SCL_SHOW_CONST"

    override fun getName(): String = ID
    override fun toString(): String = ID

    override fun getPresentation(): ActionPresentation =
        ActionPresentationData(
            "Show CONST",
            "Show or hide constant declarations",
            AllIcons.Nodes.Constant
        )

    override fun isReverted(): Boolean = false  // mostrar por padrão

    override fun isVisible(treeNode: TreeElement): Boolean {
        if (treeNode !is SclVarSectionElement) return true
        return treeNode.getValue() !is SclConstSection
    }
}
```

---

## 9. Ícones Necessários — SclIcons

O Structure View usa ícones específicos para cada tipo de bloco SCL.
Se `SclIcons` já existe no projeto, adicionar as constantes abaixo.
Se não existe, criar a classe:

```kotlin
// src/main/kotlin/com/yourplugin/scl/SclIcons.kt

object SclIcons {
    // Carregar SVGs de src/main/resources/icons/
    val FILE           = IconLoader.getIcon("/icons/scl_file.svg",    SclIcons::class.java)
    val FUNCTION_BLOCK = IconLoader.getIcon("/icons/scl_fb.svg",      SclIcons::class.java)
    val FUNCTION       = IconLoader.getIcon("/icons/scl_fc.svg",      SclIcons::class.java)
    val ORG_BLOCK      = IconLoader.getIcon("/icons/scl_ob.svg",      SclIcons::class.java)
    val UDT            = IconLoader.getIcon("/icons/scl_udt.svg",     SclIcons::class.java)
}
```

### Se os ícones customizados não existirem ainda, usar fallback dos AllIcons:

```kotlin
object SclIcons {
    val FILE           = AllIcons.FileTypes.Custom
    val FUNCTION_BLOCK = AllIcons.Nodes.Class          // azul — FB
    val FUNCTION       = AllIcons.Nodes.Function       // laranja — FC
    val ORG_BLOCK      = AllIcons.Nodes.Plugin         // verde — OB
    val UDT            = AllIcons.Nodes.Record         // roxo — TYPE/UDT
}
```

---

## 10. plugin.xml — Registro Completo

```xml
<extensions defaultExtensionNs="com.intellij">

    <!-- Structure View (Alt+7) -->
    <lang.psiStructureViewFactory
        language="SCL"
        implementationClass="com.yourplugin.scl.structure.SclStructureViewFactory"/>

</extensions>
```

---

## 11. Checklist de Testes

- [ ] `Alt+7` abre o painel Structure com a árvore do arquivo atual
- [ ] Arquivo com 1 FB + 1 FC + 1 TYPE mostra os 3 nós raiz
- [ ] Expandir FB mostra: VAR_INPUT, VAR_OUTPUT, VAR (STATIC) — na ordem certa
- [ ] Expandir VAR_INPUT mostra as variáveis com ícone correto e ": TIPO" à direita
- [ ] Clicar numa variável na árvore → cursor salta para a declaração no editor
- [ ] Mover cursor no editor para uma variável → nó correspondente fica destacado na árvore
- [ ] FC mostra "↩ REAL" (ou o tipo de retorno correto)
- [ ] TYPE/UDT lista os campos do STRUCT diretamente (sem nó intermediário)
- [ ] Seções VAR vazias NÃO aparecem na árvore (ex: FB sem VAR_TEMP não mostra esse nó)
- [ ] Botão "Show VAR_TEMP" na toolbar funciona para ocultar/mostrar
- [ ] Ordenação alfabética (botão A→Z) reordena as variáveis dentro de cada seção
- [ ] OB (ORGANIZATION_BLOCK) aparece com ícone distinto dos FB e FC

---

## 12. Problemas Comuns a Evitar

### ❌ ERRO 1: getChildren() retornando EMPTY_ARRAY para o arquivo
```kotlin
// ERRADO — se o elemento raiz não retornar filhos, a árvore fica vazia
override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY

// CORRETO — o elemento arquivo deve iterar os blocos filhos
is PsiFile -> buildChildrenForFile(element)
```

### ❌ ERRO 2: getSuitableClasses() sem SclVariableDeclaration
```kotlin
// ERRADO — sync automático não funciona para variáveis
override fun getSuitableClasses() = arrayOf(SclFunctionBlock::class.java)

// CORRETO — incluir todos os tipos que devem ser auto-selecionados
override fun getSuitableClasses() = arrayOf(
    SclFunctionBlock::class.java,
    SclFunction::class.java,
    SclVariableDeclaration::class.java   // ← essencial
)
```

### ❌ ERRO 3: navigate() não implementado ou incorreto
```kotlin
// ERRADO — clicar no nó não move o cursor
override fun navigate(requestFocus: Boolean) { }

// CORRETO — delegar para o PSI element que implementa NavigatablePsiElement
override fun navigate(requestFocus: Boolean) {
    (element as? NavigatablePsiElement)?.navigate(requestFocus)
}
```

### ❌ ERRO 4: SclVarSectionElement mostrando seções vazias
```kotlin
// ERRADO — mostra "VAR_TEMP" mesmo sem nenhuma variável temp
children.add(SclVarSectionElement(tempSection))

// CORRETO — verificar se tem ao menos 1 variável antes de adicionar
val vars = PsiTreeUtil.findChildrenOfType(section, SclVariableDeclaration::class.java)
if (vars.isNotEmpty()) children.add(SclVarSectionElement(section))
```

---

## 13. Ordem dos Nós — Preservar Ordem do Arquivo

Os blocos de nível superior (FB, FC, OB, TYPE) devem aparecer
**na mesma ordem em que estão no arquivo**.

```kotlin
// CORRETO — iterar filhos na ordem PSI natural
PsiTreeUtil.getChildrenOfType(file, PsiElement::class.java)?.forEach { child ->
    when (child) {
        is SclFunctionBlock     -> children.add(...)
        // ...
    }
}

// ERRADO — usar findChildrenOfType agrupa por tipo, quebrando a ordem original
// PsiTreeUtil.findChildrenOfType(file, SclFunctionBlock::class.java)  ← NÃO FAZER
```

As **variáveis dentro de cada seção VAR** também preservam a ordem original
por padrão. O sorter alfabético é opcional e ativado pelo usuário.
