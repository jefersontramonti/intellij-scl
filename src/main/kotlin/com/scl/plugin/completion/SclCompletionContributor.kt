package com.scl.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclArgList
import com.scl.plugin.psi.SclCallStmt
import com.scl.plugin.psi.SclTypes

/**
 * Code completion para SCL — Fase 3 + Fase 4A.
 *
 * Dois providers registrados:
 *
 *   1. [SclKeywordCompletionProvider] — palavras-chave, tipos, constantes,
 *      operadores e builtins (com template de chamada ao completar).
 *
 *   2. [SclParamNameCompletionProvider] — dentro de `BUILTIN(` sugere os
 *      nomes dos parâmetros formais (`IN :=`, `PT :=`, `Q =>` …).
 *
 * DumbAware: ambos funcionam durante indexação do projeto.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/completion-contributor.html
 */
class SclCompletionContributor : CompletionContributor(), DumbAware {

    init {
        // ── Provider 1: palavras-chave e builtins ─────────────────────────────
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclKeywordCompletionProvider,
        )

        // ── Provider 2: nomes de parâmetros dentro de chamada builtin ─────────
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclParamNameCompletionProvider,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider 1 — palavras-chave / tipos / constantes / builtins
// ─────────────────────────────────────────────────────────────────────────────

private object SclKeywordCompletionProvider : CompletionProvider<CompletionParameters>() {

    // ── Controle de fluxo ─────────────────────────────────────────────────────
    private val CONTROL = listOf(
        kw("IF",           " … END_IF"),
        kw("ELSIF",        " condition THEN"),
        kw("ELSE"),
        kw("END_IF",       ";"),
        kw("CASE",         " … OF … END_CASE"),
        kw("OF"),
        kw("END_CASE",     ";"),
        kw("FOR",          " … TO … DO … END_FOR"),
        kw("TO"),
        kw("BY"),
        kw("DO"),
        kw("END_FOR",      ";"),
        kw("WHILE",        " … DO … END_WHILE"),
        kw("END_WHILE",    ";"),
        kw("REPEAT",       " … UNTIL … END_REPEAT"),
        kw("UNTIL"),
        kw("END_REPEAT",   ";"),
        kw("RETURN",       ";"),
        kw("EXIT",         ";"),
        kw("CONTINUE",     ";"),
        kw("GOTO",         " label;"),
        kw("REGION",       " name … END_REGION"),
        kw("END_REGION"),
    )

    // ── Estruturas de bloco (nível raiz) ──────────────────────────────────────
    private val BLOCKS = listOf(
        kw("FUNCTION_BLOCK",         " name … END_FUNCTION_BLOCK"),
        kw("END_FUNCTION_BLOCK"),
        kw("FUNCTION",               " name : type … END_FUNCTION"),
        kw("END_FUNCTION"),
        kw("ORGANIZATION_BLOCK",     " name … END_ORGANIZATION_BLOCK"),
        kw("END_ORGANIZATION_BLOCK"),
        kw("DATA_BLOCK",             " name … END_DATA_BLOCK"),
        kw("END_DATA_BLOCK"),
        kw("TYPE",                   " … END_TYPE"),
        kw("END_TYPE"),
        kw("STRUCT",                 " … END_STRUCT"),
        kw("END_STRUCT",             ";"),
        kw("BEGIN"),
    )

    // ── Seções de declaração ──────────────────────────────────────────────────
    private val DECLARATIONS = listOf(
        kw("VAR",          " … END_VAR"),
        kw("VAR_INPUT",    " … END_VAR"),
        kw("VAR_OUTPUT",   " … END_VAR"),
        kw("VAR_IN_OUT",   " … END_VAR"),
        kw("VAR_STATIC",   " … END_VAR"),
        kw("VAR_TEMP",     " … END_VAR"),
        kw("VAR_CONSTANT", " … END_VAR"),
        kw("END_VAR"),
        kw("CONST",        " … END_CONST"),
        kw("END_CONST"),
        kw("LABEL",        " … END_LABEL"),
        kw("END_LABEL"),
    )

    // ── Tipos de dados elementares ────────────────────────────────────────────
    private val TYPES_ELEMENTARY = listOf(
        tp("BOOL"),   tp("BYTE"),  tp("WORD"),  tp("DWORD"), tp("LWORD"),
        tp("INT"),    tp("SINT"),  tp("DINT"),  tp("LINT"),
        tp("UINT"),   tp("USINT"), tp("UDINT"), tp("ULINT"),
        tp("REAL"),   tp("LREAL"),
        tp("TIME"),   tp("LTIME"),
        tp("DATE"),   tp("TOD"),   tp("DT"),    tp("DTL"),
        tp("DATE_AND_TIME"),       tp("S5TIME"),
        tp("STRING"), tp("WSTRING"),
        tp("CHAR"),   tp("WCHAR"),
        tp("VOID"),
    )

