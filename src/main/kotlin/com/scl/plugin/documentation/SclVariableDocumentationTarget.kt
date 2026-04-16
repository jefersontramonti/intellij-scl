package com.scl.plugin.documentation

import com.intellij.icons.AllIcons
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import com.scl.plugin.psi.SclVarSection

/**
 * Gera a documentação hover para uma variável SCL declarada.
 *
 * Duas saídas:
 *   - [computeDocumentationHint] → tooltip rápido (hover)
 *   - [computeDocumentation] → popup completo (Ctrl+Q)
 *
 * Extração de descrição:
 *   Padrão 1 — comentário `//` ao final da mesma linha da declaração
 *   Padrão 2 — comentário `(* ... *)` na linha imediatamente anterior
 */
class SclVariableDocumentationTarget(
    private val declaration: SclVarDecl,
) : DocumentationTarget {

    // ── Pointer para sobreviver a invalidações de PSI ─────────────────────────
    override fun createPointer(): Pointer<out SclVariableDocumentationTarget> {
        val ptr = SmartPointerManager.createPointer(declaration)
        return Pointer {
            // SmartPsiElementPointer expõe .element (não .dereference())
            val restored = ptr.element?.takeIf { it.isValid } ?: return@Pointer null
            SclVariableDocumentationTarget(restored)
        }
    }

    // ── Apresentação no Documentation Tool Window ─────────────────────────────
    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(varName())
            .icon(resolveIcon())
            .presentation()

    // ─────────────────────────────────────────────────────────────────────────
    // HOVER RÁPIDO — aparece no tooltip ao passar o mouse (computeDocumentationHint)
    // Formato: "<b>nome</b> — comentário: <code>TIPO</code>  <i>[SEÇÃO]</i>"
    // ─────────────────────────────────────────────────────────────────────────
    override fun computeDocumentationHint(): String {
        val name    = varName()
        val type    = varType()
        val section = sectionLabel()
        val comment = extractComment()

        return buildString {
            append("<b>$name</b>")
            if (comment.isNotBlank()) append(" — $comment")
            append(": <code>$type</code>")
            append("&nbsp;&nbsp;<i>[$section]</i>")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOCUMENTAÇÃO COMPLETA — Ctrl+Q / View → Quick Documentation
    // ─────────────────────────────────────────────────────────────────────────
    override fun computeDocumentation(): DocumentationResult {
        val name      = varName()
        val type      = varType()
        val section   = sectionLabel()
        val blockName = containingBlockName()
        val initValue = declaration.expression?.text?.trim()
        val comment   = extractComment()

        val html = buildString {
            // Cabeçalho: nome da variável
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$name</b>")
            append(DocumentationMarkup.DEFINITION_END)

            // Corpo: descrição do comentário (se houver)
            if (comment.isNotBlank()) {
                append(DocumentationMarkup.CONTENT_START)
                append(comment)
                append(DocumentationMarkup.CONTENT_END)
            }

            // Tabela de detalhes
            append(DocumentationMarkup.SECTIONS_START)

            appendSection("Type",    "<code>$type</code>")
            appendSection("Section", section)
            if (blockName != null) appendSection("Block", blockName)
            if (initValue != null)  appendSection("Default", "<code>$initValue</code>")

            append(DocumentationMarkup.SECTIONS_END)
        }

        return DocumentationResult.documentation(html)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ─────────────────────────────────────────────────────────────────────────

    private fun varName(): String =
        declaration.node.firstChildNode
            ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
            ?.text ?: "?"

    private fun varType(): String =
        declaration.typeRef?.text?.trim() ?: "?"

    /**
     * Extrai o comentário da declaração.
     * Tenta Padrão 1 (// inline), depois Padrão 2 ((* bloco *) anterior).
     */
    private fun extractComment(): String =
        findInlineLineComment() ?: findPrecedingBlockComment() ?: ""

    /**
     * Padrão 1: busca o próximo sibling LINE_COMMENT na MESMA linha.
     * Para ao encontrar uma quebra de linha no whitespace.
     */
    private fun findInlineLineComment(): String? {
        var s = declaration.nextSibling
        while (s != null) {
            val type = s.node?.elementType
            // Whitespace com newline = chegou na próxima linha
            if (type == com.intellij.psi.TokenType.WHITE_SPACE && s.text.contains('\n')) break
            if (type == SclTypes.LINE_COMMENT) {
                return s.text.removePrefix("//").trim()
            }
            s = s.nextSibling
        }
        return null
    }

    /**
     * Padrão 2: busca o sibling ANTERIOR BLOCK_COMMENT (ignora whitespace).
     */
    private fun findPrecedingBlockComment(): String? {
        var s = declaration.prevSibling
        while (s != null) {
            val type = s.node?.elementType
            if (type == com.intellij.psi.TokenType.WHITE_SPACE) {
                s = s.prevSibling
                continue
            }
            if (type == SclTypes.BLOCK_COMMENT) {
                return s.text
                    .removePrefix("(*").removeSuffix("*)").trim()
            }
            break
        }
        return null
    }

    /** Rótulo legível da seção VAR que contém a declaração. */
    private fun sectionLabel(): String {
        val section = declaration.parent as? SclVarSection ?: return "VAR"
        return when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT    -> "VAR_INPUT"
            SclTypes.VAR_OUTPUT   -> "VAR_OUTPUT"
            SclTypes.VAR_IN_OUT   -> "VAR_IN_OUT"
            SclTypes.VAR_STATIC   -> "VAR (STATIC)"
            SclTypes.VAR_TEMP     -> "VAR_TEMP"
            SclTypes.VAR_CONSTANT -> "CONST"
            else                  -> "VAR"
        }
    }

    /** Nome do bloco FB/FC/OB que contém esta declaração. */
    private fun containingBlockName(): String? {
        val block: PsiElement = PsiTreeUtil.getParentOfType(
            declaration,
            SclFunctionBlockDecl::class.java,
            SclFunctionDecl::class.java,
            SclOrgBlockDecl::class.java,
        ) ?: return null
        return extractBlockName(block)
    }

    private fun extractBlockName(block: PsiElement): String? {
        val keyword = when (block) {
            is SclFunctionBlockDecl -> SclTypes.FUNCTION_BLOCK
            is SclFunctionDecl      -> SclTypes.FUNCTION
            is SclOrgBlockDecl      -> SclTypes.ORGANIZATION_BLOCK
            else                    -> return null
        }
        var node = block.node.firstChildNode
        var seenKw = false
        while (node != null) {
            when {
                node.elementType == keyword -> seenKw = true
                seenKw && node.elementType == SclTypes.IDENTIFIER ->
                    return node.text
                seenKw && node.elementType == SclTypes.QUOTED_IDENTIFIER ->
                    return node.text.trim('"')
            }
            node = node.treeNext
        }
        return null
    }

    /** Ícone baseado na seção, consistente com o completion. */
    private fun resolveIcon(): javax.swing.Icon {
        val section = declaration.parent as? SclVarSection
            ?: return AllIcons.Nodes.Variable
        return when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT    -> AllIcons.Nodes.Parameter
            SclTypes.VAR_OUTPUT   -> AllIcons.Nodes.Property
            SclTypes.VAR_IN_OUT   -> AllIcons.Nodes.PropertyRead
            SclTypes.VAR_STATIC   -> AllIcons.Nodes.Field
            SclTypes.VAR_TEMP     -> AllIcons.Nodes.Variable
            SclTypes.VAR_CONSTANT -> AllIcons.Nodes.Constant
            else                  -> AllIcons.Nodes.Variable
        }
    }

    /** Helper para seção da tabela de detalhes. */
    private fun StringBuilder.appendSection(header: String, value: String) {
        append(DocumentationMarkup.SECTION_HEADER_START)
        append(header)
        append(DocumentationMarkup.SECTION_SEPARATOR)
        append(value)
        append(DocumentationMarkup.SECTION_END)
    }
}
