# ESPECIFICAÇÃO: MCP Server SCL — Fase 7
**Documento para Claude Code CLI**

---

## 0. Contexto e Arquitetura

O IntelliJ IDEA 2026.1 já tem MCP Server embutido.
O plugin SCL estende esse servidor com ferramentas SCL específicas.
Claude Code se conecta ao IntelliJ via MCP e usa essas ferramentas.

```
Claude Code (terminal)
      ↕ MCP protocol (stdio/SSE)
IntelliJ MCP Server (embutido, porta dinâmica)
      ↕ extension point com.intellij.mcpServer
SCL MCP Tools (este plugin)
      ├── scl_list_blocks      → lista todos os FBs/FCs/OBs do projeto
      ├── scl_get_interface    → retorna interface completa de um bloco
      ├── scl_generate_fb      → gera código SCL de um FB completo
      ├── scl_validate_file    → valida arquivo SCL via linter do plugin
      ├── scl_read_io_list     → lê arquivo CSV/Excel de lista de I/O
      └── scl_project_summary  → resumo completo do projeto SCL
```

---

## 1. Dependência no build.gradle.kts

```kotlin
// Adicionar dependência ao MCP Server embutido do IntelliJ
intellijPlatform {
    // ... configurações existentes ...
    
    // Dependência do MCP Server (embutido desde 2025.2)
    bundledPlugin("com.intellij.mcpServer")
}
```

---

## 2. Registrar no plugin.xml

```xml
<!-- Dependência do MCP Server -->
<depends>com.intellij.mcpServer</depends>

<extensions defaultExtensionNs="com.intellij.mcpServer">
    <mcpTool implementation="com.scl.plugin.mcp.SclListBlocksTool"/>
    <mcpTool implementation="com.scl.plugin.mcp.SclGetInterfaceTool"/>
    <mcpTool implementation="com.scl.plugin.mcp.SclGenerateFbTool"/>
    <mcpTool implementation="com.scl.plugin.mcp.SclValidateFileTool"/>
    <mcpTool implementation="com.scl.plugin.mcp.SclReadIoListTool"/>
    <mcpTool implementation="com.scl.plugin.mcp.SclProjectSummaryTool"/>
</extensions>
```

---

## 3. Estrutura de Arquivos

```
src/main/kotlin/com/scl/plugin/mcp/
├── SclMcpBase.kt           ← classe base com utilitários comuns
├── SclListBlocksTool.kt    ← lista blocos do projeto
├── SclGetInterfaceTool.kt  ← retorna interface de um bloco
├── SclGenerateFbTool.kt    ← gera código SCL completo
├── SclValidateFileTool.kt  ← valida arquivo SCL
├── SclReadIoListTool.kt    ← lê lista de I/O
└── SclProjectSummaryTool.kt ← resumo do projeto
```

---

## 4. Classe Base

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclMcpBase.kt

import com.intellij.mcpServer.McpTool
import com.intellij.mcpServer.McpToolCall
import com.intellij.mcpServer.Response
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.openapi.vfs.VirtualFile

abstract class SclMcpBase : McpTool {

    // Busca todos os arquivos .scl no projeto
    protected fun findSclFiles(project: Project): List<VirtualFile> {
        val scope = com.intellij.openapi.vfs.VfsUtil
            .collectChildrenRecursively(project.baseDir)
            .filter { it.extension == "scl" }
        return scope
    }

    // Encontra um bloco específico por nome
    protected fun findBlock(
        project: Project,
        name: String
    ): SclFunctionBlockDeclImpl? {
        return findSclFiles(project)
            .mapNotNull { vFile ->
                PsiManager.getInstance(project).findFile(vFile)
            }
            .flatMap { psiFile ->
                com.intellij.psi.util.PsiTreeUtil
                    .findChildrenOfType(psiFile, SclFunctionBlockDeclImpl::class.java)
                    .asSequence()
            }
            .firstOrNull { fb ->
                fb.name?.equals(name.trim('"'), ignoreCase = true) == true
            }
    }