    // ── Tipos compostos ───────────────────────────────────────────────────────
    private val TYPES_COMPOSITE = listOf(
        elem("ARRAY",  "type",  "[lo..hi] OF type"),
        elem("STRUCT", "type",  " … END_STRUCT"),
    )

    // ── FB / Timers / Contadores Siemens — com template de chamada ────────────
    // Cada item produz dois LookupElements:
    //   • versão "type"  → insere só o nome (uso em declaração VAR)
    //   • versão "call"  → insere template com parâmetros (uso em statement)
    // O segundo é criado com InsertHandler que detecta o contexto.
    private val TYPES_BLOCKS: List<LookupElement> = buildList {
        // FBs com parâmetros conhecidos — template de chamada via InsertHandler
        for (builtin in SclBuiltinFunctions.catalog.filter { it.kind == Kind.FB }) {
            val suffix = builtin.parameters
                .filter { it.direction != Direction.OUTPUT }
                .joinToString(prefix = "(", postfix = ")") { "${it.name}:=…" }
            add(
                LookupElementBuilder.create(builtin.name as Any)
                    .bold()
                    .withTypeText(builtin.kindLabel)
                    .withTailText(suffix, true)
                    .withInsertHandler(builtinInsertHandler(builtin))
            )
        }
        // Itens adicionais que não estão no catálogo de builtins
        add(tp("SR",       "FB type", "(S1:=…, R:=…)"))
        add(tp("RS",       "FB type", "(S:=…, R1:=…)"))
        add(tp("TIMER",    "param type"))
        add(tp("COUNTER",  "param type"))
        add(tp("POINTER",  "param type"))
        add(tp("ANY",      "param type"))
        add(tp("BLOCK_FB", "param type"))
        add(tp("BLOCK_FC", "param type"))
        add(tp("BLOCK_DB", "param type"))
    }

    // ── FCs builtins — com template de chamada ────────────────────────────────
    private val BUILTINS_FC: List<LookupElement> = buildList {
        for (builtin in SclBuiltinFunctions.catalog.filter { it.kind == Kind.FC }) {
            val retSuffix = builtin.returnType?.let { " : $it" } ?: ""
            add(
                LookupElementBuilder.create(builtin.name as Any)
                    .bold()
                    .withTypeText("Function$retSuffix")
                    .withTailText(builtin.description.ifEmpty { builtin.name }, true)
                    .withInsertHandler(builtinInsertHandler(builtin))
            )
        }
    }

    // ── Constantes / flags predefinidas ───────────────────────────────────────
    private val CONSTANTS = listOf(
        cn("TRUE"),
        cn("FALSE"),
        cn("NIL",  " // null pointer"),
        cn("EN",   " // enable flag"),
        cn("ENO",  " // enable output flag"),
        cn("OK",   " // operation status"),
    )

    // ── Operadores (palavras-chave) ────────────────────────────────────────────
    private val OPERATORS = listOf(
        kw("AND"), kw("OR"), kw("XOR"), kw("NOT"),
        kw("MOD"), kw("DIV"),
    )

    // ─────────────────────────────────────────────────────────────────────────
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (isInsideComment(parameters.position)) return
        if (isInsideString(parameters.position))  return

        val r = result.caseInsensitive()

