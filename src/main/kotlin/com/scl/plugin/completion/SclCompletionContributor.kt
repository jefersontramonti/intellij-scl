package com.scl.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclTypes

/**
 * Code completion para SCL — Fase 3.
 *
 * Oferece sugestoes de palavras-chave, tipos, constantes e operadores
 * em qualquer posicao valida dentro de um arquivo .scl.
 *
 * DumbAware: funciona mesmo enquanto o IntelliJ esta indexando o projeto.
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/completion-contributor.html
 */
class SclCompletionContributor : CompletionContributor(), DumbAware {

    init {
        // PlatformPatterns.psiElement(PsiElement::class.java) fornece o tipo T
        // explicitamente, evitando erro de inferencia no Kotlin com IntelliJ 2026.x.
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclKeywordCompletionProvider
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider centralizado
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

    // ── Estruturas de bloco (nivel raiz) ──────────────────────────────────────
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

    // ── Secoes de declaracao ──────────────────────────────────────────────────
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

    // ── FB / Timers / Contadores / Trigger Siemens ────────────────────────────
    private val TYPES_BLOCKS = listOf(
        tp("TON",    "FB type", "(IN:=…, PT:=…)"),
        tp("TOF",    "FB type", "(IN:=…, PT:=…)"),
        tp("TP",     "FB type", "(IN:=…, PT:=…)"),
        tp("TONR",   "FB type", "(IN:=…, PT:=…, R:=…)"),
        tp("CTU",    "FB type", "(CU:=…, R:=…, PV:=…)"),
        tp("CTD",    "FB type", "(CD:=…, LD:=…, PV:=…)"),
        tp("CTUD",   "FB type", "(CU:=…, CD:=…, R:=…, LD:=…, PV:=…)"),
        tp("R_TRIG", "FB type", "(CLK:=…)"),
        tp("F_TRIG", "FB type", "(CLK:=…)"),
        tp("SR",     "FB type", "(S1:=…, R:=…)"),
        tp("RS",     "FB type", "(S:=…, R1:=…)"),
        tp("TIMER",    "param type"),
        tp("COUNTER",  "param type"),
        tp("POINTER",  "param type"),
        tp("ANY",      "param type"),
        tp("BLOCK_FB", "param type"),
        tp("BLOCK_FC", "param type"),
        tp("BLOCK_DB", "param type"),
    )

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
        // Nao completar dentro de comentarios ou literais string
        val tokenType = parameters.position.node?.elementType
        if (tokenType == SclTypes.LINE_COMMENT  ||
            tokenType == SclTypes.BLOCK_COMMENT ||
            tokenType == SclTypes.STRING_LITERAL) return

        // Case-insensitive: usuario pode digitar 'if' e receber 'IF'
        val r = result.caseInsensitive()

        CONTROL.forEach           { r.addElement(it) }
        BLOCKS.forEach            { r.addElement(it) }
        DECLARATIONS.forEach      { r.addElement(it) }
        TYPES_ELEMENTARY.forEach  { r.addElement(it) }
        TYPES_COMPOSITE.forEach   { r.addElement(it) }
        TYPES_BLOCKS.forEach      { r.addElement(it) }
        CONSTANTS.forEach         { r.addElement(it) }
        OPERATORS.forEach         { r.addElement(it) }
    }

    // ── Factories ────────────────────────────────────────────────────────────

    /** Keyword: negrito, typeText = "keyword" */
    private fun kw(text: String, tail: String? = null) =
        elem(text, "keyword", tail)

    /** Data type: negrito, typeText configuravel */
    private fun tp(text: String, typeText: String = "type", tail: String? = null) =
        elem(text, typeText, tail)

    /** Constant: negrito, typeText = "constant" */
    private fun cn(text: String, tail: String? = null) =
        elem(text, "constant", tail)

    /** Elemento base — bold() e o metodo correto em IntelliJ 2026.x */
    private fun elem(text: String, typeText: String, tail: String?): LookupElementBuilder {
        var e = LookupElementBuilder.create(text as Any)   // cast evita ambiguidade de overload
            .bold()                                         // bold() em vez de withBold(true)
            .withTypeText(typeText)
        if (tail != null) e = e.withTailText(tail, true)
        return e
    }
}