    // Extrai a interface completa de um FB como texto estruturado
    protected fun extractInterface(fb: SclFunctionBlockDeclImpl): String {
        val sb = StringBuilder()
        sb.appendLine("FUNCTION_BLOCK \"${fb.name}\"")

        com.intellij.psi.util.PsiTreeUtil
            .findChildrenOfType(fb, SclVarSectionImpl::class.java)
            .forEach { section ->
                val sectionType = section.firstChild?.node?.elementType
                val sectionName = when (sectionType) {
                    SclTypes.VAR_INPUT  -> "VAR_INPUT"
                    SclTypes.VAR_OUTPUT -> "VAR_OUTPUT"
                    SclTypes.VAR_IN_OUT -> "VAR_IN_OUT"
                    SclTypes.VAR        -> "VAR"
                    SclTypes.VAR_TEMP   -> "VAR_TEMP"
                    else -> "VAR"
                }
                sb.appendLine("  $sectionName")
                section.varDeclList.forEach { decl ->
                    val varName = decl.name ?: return@forEach
                    val varType = decl.typeRef?.text ?: "?"
                    sb.appendLine("    $varName : $varType;")
                }
                sb.appendLine("  END_VAR")
            }
        return sb.toString()
    }
}
```

---

## 5. Tool 1 — scl_list_blocks

**Propósito:** Claude Code descobre o que existe no projeto antes de gerar código.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclListBlocksTool.kt

class SclListBlocksTool : SclMcpBase() {

    // Nome da ferramenta — como Claude Code vai chamá-la
    override val name = "scl_list_blocks"

    override val description = """
        Lists all SCL blocks (Function Blocks, Functions, Organization Blocks)
        in the current IntelliJ project. Returns block names, types, and file paths.
        Use this before generating new code to understand the existing project structure.
    """.trimIndent()

    // Sem parâmetros necessários
    data class Params(val dummy: String? = null)

    override fun execute(call: McpToolCall, project: Project): Response {
        val result = StringBuilder()
        result.appendLine("# SCL Blocks in Project\n")

        val files = findSclFiles(project)
        if (files.isEmpty()) {
            return Response("No .scl files found in project.")
        }

        var fbCount = 0
        var fcCount = 0
        var obCount = 0

        files.forEach { vFile ->
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEach
            val relativePath = vFile.path.removePrefix(project.basePath ?: "")

            // Function Blocks
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(psiFile, SclFunctionBlockDeclImpl::class.java)
                .forEach { fb ->
                    result.appendLine("FB | ${fb.name} | $relativePath")
                    fbCount++
                }

            // Functions
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(psiFile, SclFunctionDeclImpl::class.java)
                .forEach { fc ->
                    result.appendLine("FC | ${fc.name} | $relativePath")
                    fcCount++
                }

            // Organization Blocks
            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(psiFile, SclOrgBlockDeclImpl::class.java)
                .forEach { ob ->
                    result.appendLine("OB | ${ob.name} | $relativePath")
                    obCount++
                }
        }

        result.appendLine("\nSummary: $fbCount FBs, $fcCount FCs, $obCount OBs")
        return Response(result.toString())
    }
}
```

---

## 6. Tool 2 — scl_get_interface

**Propósito:** Claude Code lê a interface de um FB antes de gerar chamadas ou extensões.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclGetInterfaceTool.kt

class SclGetInterfaceTool : SclMcpBase() {

    override val name = "scl_get_interface"

    override val description = """
        Returns the complete interface (VAR_INPUT, VAR_OUTPUT, VAR_IN_OUT, VAR)
        of a specific SCL Function Block or Function.
        Use this to understand parameter names and types before writing FB calls.
        Parameter 'blockName': name of the block (e.g. "FB_TankControl" or FB_TankControl).
    """.trimIndent()

