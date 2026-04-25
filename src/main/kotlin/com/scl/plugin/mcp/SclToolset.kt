package com.scl.plugin.mcp

import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.linter.SclCpuSettings
import com.scl.plugin.linter.inspections.*
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import java.time.LocalDate

class SclToolset : SclMcpBase(), McpToolset {

    // ── Tool 1: scl_list_blocks ────────────────────────────────────────────────

    @McpTool(name = "scl_list_blocks")
    @McpDescription(description = "Lists all SCL blocks (FB/FC/OB) in the current project with file paths. Use before generating code to understand the project structure.")
    suspend fun scl_list_blocks(): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val result = StringBuilder("# SCL Blocks in Project\n\n")
        val files = findSclFiles(project)
        if (files.isEmpty()) return "No .scl files found in project."
        val basePath = project.basePath ?: ""
        var fbCount = 0; var fcCount = 0; var obCount = 0
        files.forEach { vFile ->
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEach
            val rel = vFile.path.removePrefix(basePath)
            PsiTreeUtil.findChildrenOfType(psiFile, SclFunctionBlockDecl::class.java).forEach {
                result.appendLine("FB | ${it.name} | $rel"); fbCount++
            }
            PsiTreeUtil.findChildrenOfType(psiFile, SclFunctionDecl::class.java).forEach {
                result.appendLine("FC | ${it.name} | $rel"); fcCount++
            }
            PsiTreeUtil.findChildrenOfType(psiFile, SclOrgBlockDecl::class.java).forEach {
                result.appendLine("OB | ${it.name} | $rel"); obCount++
            }
        }
        result.appendLine("\nSummary: $fbCount FBs, $fcCount FCs, $obCount OBs")
        return result.toString()
    }

    // ── Tool 2: scl_get_interface ──────────────────────────────────────────────

    @McpTool(name = "scl_get_interface")
    @McpDescription(description = "Returns VAR_INPUT/OUTPUT/IN_OUT/VAR interface of a SCL Function Block. Use before writing FB call code.")
    suspend fun scl_get_interface(
        @McpDescription(description = "Block name, e.g. FB_TankControl") blockName: String
    ): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val fb = findBlock(project, blockName)
            ?: throw McpExpectedError("Block '$blockName' not found. Use scl_list_blocks to see available blocks.")
        return extractInterface(fb)
    }

    // ── Tool 3: scl_generate_fb ───────────────────────────────────────────────

    @McpTool(name = "scl_generate_fb")
    @McpDescription(description = "Generates a complete SCL Function Block and creates the .scl file in the project.")
    suspend fun scl_generate_fb(
        @McpDescription(description = "FB name, e.g. FB_MotorControl") blockName: String,
        @McpDescription(description = "What the FB does") description: String,
        @McpDescription(description = "Inputs as name:TYPE,name:TYPE, e.g. i_Start:BOOL,i_Speed:REAL") inputs: String = "",
        @McpDescription(description = "Outputs as name:TYPE,name:TYPE, e.g. o_Running:BOOL") outputs: String = "",
        @McpDescription(description = "CPU target: S7-1200 or S7-1500") cpuTarget: String = "S7-1200",
        @McpDescription(description = "File path relative to project root, e.g. FBs/FB_Motor.scl") targetFile: String = ""
    ): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val basePath = project.basePath ?: throw McpExpectedError("Project has no base path")

        val inputParams = parseParamList(inputs)
        val outputParams = parseParamList(outputs)

        val code = buildString {
            appendLine("FUNCTION_BLOCK \"$blockName\"")
            appendLine("{ S7_Optimized_Access := 'TRUE' }")
            appendLine("//============================================================")
            appendLine("// $blockName — $description")
            appendLine("// CPU Target: $cpuTarget")
            appendLine("// Generated: ${LocalDate.now()}")
            appendLine("//============================================================")
            appendLine()
            if (inputParams.isNotEmpty()) {
                appendLine("VAR_INPUT")
                inputParams.forEach { (n, t) -> appendLine("    $n : $t;") }
                appendLine("END_VAR")
                appendLine()
            }
            if (outputParams.isNotEmpty()) {
                appendLine("VAR_OUTPUT")
                outputParams.forEach { (n, t) -> appendLine("    $n : $t;") }
                appendLine("END_VAR")
                appendLine()
            }
            appendLine("VAR")
            appendLine("    s_State : INT;")
            appendLine("END_VAR")
            appendLine()
            appendLine("BEGIN")
            appendLine("    REGION State Machine")
            appendLine("        CASE s_State OF")
            appendLine("            0: // IDLE")
            appendLine("                ;")
            appendLine("            ELSE")
            appendLine("                s_State := 0;")
            appendLine("        END_CASE;")
            appendLine("    END_REGION")
            appendLine()
            appendLine("END_FUNCTION_BLOCK")
        }

        val filePath = if (targetFile.isNotBlank()) "$basePath/$targetFile"
                       else "$basePath/FBs/$blockName.scl"

        try {
            val file = java.io.File(filePath)
            file.parentFile.mkdirs()
            file.writeText(code)
            ApplicationManager.getApplication().invokeLater {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
            }
        } catch (e: Exception) {
            throw McpExpectedError("Error creating file: ${e.message}")
        }

        return "Generated: $filePath\nBlock: $blockName\nInputs: ${inputParams.size}, Outputs: ${outputParams.size}\n\n$code"
    }

    // ── Tool 4: scl_validate_file ──────────────────────────────────────────────

    @McpTool(name = "scl_validate_file")
    @McpDescription(description = "Validates an SCL file using the plugin linter. Returns errors and warnings with line numbers.")
    suspend fun scl_validate_file(
        @McpDescription(description = "File path relative to project root, e.g. FBs/FB_TankControl.scl") filePath: String
    ): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val basePath = project.basePath ?: throw McpExpectedError("Project has no base path")

        val vFile = LocalFileSystem.getInstance().findFileByPath("$basePath/$filePath")
            ?: throw McpExpectedError("File not found: $filePath")
        val psiFile = PsiManager.getInstance(project).findFile(vFile)
            ?: throw McpExpectedError("Cannot parse file: $filePath")

        val manager = InspectionManager.getInstance(project)
        val doc = FileDocumentManager.getInstance().getDocument(vFile)
        val problems = mutableListOf<String>()

        listOf(
            SclUnsupportedTypeInspection(),
            SclAbsoluteAddressInspection(),
            SclBooleanIfInspection(),
            SclTempVarInspection(),
            SclForIndexTypeInspection(),
            SclCaseElseInspection()
        ).forEach { inspection ->
            val holder = ProblemsHolder(manager, psiFile, false)
            val visitor = inspection.buildVisitor(holder, false)
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    super.visitElement(element)
                    visitor.visitElement(element)
                }
            })
            holder.results.forEach { problem ->
                val line = doc?.getLineNumber(problem.psiElement.textOffset)?.plus(1) ?: 0
                val sev = if (problem.highlightType == ProblemHighlightType.GENERIC_ERROR) "ERROR" else "WARNING"
                problems.add("Line $line [$sev]: ${problem.descriptionTemplate}")
            }
        }

        return if (problems.isEmpty()) "No issues found in $filePath"
        else "Found ${problems.size} issue(s) in $filePath:\n\n${problems.joinToString("\n")}"
    }

    // ── Tool 5: scl_read_io_list ───────────────────────────────────────────────

    @McpTool(name = "scl_read_io_list")
    @McpDescription(description = "Reads an I/O list CSV from the project and returns suggested VAR_INPUT/OUTPUT declarations. CSV format: Address,Name,Type,Description")
    suspend fun scl_read_io_list(
        @McpDescription(description = "CSV file path relative to project root, e.g. io_list/tank.csv") filePath: String
    ): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val basePath = project.basePath ?: throw McpExpectedError("Project has no base path")

        val file = java.io.File("$basePath/$filePath")
        if (!file.exists()) throw McpExpectedError(
            "File not found: $filePath\nExpected CSV: Address,Name,Type,Description\nExample: %I0.0,Emergency_Stop,BOOL,NF contact"
        )

        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        val memTags = mutableListOf<String>()
        var parseErrors = 0

        file.readLines().drop(1).forEach { line ->
            val parts = line.split(",").map { it.trim() }
            if (parts.size < 3) { parseErrors++; return@forEach }
            val (address, name, type) = parts
            val desc = parts.getOrElse(3) { "" }
            val entry = "    $name : $type; // $address — $desc"
            when {
                address.startsWith("%I") -> inputs.add(entry)
                address.startsWith("%Q") -> outputs.add(entry)
                address.startsWith("%M") -> memTags.add(entry)
            }
        }

        return buildString {
            appendLine("# I/O List: $filePath")
            appendLine("Inputs: ${inputs.size}, Outputs: ${outputs.size}, Memory: ${memTags.size}")
            appendLine()
            if (inputs.isNotEmpty()) {
                appendLine("## VAR_INPUT (suggested)")
                appendLine("```scl\nVAR_INPUT")
                inputs.forEach { appendLine(it) }
                appendLine("END_VAR\n```\n")
            }
            if (outputs.isNotEmpty()) {
                appendLine("## VAR_OUTPUT (suggested)")
                appendLine("```scl\nVAR_OUTPUT")
                outputs.forEach { appendLine(it) }
                appendLine("END_VAR\n```")
            }
            if (parseErrors > 0) appendLine("\n⚠️ $parseErrors lines could not be parsed")
        }
    }

    // ── Tool 6: scl_project_summary ───────────────────────────────────────────

    @McpTool(name = "scl_project_summary")
    @McpDescription(description = "Returns a full project summary: CPU target, all blocks with interfaces, and I/O files found. Use this first to understand the project before generating code.")
    suspend fun scl_project_summary(): String {
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
            ?: throw McpExpectedError("No project open in IntelliJ")
        val basePath = project.basePath ?: throw McpExpectedError("Project has no base path")
        val settings = SclCpuSettings.getInstance(project)

        return buildString {
            appendLine("# SCL Project Summary")
            appendLine("Project: ${project.name}")
            appendLine("Path: $basePath\n")
            appendLine("## CPU Target")
            appendLine("- Family: ${settings.cpuFamily.displayName}")
            appendLine("- Firmware: ${settings.firmwareVersion.displayName}\n")
            appendLine("## Blocks")
            findSclFiles(project).forEach { vFile ->
                val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return@forEach
                val rel = vFile.path.removePrefix(basePath)
                PsiTreeUtil.findChildrenOfType(psiFile, SclFunctionBlockDecl::class.java).forEach { fb ->
                    appendLine("### FB: ${fb.name} ($rel)")
                    appendLine(extractInterface(fb))
                }
            }
            appendLine("## I/O List Files")
            java.io.File(basePath).walkTopDown()
                .filter { it.extension in listOf("csv", "xlsx", "txt") }
                .forEach { appendLine("- ${it.path.removePrefix(basePath)}") }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun parseParamList(params: String): List<Pair<String, String>> {
        if (params.isBlank()) return emptyList()
        return params.split(",").mapNotNull { param ->
            val parts = param.trim().split(":")
            if (parts.size == 2) Pair(parts[0].trim(), parts[1].trim()) else null
        }
    }
}
