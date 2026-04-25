# ESPECIFICAÇÃO: Linter SCL — Fase 4B
**Documento para Claude Code CLI — implementar LocalInspectionTool**

---

## 0. Objetivo

Implementar um linter estático para SCL que detecta erros e
warnings **em tempo real** no editor, com sublinhados coloridos
e mensagens contextuais — igual ao comportamento do TIA Portal
mas com regras mais inteligentes e configuráveis por CPU/firmware.

---

## 1. Arquitetura — 4 Componentes

```
src/main/kotlin/com/scl/plugin/linter/
├── SclCpuSettings.kt          ← configuração CPU/FW por projeto
├── SclLinterBundle.kt         ← mensagens de erro (i18n)
├── inspections/
│   ├── SclUnsupportedTypeInspection.kt   ← tipos não suportados por CPU
│   ├── SclAbsoluteAddressInspection.kt   ← %M, %I, %Q absolutos
│   ├── SclBooleanIfInspection.kt         ← antipattern IF bool → quick fix
│   ├── SclTempVarInspection.kt           ← VAR_TEMP não inicializado
│   ├── SclForIndexTypeInspection.kt      ← FOR com índice REAL
│   └── SclCaseElseInspection.kt          ← CASE sem ELSE
```

---

## 2. SclCpuSettings — Configuração de CPU/Firmware

```kotlin
// src/main/kotlin/com/scl/plugin/linter/SclCpuSettings.kt

enum class CpuFamily { S7_1200, S7_1500 }

enum class FirmwareVersion {
    S7_1200_BEFORE_V4,      // < V4.0 — TEMP não inicializado, mais restritivo
    S7_1200_V4_0,           // V4.0+ — TEMP inicializado em blocos otimizados
    S7_1200_V4_1_PLUS,      // V4.1+ — VARIANT, SERIALIZE disponíveis
    S7_1500_ANY             // S7-1500 — superset completo
}

@State(
    name = "SclCpuSettings",
    storages = [Storage("sclCpuSettings.xml")]
)
class SclCpuSettings : PersistentStateComponent<SclCpuSettings.State> {

    data class State(
        var cpuFamily: CpuFamily = CpuFamily.S7_1200,
        var firmwareVersion: FirmwareVersion = FirmwareVersion.S7_1200_V4_0
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    companion object {
        fun getInstance(project: Project): SclCpuSettings =
            project.service<SclCpuSettings>()
    }

    val isS7_1200 get() = state.cpuFamily == CpuFamily.S7_1200
    val isS7_1500 get() = state.cpuFamily == CpuFamily.S7_1500
    val hasFW4_1  get() = state.firmwareVersion == FirmwareVersion.S7_1200_V4_1_PLUS
                       || state.firmwareVersion == FirmwareVersion.S7_1500_ANY
    val tempInitialized get() = state.firmwareVersion != FirmwareVersion.S7_1200_BEFORE_V4
}
```

**Registrar no plugin.xml:**
```xml
<projectService
    serviceImplementation="com.scl.plugin.linter.SclCpuSettings"/>
```

---

## 3. Página de Configuração — Settings → Tools → SCL CPU Target

```kotlin
// SclCpuConfigurable.kt
class SclCpuConfigurable(private val project: Project) : Configurable {

    override fun getDisplayName() = "SCL CPU Target"

    override fun createComponent(): JComponent {
        // Dropdown: CPU Family — S7-1200 / S7-1500
        // Dropdown: Firmware — S7-1200 < V4.0 / V4.0+ / V4.1+ / S7-1500
        // Label: "Affects linter rules for data types and instructions"
    }
}
```

**Registrar no plugin.xml:**
```xml
<projectConfigurable
    parentId="tools"
    instance="com.scl.plugin.linter.SclCpuConfigurable"
    displayName="SCL CPU Target"
    id="com.scl.plugin.linter.SclCpuConfigurable"/>
```

---

## 4. Inspeção 1 — Tipos Não Suportados por CPU

**ID:** `SclUnsupportedType`
**Severidade padrão:** ERROR (vermelho)

### Tipos proibidos no S7-1200 (qualquer FW):
```
LInt, ULInt         → inteiros 64-bit — S7-1500 apenas
LTIME               → tempo 64-bit — S7-1500 apenas
LTOD, LDT           → time of day / date time 64-bit — S7-1500 apenas
WCHAR               → Unicode char — S7-1500 apenas
WSTRING             → Unicode string — S7-1500 apenas
```