    data class Params(val blockName: String)

    override fun execute(call: McpToolCall, project: Project): Response {
        val params = call.parseParams<Params>()
        val name = params.blockName.trim('"')

        val fb = findBlock(project, name)
            ?: return Response(
                "Block '$name' not found in project. " +
                "Use scl_list_blocks to see available blocks."
            )

        return Response(extractInterface(fb))
    }
}
```

---

## 7. Tool 3 — scl_generate_fb

**Propósito:** Claude Code gera um FB SCL completo e cria o arquivo no projeto.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclGenerateFbTool.kt

class SclGenerateFbTool : SclMcpBase() {

    override val name = "scl_generate_fb"

    override val description = """
        Generates a complete SCL Function Block and creates the .scl file in the project.
        Parameters:
          - blockName: Name of the FB (e.g. "FB_MotorControl")
          - description: What the FB does (e.g. "Controls a 3-phase motor with star-delta start")
          - inputs: List of inputs as "name:type" (e.g. "i_Start:BOOL,i_Stop:BOOL,i_Speed:REAL")
          - outputs: List of outputs as "name:type" (e.g. "o_Running:BOOL,o_Fault:BOOL")
          - cpuTarget: "S7-1200" or "S7-1500" (default: S7-1200)
          - targetFile: File path relative to project root (e.g. "FBs/FB_MotorControl.scl")
    """.trimIndent()

    data class Params(
        val blockName: String,
        val description: String,
        val inputs: String = "",
        val outputs: String = "",
        val cpuTarget: String = "S7-1200",
        val targetFile: String = ""
    )

    override fun execute(call: McpToolCall, project: Project): Response {
        val p = call.parseParams<Params>()

        // Parse parâmetros de entrada
        val inputParams = parseParamList(p.inputs)
        val outputParams = parseParamList(p.outputs)

        // Gerar código SCL
        val code = buildString {
            appendLine("FUNCTION_BLOCK \"${p.blockName}\"")
            appendLine("{ S7_Optimized_Access := 'TRUE' }")
            appendLine("//=".repeat(30))
            appendLine("// ${p.blockName} — ${p.description}")
            appendLine("// CPU Target: ${p.cpuTarget}")
            appendLine("// Generated: ${java.time.LocalDate.now()}")
            appendLine("//=".repeat(30))
            appendLine()

            // VAR_INPUT
            if (inputParams.isNotEmpty()) {
                appendLine("VAR_INPUT")
                inputParams.forEach { (name, type) ->
                    appendLine("    $name : $type;")
                }
                appendLine("END_VAR")
                appendLine()
            }

            // VAR_OUTPUT
            if (outputParams.isNotEmpty()) {
                appendLine("VAR_OUTPUT")
                outputParams.forEach { (name, type) ->
                    appendLine("    $name : $type;")
                }
                appendLine("END_VAR")
                appendLine()
            }

            // VAR (estáticas)
            appendLine("VAR")
            appendLine("    s_State : INT;   // FSM state")
            appendLine("END_VAR")
            appendLine()

            // BEGIN
            appendLine("BEGIN")
            appendLine("    // TODO: implement logic")
            appendLine("    REGION State Machine")
            appendLine("        CASE s_State OF")
            appendLine("            0: // IDLE")
            appendLine("                ;")
            appendLine("            ELSE")
            appendLine("                s_State := 0; // reset unknown state")
            appendLine("        END_CASE;")
            appendLine("    END_REGION")
            appendLine()
            appendLine("END_FUNCTION_BLOCK")
        }

        // Determinar caminho do arquivo
        val filePath = if (p.targetFile.isNotBlank()) {
            "${project.basePath}/${p.targetFile}"
        } else {
            "${project.basePath}/FBs/${p.blockName}.scl"
        }

        // Criar o arquivo
        try {
            val file = java.io.File(filePath)
            file.parentFile.mkdirs()
            file.writeText(code)

            // Refresh no VFS do IntelliJ
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .invokeLater {
                    com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(filePath)
                }

            return Response(
                "✅ Generated: $filePath\n\n" +
                "Block: ${p.blockName}\n" +
                "Inputs: ${inputParams.size}, Outputs: ${outputParams.size}\n\n" +
                "File content:\n$code"
            )
        } catch (e: Exception) {
            return Response("❌ Error creating file: ${e.message}")
        }
    }

    private fun parseParamList(params: String): List<Pair<String, String>> {
        if (params.isBlank()) return emptyList()
        return params.split(",").mapNotNull { param ->
            val parts = param.trim().split(":")
            if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim())
            else null
        }
    }
}
```