        CONTROL.forEach           { r.addElement(it) }
        BLOCKS.forEach            { r.addElement(it) }
        DECLARATIONS.forEach      { r.addElement(it) }
        TYPES_ELEMENTARY.forEach  { r.addElement(it) }
        TYPES_COMPOSITE.forEach   { r.addElement(it) }
        TYPES_BLOCKS.forEach      { r.addElement(it) }
        BUILTINS_FC.forEach       { r.addElement(it) }
        CONSTANTS.forEach         { r.addElement(it) }
        OPERATORS.forEach         { r.addElement(it) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider 2 — nomes de parâmetros dentro de uma chamada builtin
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Quando o cursor está dentro de `BUILTIN(...)`, Ctrl+Space sugere os nomes
 * dos parâmetros formais com o operador correto:
 *   `IN :=`  para INPUT/INOUT
 *   `Q =>`   para OUTPUT
 *
 * Prioridade elevada (100.0) para aparecer no topo da lista.
 */
private object SclParamNameCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (isInsideComment(parameters.position)) return
        if (isInsideString(parameters.position))  return

        // Procura o SclCallStmt que envolve o cursor
        val callStmt = PsiTreeUtil.getParentOfType(
            parameters.position, SclCallStmt::class.java
        ) ?: return

        // Confirma que estamos após o LPAREN
        val lparen = callStmt.node.findChildByType(SclTypes.LPAREN) ?: return
        if (parameters.position.textRange.startOffset <= lparen.startOffset) return

        // Resolve o builtin: lookup direto (TON) ou via VAR section (myTimer → TON)
        val builtin = callStmt.resolveBuiltin() ?: return

        val r = result.caseInsensitive()
        for (param in builtin.parameters) {
            val op = when (param.direction) {
                Direction.OUTPUT -> "=>"
                else             -> ":="
            }
            val entry = LookupElementBuilder
                .create("${param.name} $op ")
                .bold()
                .withTypeText(param.type)
                .withTailText(if (!param.required) " (optional)" else "")
            r.addElement(PrioritizedLookupElement.withPriority(entry, 100.0))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// InsertHandler: template de chamada com TAB entre parâmetros
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Ao completar um builtin fora de uma seção VAR, insere um live template:
 *   `TON` → `TON(IN := $IN$, PT := $PT$, Q => $Q$, ET => $ET$)$END$`
 *
 * O usuário navega entre as variáveis com TAB.
 * Dentro de seções VAR (declaração de tipo) o handler é ignorado.
 */
private fun builtinInsertHandler(builtin: SclBuiltin): InsertHandler<LookupElement> =
    InsertHandler { ctx, _ ->
        // Não insere template em seções de declaração de variáveis
        if (isInsideVarSection(ctx.file.findElementAt(ctx.startOffset))) return@InsertHandler
        // Não insere se já há `(` logo após (usuário já digitou a abertura)
        val doc    = ctx.editor.document
        val offset = ctx.tailOffset
        if (offset < doc.textLength && doc.charsSequence[offset] == '(') return@InsertHandler

        if (builtin.parameters.isEmpty()) {
            // Sem parâmetros: insere apenas `()` e posiciona cursor após
            doc.insertString(offset, "()")
            ctx.editor.caretModel.moveToOffset(offset + 2)
            return@InsertHandler
        }

        // Constrói o texto do template com variáveis `$NAME$`
        val templateText = buildString {
            append("(")
            for ((i, param) in builtin.parameters.withIndex()) {
                if (i > 0) append(", ")
                val op = when (param.direction) {
                    Direction.OUTPUT -> "=>"
                    else             -> ":="
                }
                // Variável de template: $PARAM_NAME$ — TAB navega entre elas
                append("${param.name} $op \$${param.name}\$")
            }
            append(")\$END\$")
        }

        val mgr      = TemplateManager.getInstance(ctx.project)
        val template = mgr.createTemplate("", "scl", templateText)
        template.isToReformat = false
        mgr.startTemplate(ctx.editor, template)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Guardas de contexto (compartilhadas pelos dois providers)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sobe a árvore PSI verificando se algum ancestral é um comentário.
 * Cobre tokens LINE_COMMENT / BLOCK_COMMENT e nós que implementam PsiComment.
 */
internal fun isInsideComment(element: PsiElement): Boolean {
    var current: PsiElement? = element
    while (current != null) {
        if (current is PsiComment) return true
        val type = current.node?.elementType
        if (type == SclTypes.LINE_COMMENT || type == SclTypes.BLOCK_COMMENT) return true
        current = current.parent
    }
    return false
}

/** Verifica se o elemento está dentro de um literal string ou identificador quoted. */
internal fun isInsideString(element: PsiElement): Boolean {
    var current: PsiElement? = element
    while (current != null) {
        val type = current.node?.elementType
        if (type == SclTypes.STRING_LITERAL || type == SclTypes.QUOTED_IDENTIFIER) return true
        current = current.parent
    }
    return false
}

/** Verifica se o elemento está dentro de uma seção de declaração VAR. */
private fun isInsideVarSection(element: PsiElement?): Boolean {
    var current: PsiElement? = element
    while (current != null) {
        val type = current.node?.elementType
        if (type == SclTypes.VAR_SECTION || type == SclTypes.VAR_DECL) return true
        current = current.parent
    }
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
// Factories (usadas em SclKeywordCompletionProvider)
// ─────────────────────────────────────────────────────────────────────────────

/** Keyword: negrito, typeText = "keyword" */
private fun kw(text: String, tail: String? = null) = elem(text, "keyword", tail)

/** Data type: negrito, typeText configurável */
private fun tp(text: String, typeText: String = "type", tail: String? = null) =
    elem(text, typeText, tail)

/** Constant: negrito, typeText = "constant" */
private fun cn(text: String, tail: String? = null) = elem(text, "constant", tail)

/** Elemento base: bold() é o método correto em IntelliJ 2026.x */
private fun elem(text: String, typeText: String, tail: String?): LookupElementBuilder {
    var e = LookupElementBuilder.create(text as Any)
        .bold()
        .withTypeText(typeText)
    if (tail != null) e = e.withTailText(tail, true)
    return e
}