### Tipos proibidos no S7-1200 FW < V4.1:
```
VARIANT             → pointer com type check — requer FW ≥ 4.1
```

### Instruções proibidas no S7-1200:
```
MAX_LEN             → string S7-1500 apenas
JOIN, SPLIT         → string S7-1500 apenas
TON_LTIME, TOF_LTIME, TP_LTIME, TONR_LTIME  → timer LTIME
CTU_LINT, CTD_LINT, CTUD_LINT               → counter 64-bit
GATHER_BLK, SCATTER_BLK                     → bit S7-1500
SERIALIZE, DESERIALIZE                       → requer FW ≥ 4.1
MOVE_BLK_VARIANT                            → requer FW ≥ 4.1
RUNTIME                                     → S7-1500 apenas
GET_ERROR                                   → S7-1500 apenas
```

```kotlin
class SclUnsupportedTypeInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unsupported data type or instruction for target CPU"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel() = HighlightDisplayLevel.ERROR

    // Tipos S7-1500 apenas
    private val s7_1500OnlyTypes = setOf(
        "LINT", "ULINT", "LTIME", "LTOD", "LDT", "WCHAR", "WSTRING",
        "TON_LTIME", "TOF_LTIME", "TP_LTIME", "TONR_LTIME",
        "CTU_LINT", "CTD_LINT", "CTUD_LINT",
        "GATHER_BLK", "SCATTER_BLK", "MAX_LEN", "JOIN", "SPLIT",
        "RUNTIME", "GET_ERROR"
    )

    // Tipos que requerem S7-1200 FW ≥ 4.1
    private val requiresFW41 = setOf(
        "VARIANT", "SERIALIZE", "DESERIALIZE", "MOVE_BLK_VARIANT",
        "TypeOf", "TypeOfElements"
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): PsiElementVisitor {

        val settings = SclCpuSettings.getInstance(holder.project)
        if (settings.isS7_1500) return PsiElementVisitor.EMPTY_VISITOR

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val text = element.text.uppercase()

                // Verifica typeRef — ex: myVar : LINT
                if (element is SclTypeRefImpl) {
                    if (text in s7_1500OnlyTypes) {
                        holder.registerProblem(
                            element,
                            "Type '$text' is not supported on S7-1200. " +
                            "Use S7-1500 or change the data type.",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                    if (!settings.hasFW4_1 && text in requiresFW41) {
                        holder.registerProblem(
                            element,
                            "'$text' requires S7-1200 firmware ≥ V4.1. " +
                            "Current target: ${settings.state.firmwareVersion}",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                }
            }
        }
    }
}
```

---

## 5. Inspeção 2 — Endereçamento Absoluto

**ID:** `SclAbsoluteAddress`
**Severidade padrão:** WARNING (amarelo)

Detecta uso de %M, %I, %Q no código e sugere tag simbólico.

```kotlin
class SclAbsoluteAddressInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Absolute memory address used"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel() = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Detecta tokens ABS_ADDRESS: %I0.0, %Q1.5, %M100.0
                if (element.node?.elementType == SclTypes.ABS_ADDRESS) {
                    holder.registerProblem(
                        element,
                        "Absolute address '${element.text}' found. " +
                        "Use symbolic PLC tags for maintainability. " +
                        "Absolute addressing breaks with hardware changes.",
                        ProblemHighlightType.WARNING,
                        SclConvertToSymbolicFix(element)  // quick fix
                    )
                }
            }
        }
}

// Quick Fix: sugere criar tag simbólico
class SclConvertToSymbolicFix(element: PsiElement) :
    LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText() = "Convert to symbolic tag (add to PLC tag table)"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        // Abre dialog ou mostra balloon explicando que o usuário deve
        // criar a tag no TIA Portal e usar o nome simbólico
        HintManager.getInstance().showInformationHint(
            FileEditorManager.getInstance(project).selectedTextEditor!!,
            "Create a symbolic tag in the PLC tag table and replace '${startElement.text}' with the tag name."
        )
    }
}
```

---

## 6. Inspeção 3 — Antipattern IF Booleano (com Quick Fix automático)