---

## 8. Tool 4 — scl_validate_file

**Propósito:** Claude Code verifica erros antes de fazer download para o PLC.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclValidateFileTool.kt

class SclValidateFileTool : SclMcpBase() {

    override val name = "scl_validate_file"

    override val description = """
        Validates an SCL file using the plugin's built-in linter.
        Returns a list of errors and warnings with line numbers.
        Parameter 'filePath': path relative to project root (e.g. "FBs/FB_TankControl.scl")
        Use this before generating PLC download to catch issues early.
    """.trimIndent()

    data class Params(val filePath: String)

    override fun execute(call: McpToolCall, project: Project): Response {
        val params = call.parseParams<Params>()
        val fullPath = "${project.basePath}/${params.filePath}"

        val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(fullPath)
            ?: return Response("❌ File not found: ${params.filePath}")

        val psiFile = PsiManager.getInstance(project).findFile(vFile)
            ?: return Response("❌ Cannot parse file: ${params.filePath}")

        // Coletar problemas via InspectionManager
        val problems = mutableListOf<String>()

        com.intellij.codeInspection.InspectionManager.getInstance(project)
            .let { manager ->
                listOf(
                    SclUnsupportedTypeInspection(),
                    SclAbsoluteAddressInspection(),
                    SclBooleanIfInspection(),
                    SclTempVarInspection(),
                    SclForIndexTypeInspection(),
                    SclCaseElseInspection()
                ).forEach { inspection ->
                    val holder = com.intellij.codeInspection.ProblemsHolder(
                        manager, psiFile, false
                    )
                    psiFile.accept(inspection.buildVisitor(holder, false))
                    holder.results.forEach { problem ->
                        val line = com.intellij.openapi.editor.Document::class.java
                            .let {
                                com.intellij.openapi.fileEditor.FileDocumentManager
                                    .getInstance()
                                    .getDocument(vFile)
                                    ?.getLineNumber(problem.psiElement.textOffset)
                                    ?.plus(1) ?: 0
                            }
                        val severity = when (problem.highlightType) {
                            com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR -> "ERROR"
                            else -> "WARNING"
                        }
                        problems.add("Line $line [$severity]: ${problem.descriptionTemplate}")
                    }
                }
            }

        return if (problems.isEmpty()) {
            Response("✅ No issues found in ${params.filePath}")
        } else {
            Response(
                "Found ${problems.size} issue(s) in ${params.filePath}:\n\n" +
                problems.joinToString("\n")
            )
        }
    }
}
```

---

## 9. Tool 5 — scl_read_io_list

**Propósito:** Claude Code lê a lista de I/O e gera mapeamento de variáveis automaticamente.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclReadIoListTool.kt

class SclReadIoListTool : SclMcpBase() {

    override val name = "scl_read_io_list"

    override val description = """
        Reads an I/O list file (CSV or TXT) from the project and returns
        a structured list of PLC inputs and outputs.
        Supports EPLAN and standard CSV exports.
        Parameter 'filePath': path to CSV file relative to project root
        Expected CSV format: Address,Name,Type,Description
        Example: %I0.0,Emergency_Stop,BOOL,Emergency stop button NF
        Use this to generate VAR_INPUT/VAR_OUTPUT declarations automatically.
    """.trimIndent()

    data class Params(val filePath: String)

    override fun execute(call: McpToolCall, project: Project): Response {
        val params = call.parseParams<Params>()
        val fullPath = "${project.basePath}/${params.filePath}"

        val file = java.io.File(fullPath)
        if (!file.exists()) {
            return Response(
                "❌ File not found: ${params.filePath}\n" +
                "Expected format: Address,Name,Type,Description\n" +
                "Example: %I0.0,Emergency_Stop,BOOL,NF contact"
            )
        }

        val lines = file.readLines()
        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        val memoryTags = mutableListOf<String>()
        var parseErrors = 0

        lines.drop(1).forEach { line -> // skip header
            val parts = line.split(",").map { it.trim() }
            if (parts.size < 3) { parseErrors++; return@forEach }

            val address = parts[0]
            val name = parts[1]
            val type = parts[2]
            val description = parts.getOrElse(3) { "" }

            val entry = "    $name : $type; // $address — $description"

            when {
                address.startsWith("%I") -> inputs.add(entry)
                address.startsWith("%Q") -> outputs.add(entry)
                address.startsWith("%M") -> memoryTags.add(entry)
            }
        }

        val result = buildString {
            appendLine("# I/O List: ${params.filePath}")
            appendLine("Inputs: ${inputs.size}, Outputs: ${outputs.size}, Memory: ${memoryTags.size}")
            appendLine()

            if (inputs.isNotEmpty()) {
                appendLine("## VAR_INPUT (suggested)")
                appendLine("```scl")
                appendLine("VAR_INPUT")
                inputs.forEach { appendLine(it) }
                appendLine("END_VAR")
                appendLine("```")
                appendLine()
            }

            if (outputs.isNotEmpty()) {
                appendLine("## VAR_OUTPUT (suggested)")
                appendLine("```scl")
                appendLine("VAR_OUTPUT")
                outputs.forEach { appendLine(it) }
                appendLine("END_VAR")
                appendLine("```")
            }

            if (parseErrors > 0) {
                appendLine("\n⚠️ $parseErrors lines could not be parsed (check CSV format)")
            }
        }

        return Response(result)
    }
}
```

---

## 10. Tool 6 — scl_project_summary

**Propósito:** Visão geral completa do projeto para Claude Code entender o contexto.

```kotlin
// src/main/kotlin/com/scl/plugin/mcp/SclProjectSummaryTool.kt

