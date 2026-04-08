package com.scl.plugin.completion

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.psi.SclCallStmt
import com.scl.plugin.psi.SclTypes

/**
 * Popup de informação de parâmetros SCL — Fase 4A (fix Bug 2).
 *
 * Exibe assinatura builtin para TRÊS formas de chamada:
 *   1. `TON(…)`           — nome é diretamente um builtin
 *   2. `myTimer(…)`       — nome resolve via VAR section (ex: myTimer : TON)
 *   3. `#myTimer(…)`      — idem, com prefixo # (LOCAL_VAR_ID)
 *
 * Exemplos de exibição:
 *   TON / myTimer → "IN: BOOL :=, PT: TIME :=, Q: BOOL =>, ET: TIME =>"
 *   LEN           → "IN: STRING :="   (parâmetro atual em negrito)
 *
 * Ativação:
 *   – Automática : ao digitar `(` após nome reconhecido
 *   – Manual     : Ctrl+P (ou Cmd+P no macOS) com cursor dentro de `(...)`
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/parameter-info.html
 */
class SclParameterInfoHandler : ParameterInfoHandler<SclCallStmt, SclBuiltin>, DumbAware {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Localiza o elemento-âncora ao abrir o popup (Ctrl+P ou digitação de `(`)
    // ─────────────────────────────────────────────────────────────────────────

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): SclCallStmt? {
        val call = findCallAt(context.file, context.offset) ?: return null
        // Só ativa se o cursor estiver DENTRO dos parênteses
        val lparen = call.node.findChildByType(SclTypes.LPAREN) ?: return null
        if (context.offset <= lparen.startOffset) return null
        return call
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Carrega os itens a exibir no popup (chamado uma vez quando o popup abre)
    // ─────────────────────────────────────────────────────────────────────────

    override fun showParameterInfo(element: SclCallStmt, context: CreateParameterInfoContext) {
        // resolveBuiltin() tenta lookup direto e, se falhar, busca na VAR section
        val builtin = element.resolveBuiltin() ?: return
        context.itemsToShow = arrayOf(builtin)
        context.highlightedElement = element
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Reencontra o elemento-âncora a cada movimento do cursor
    // ─────────────────────────────────────────────────────────────────────────

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): SclCallStmt? {
        val call = findCallAt(context.file, context.offset) ?: return null
        val lparen = call.node.findChildByType(SclTypes.LPAREN) ?: return null
        if (context.offset <= lparen.startOffset) return null
        return call
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Atualiza o índice do parâmetro atual (contagem de vírgulas)
    // ─────────────────────────────────────────────────────────────────────────

    override fun updateParameterInfo(callStmt: SclCallStmt, context: UpdateParameterInfoContext) {
        val offset  = context.offset
        val argList = callStmt.argList

        if (argList == null) {
            // Cursor logo após `(` — sem argumentos ainda → primeiro parâmetro
            context.setCurrentParameter(0)
            return
        }

        // Conta vírgulas antes do cursor para determinar qual parâmetro está ativo
        var index = 0
        var node  = argList.node.firstChildNode
        while (node != null && node.startOffset < offset) {
            if (node.elementType == SclTypes.COMMA) index++
            node = node.treeNext
        }
        context.setCurrentParameter(index)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Renderiza a linha do popup para um SclBuiltin
    // ─────────────────────────────────────────────────────────────────────────

    override fun updateUI(builtin: SclBuiltin, context: ParameterInfoUIContext) {
        val params = builtin.parameters

        if (params.isEmpty()) {
            context.setupUIComponentPresentation(
                "(sem parâmetros)", -1, -1,
                false, false, false,
                context.defaultParameterColor,
            )
            return
        }

        val sb             = StringBuilder()
        var highlightStart = -1
        var highlightEnd   = -1
        val currentIdx     = context.currentParameterIndex

        for ((i, param) in params.withIndex()) {
            if (i > 0) sb.append(", ")

            val start = sb.length

            // INPUT/INOUT usa :=  |  OUTPUT usa =>
            val op = when (param.direction) {
                Direction.OUTPUT -> "=>"
                else             -> ":="
            }
            sb.append(param.name)
            sb.append(": ")
            sb.append(param.type)
            sb.append(" ")
            sb.append(op)
            if (!param.required) sb.append(" (opt)")

            if (i == currentIdx) {
                highlightStart = start
                highlightEnd   = sb.length
            }
        }

        context.setupUIComponentPresentation(
            sb.toString(),
            highlightStart,
            highlightEnd,
            false, false, false,
            context.defaultParameterColor,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Encontra o [SclCallStmt] que contém o [offset] no arquivo.
     * Tenta offset-1 primeiro para capturar o caso logo após `(`.
     */
    private fun findCallAt(file: PsiFile, offset: Int): SclCallStmt? {
        val element = file.findElementAt(offset - 1)
            ?: file.findElementAt(offset)
            ?: return null
        return PsiTreeUtil.getParentOfType(element, SclCallStmt::class.java)
    }
}