**ID:** `SclBooleanIf`
**Severidade padrão:** WARNING (amarelo)
**Quick Fix:** substitui automaticamente pelo assignment direto

```
PADRÃO DETECTADO:
IF #condicao THEN
    #saida := TRUE;
ELSE
    #saida := FALSE;
END_IF;

QUICK FIX APLICA:
#saida := #condicao;
```

```kotlin
class SclBooleanIfInspection : LocalInspectionTool() {

    override fun getDisplayName() = "Unnecessary IF for boolean assignment"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel() = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclIfStatementImpl) return

                val condition = element.getExpression() ?: return
                val thenList  = element.getThenStatementList() ?: return
                val elseList  = element.getElseStatementList() ?: return

                // Verificar: THEN tem apenas 1 statement
                val thenStmts = thenList.statementList
                val elseStmts = elseList.statementList
                if (thenStmts.size != 1 || elseStmts.size != 1) return

                // Verificar: THEN é assign := TRUE
                val thenAssign = thenStmts[0] as? SclAssignStmtImpl ?: return
                val elseAssign = elseStmts[0] as? SclAssignStmtImpl ?: return

                val thenVal = thenAssign.expression?.text?.uppercase()
                val elseVal = elseAssign.expression?.text?.uppercase()

                // Padrão: THEN x := TRUE; ELSE x := FALSE
                val isBoolPattern =
                    (thenVal == "TRUE" && elseVal == "FALSE") ||
                    (thenVal == "FALSE" && elseVal == "TRUE")

                // Variável deve ser a mesma nos dois branches
                val isSameVar = thenAssign.getVariableName() ==
                                elseAssign.getVariableName()

                if (isBoolPattern && isSameVar) {
                    val varName = thenAssign.getVariableName()
                    val condText = condition.text
                    val inverted = thenVal == "FALSE"

                    holder.registerProblem(
                        element,
                        "Unnecessary IF for boolean assignment. " +
                        "Simplify to: $varName := ${if (inverted) "NOT ($condText)" else condText};",
                        ProblemHighlightType.WEAK_WARNING,
                        SclSimplifyBooleanIfFix(element, varName, condText, inverted)
                    )
                }
            }
        }
}

class SclSimplifyBooleanIfFix(
    element: PsiElement,
    private val varName: String,
    private val condText: String,
    private val inverted: Boolean
) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText() = "Simplify to: $varName := ${if (inverted) "NOT ($condText)" else condText};"

    override fun invoke(project: Project, file: PsiFile, start: PsiElement, end: PsiElement) {
        val replacement = if (inverted)
            "$varName := NOT ($condText);"
        else
            "$varName := $condText;"

        val newElement = SclPsiElementFactory.createStatement(project, replacement)
        start.replace(newElement)
    }
}
```

---

## 7. Inspeção 4 — VAR_TEMP Não Inicializado

**ID:** `SclTempVarUninitialized`
**Severidade padrão:** WARNING para FW ≥ 4.0, ERROR para FW < 4.0

Detecta variáveis VAR_TEMP que são **lidas antes de escritas**.

```kotlin
class SclTempVarInspection : LocalInspectionTool() {

    override fun getDisplayName() = "VAR_TEMP variable read before assignment"
    override fun getGroupDisplayName() = "SCL"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val settings = SclCpuSettings.getInstance(holder.project)

                // Nível de severidade depende do firmware
                val level = if (settings.tempInitialized)
                    ProblemHighlightType.WEAK_WARNING
                else
                    ProblemHighlightType.GENERIC_ERROR

                val message = if (settings.tempInitialized)
                    "VAR_TEMP '${element.text}' may not be initialized. " +
                    "Initialize before use for clarity."
                else
                    "VAR_TEMP '${element.text}' is read before assignment. " +
                    "On S7-1200 FW < V4.0, TEMP variables contain undefined values (L-stack garbage)."

                // Lógica: verificar se variável TEMP é usada antes de
                // receber uma atribuição no fluxo do código
                if (element is SclIdentifierUsage && isTempVar(element) && isReadBeforeWrite(element)) {
                    holder.registerProblem(element, message, level)
                }
            }
        }

    private fun isTempVar(element: PsiElement): Boolean {
        // Busca declaração da variável — verifica se está em VAR_TEMP section
        val decl = SclSymbolResolver.resolveDeclaration(element) ?: return false
        val section = PsiTreeUtil.getParentOfType(decl, SclVarSectionImpl::class.java)
        return section?.firstChild?.node?.elementType == SclTypes.VAR_TEMP
    }

    private fun isReadBeforeWrite(element: PsiElement): Boolean {
        // Análise simples de fluxo: verifica se há atribuição antes do uso
        // no mesmo bloco — análise conservadora (false positive é aceitável)
        val block = PsiTreeUtil.getParentOfType(element, SclFunctionBlockDeclImpl::class.java)
            ?: return false
        val varName = element.text
        val elementOffset = element.textOffset

        // Busca por assignments à mesma variável antes desse offset
        val assignments = PsiTreeUtil.findChildrenOfType(block, SclAssignStmtImpl::class.java)
        val hasEarlierAssignment = assignments.any { assign ->
            assign.getVariableName() == varName &&
            assign.textOffset < elementOffset
        }

        return !hasEarlierAssignment
    }
}
```