class SclProjectSummaryTool : SclMcpBase() {

    override val name = "scl_project_summary"

    override val description = """
        Returns a complete summary of the SCL project:
        - All blocks with their interfaces
        - CPU target configuration
        - I/O files found
        - Potential linter issues count
        Use this as the first tool call to understand the full project context
        before generating or modifying SCL code.
    """.trimIndent()

    data class Params(val dummy: String? = null)

    override fun execute(call: McpToolCall, project: Project): Response {
        val result = StringBuilder()
        result.appendLine("# SCL Project Summary")
        result.appendLine("Project: ${project.name}")
        result.appendLine("Path: ${project.basePath}")
        result.appendLine()

        // CPU Target
        val settings = SclCpuSettings.getInstance(project)
        result.appendLine("## CPU Target")
        result.appendLine("- Family: ${settings.state.cpuFamily}")
        result.appendLine("- Firmware: ${settings.state.firmwareVersion}")
        result.appendLine()

        // Blocos
        result.appendLine("## Blocks")
        findSclFiles(project).forEach { vFile ->
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEach
            val relativePath = vFile.path.removePrefix(project.basePath ?: "")

            com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(psiFile, SclFunctionBlockDeclImpl::class.java)
                .forEach { fb ->
                    result.appendLine("### FB: ${fb.name} ($relativePath)")
                    result.appendLine(extractInterface(fb))
                }
        }

        // Arquivos CSV de I/O
        result.appendLine("## I/O List Files Found")
        project.basePath?.let { basePath ->
            java.io.File(basePath).walkTopDown()
                .filter { it.extension in listOf("csv", "xlsx", "txt") }
                .forEach { result.appendLine("- ${it.path.removePrefix(basePath)}") }
        }

        return Response(result.toString())
    }
}
```

---

## 11. Exemplos de Uso no Claude Code

Após configurar Settings → Tools → MCP Server → Configure for Claude Code:

```bash
# Listar blocos do projeto
claude "Use scl_list_blocks to show me all blocks in the project"