---

## 8. Inspeção 5 — FOR com Índice REAL

**ID:** `SclForRealIndex`
**Severidade padrão:** ERROR

```kotlin
class SclForIndexTypeInspection : LocalInspectionTool() {

    override fun getDisplayName() = "FOR loop index must be integer type"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel() = HighlightDisplayLevel.ERROR

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclForStatementImpl) return

                val indexVar = element.getForVariable() ?: return
                val varType = SclSymbolResolver.resolveVarType(element, indexVar.text)
                    ?: return

                if (varType.uppercase() in setOf("REAL", "LREAL")) {
                    holder.registerProblem(
                        indexVar,
                        "FOR loop index '${indexVar.text}' is type '$varType'. " +
                        "SCL requires integer type (INT, DINT, etc.) for FOR index. " +
                        "Floating-point indices cause compile errors in TIA Portal.",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
}
```

---

## 9. Inspeção 6 — CASE sem ELSE

**ID:** `SclCaseMissingElse`
**Severidade padrão:** WARNING

```kotlin
class SclCaseElseInspection : LocalInspectionTool() {

    override fun getDisplayName() = "CASE statement missing ELSE branch"
    override fun getGroupDisplayName() = "SCL"
    override fun getDefaultLevel() = HighlightDisplayLevel.WARNING

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is SclCaseStatementImpl) return

                val hasElse = element.getCaseElseClause() != null

                if (!hasElse) {
                    // Destacar apenas a keyword CASE
                    val caseKeyword = element.firstChild
                    holder.registerProblem(
                        caseKeyword,
                        "CASE statement has no ELSE branch. " +
                        "Per Siemens Programming Guideline, always add ELSE to handle " +
                        "unexpected states (e.g., set error flag or status code).",
                        ProblemHighlightType.WEAK_WARNING,
                        SclAddCaseElseFix(element)
                    )
                }
            }
        }
}

class SclAddCaseElseFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName() = "SCL"
    override fun getText() = "Add ELSE branch to CASE"

    override fun invoke(project: Project, file: PsiFile, start: PsiElement, end: PsiElement) {
        // Insere antes do END_CASE:
        // ELSE
        //     // TODO: handle unexpected state
        //     #status := 16#8001;
        val caseStmt = start as SclCaseStatementImpl
        val endCase = PsiTreeUtil.findChildOfType(caseStmt, /* END_CASE token */)
        val elseClause = SclPsiElementFactory.createCaseElse(project,
            "\nELSE\n    // TODO: handle unexpected state\n    ;\n")
        caseStmt.addBefore(elseClause, endCase)
    }
}
```

---

## 10. Registrar Inspeções no plugin.xml

```xml
<extensions defaultExtensionNs="com.intellij">

    <!-- CPU Settings -->
    <projectService
        serviceImplementation="com.scl.plugin.linter.SclCpuSettings"/>

    <!-- Settings page -->
    <projectConfigurable
        parentId="tools"
        instance="com.scl.plugin.linter.SclCpuConfigurable"
        displayName="SCL CPU Target"
        id="com.scl.plugin.linter.SclCpuConfigurable"/>

    <!-- Inspections -->
    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="Unsupported data type for target CPU"
        enabledByDefault="true"
        level="ERROR"
        implementationClass="com.scl.plugin.linter.inspections.SclUnsupportedTypeInspection"/>

    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="Absolute memory address used"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="com.scl.plugin.linter.inspections.SclAbsoluteAddressInspection"/>

    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="Unnecessary IF for boolean assignment"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="com.scl.plugin.linter.inspections.SclBooleanIfInspection"/>

    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="VAR_TEMP read before assignment"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="com.scl.plugin.linter.inspections.SclTempVarInspection"/>

    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="FOR loop index must be integer type"
        enabledByDefault="true"
        level="ERROR"
        implementationClass="com.scl.plugin.linter.inspections.SclForIndexTypeInspection"/>

    <localInspection
        language="SCL"
        groupName="SCL"
        displayName="CASE statement missing ELSE branch"
        enabledByDefault="true"
        level="WARNING"
        implementationClass="com.scl.plugin.linter.inspections.SclCaseElseInspection"/>

</extensions>
```

---

## 11. Tabela de Regras — Referência Rápida

| ID | Descrição | S7-1200 | S7-1500 | Severidade | Quick Fix |
|---|---|---|---|---|---|
| `SclUnsupportedType` | LInt/LTIME/WSTRING usados | ❌ ERRO | ✅ OK | ERROR | Não |
| `SclUnsupportedType` | VARIANT sem FW 4.1 | ❌ ERRO | ✅ OK | ERROR | Não |
| `SclAbsoluteAddress` | %M, %I, %Q no código | ⚠️ WARN | ⚠️ WARN | WARNING | Sim |
| `SclBooleanIf` | IF x THEN y:=TRUE | ⚠️ WARN | ⚠️ WARN | WARNING | ✅ Sim |
| `SclTempVarUninitialized` | TEMP lido antes de escrito (FW < 4.0) | ❌ ERRO | n/a | ERROR | Não |
| `SclTempVarUninitialized` | TEMP lido antes de escrito (FW ≥ 4.0) | ⚠️ WARN | ⚠️ WARN | WARNING | Não |
| `SclForRealIndex` | FOR com índice REAL/LREAL | ❌ ERRO | ❌ ERRO | ERROR | Não |
| `SclCaseMissingElse` | CASE sem ELSE | ⚠️ WARN | ⚠️ WARN | WARNING | ✅ Sim |

---

## 12. Checklist de Testes

- [ ] Settings → Tools → SCL CPU Target aparece na IDE
- [ ] Selecionar S7-1200 → `LINT` sublinhado vermelho com mensagem
- [ ] Selecionar S7-1500 → `LINT` sem sublinhado
- [ ] `%M100.0` sublinhado amarelo + quick fix aparece no Alt+Enter
- [ ] Antipattern IF → quick fix colapsa para assignment direto
- [ ] Código com VAR_TEMP lido antes de escrito → warning/error
- [ ] FOR com índice REAL → sublinhado vermelho
- [ ] CASE sem ELSE → sublinhado amarelo + quick fix adiciona ELSE
- [ ] Alt+Enter em qualquer marcação abre menu de quick fixes
- [ ] Inspection profile em Settings → Editor → Inspections → SCL mostra todas as 6

---

## 13. Problemas Comuns a Evitar

### ❌ ERRO 1: buildVisitor visitando todos os elementos
```kotlin
// ERRADO — muito lento, visita cada PsiWhiteSpace
override fun buildVisitor(...) = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) { ... }
}

// CORRETO — usar PsiRecursiveElementVisitor com early return
override fun buildVisitor(...) = object : PsiRecursiveElementVisitor() {
    override fun visitElement(element: PsiElement) {
        if (element is SclTypeRefImpl) { ... }
        super.visitElement(element)  // continua recursão
    }
}
```

### ❌ ERRO 2: Registrar problema no elemento errado
```kotlin
// ERRADO — sublinha o bloco inteiro (muito grande)
holder.registerProblem(functionBlock, message, ...)

// CORRETO — sublinha apenas o token relevante
holder.registerProblem(typeRefElement, message, ...)
```

### ❌ ERRO 3: Quick fix sem refresh da árvore PSI
```kotlin
// Após modificar o texto, sempre chamar:
PsiDocumentManager.getInstance(project).commitDocument(document)
```