# Ver interface de um FB
claude "Use scl_get_interface to get the interface of FB_TankControl"

# Gerar novo FB
claude "Use scl_generate_fb to create a motor control FB with:
  - blockName: FB_MotorControl
  - description: Controls a 3-phase motor with star-delta start
  - inputs: i_Start:BOOL,i_Stop:BOOL,i_EStop:BOOL,i_Speed:REAL
  - outputs: o_Running:BOOL,o_Fault:BOOL,o_StarActive:BOOL,o_DeltaActive:BOOL
  - cpuTarget: S7-1200"

# Validar arquivo antes do download
claude "Use scl_validate_file to check FBs/FB_TankControl.scl for errors"

# Gerar mapeamento a partir da lista de I/O
claude "Use scl_read_io_list to read io_list/filling_tank.csv 
  and generate the VAR_INPUT and VAR_OUTPUT sections"

# Resumo completo do projeto
claude "Use scl_project_summary to understand the full project context,
  then generate a new OB_Main that calls all existing FBs"
```

---

## 12. Checklist de Configuração

```
[ ] Adicionar bundledPlugin("com.intellij.mcpServer") no build.gradle.kts
[ ] Adicionar <depends>com.intellij.mcpServer</depends> no plugin.xml
[ ] Registrar 6 mcpTool no plugin.xml
[ ] ./gradlew buildPlugin
[ ] Abrir IntelliJ → Settings → Tools → MCP Server → Enable
[ ] Clicar "Configure for Claude Code"
[ ] Reiniciar Claude Code
[ ] Testar: claude "Use scl_list_blocks"
```

## 13. Checklist de Testes

```
[ ] scl_list_blocks retorna lista de FBs do projeto
[ ] scl_get_interface retorna VAR_INPUT/OUTPUT de FB_TankControl
[ ] scl_generate_fb cria arquivo .scl na pasta FBs/
[ ] Arquivo gerado abre no IntelliJ sem erros de parser
[ ] scl_validate_file detecta erros no arquivo de teste
[ ] scl_read_io_list lê CSV e gera declarações VAR
[ ] scl_project_summary retorna visão completa do projeto
[ ] Claude Code consegue usar TODOS os tools encadeados:
    summary → get_interface → generate_fb → validate_file
```

---

## 14. Problemas Comuns

### ❌ ERRO 1 — bundledPlugin não encontrado
```kotlin
// ERRADO: plugin como dependência externa
plugins { id("com.intellij.mcpServer") version "x.x" }

// CORRETO: é bundled — não tem versão separada
intellijPlatform {
    bundledPlugin("com.intellij.mcpServer")
}
```

### ❌ ERRO 2 — Tool não aparece no Claude Code
```
CAUSA: plugin.xml não tem o <depends>com.intellij.mcpServer</depends>
FIX: adicionar ANTES das extensions
```

### ❌ ERRO 3 — project.basePath é null
```kotlin
// SEMPRE usar ?: para basePath
val basePath = project.basePath ?: return Response("❌ Project has no base path")
```

### ❌ ERRO 4 — Arquivo criado mas não aparece no Project View
```kotlin
// Sempre fazer refresh no VFS após criar arquivo:
ApplicationManager.getApplication().invokeLater {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
}
```
