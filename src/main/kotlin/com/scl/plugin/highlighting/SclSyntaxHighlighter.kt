package com.scl.plugin.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.scl.plugin.lexer.SclLexerAdapter
import com.scl.plugin.psi.SclTypes

/**
 * Define as cores de syntax highlighting para SCL.
 *
 * Cada TextAttributesKey mapeia uma categoria de token para um estilo visual.
 * O usuario pode customizar as cores em:
 *   Settings > Editor > Color Scheme > SCL
 *
 * Fonte: https://plugins.jetbrains.com/docs/intellij/syntax-highlighter-and-color-settings-page.html
 */
object SclHighlighterColors {

    @JvmField
    val KEYWORD = createTextAttributesKey(
        "SCL_KEYWORD",
        DefaultLanguageHighlighterColors.KEYWORD
    )

    @JvmField
    val DATA_TYPE = createTextAttributesKey(
        "SCL_DATA_TYPE",
        DefaultLanguageHighlighterColors.CLASS_NAME
    )

    @JvmField
    val COMMENT = createTextAttributesKey(
        "SCL_COMMENT",
        DefaultLanguageHighlighterColors.LINE_COMMENT
    )

    @JvmField
    val STRING = createTextAttributesKey(
        "SCL_STRING",
        DefaultLanguageHighlighterColors.STRING
    )

    // Identificadores globais TIA Portal ("TagName")
    @JvmField
    val QUOTED_IDENTIFIER = createTextAttributesKey(
        "SCL_QUOTED_IDENTIFIER",
        DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    )

    // Variaveis locais TIA Portal (#varName)
    @JvmField
    val LOCAL_VAR = createTextAttributesKey(
        "SCL_LOCAL_VAR",
        DefaultLanguageHighlighterColors.LOCAL_VARIABLE
    )

    @JvmField
    val NUMBER = createTextAttributesKey(
        "SCL_NUMBER",
        DefaultLanguageHighlighterColors.NUMBER
    )

    @JvmField
    val TIME_LITERAL = createTextAttributesKey(
        "SCL_TIME_LITERAL",
        DefaultLanguageHighlighterColors.NUMBER
    )

    @JvmField
    val OPERATOR = createTextAttributesKey(
        "SCL_OPERATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN
    )

    @JvmField
    val MEMORY_ACCESS = createTextAttributesKey(
        "SCL_MEMORY_ACCESS",
        DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    )

    @JvmField
    val BAD_CHARACTER = createTextAttributesKey(
        "SCL_BAD_CHARACTER",
        HighlighterColors.BAD_CHARACTER
    )
}

class SclSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = SclLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        when (tokenType) {

            // ── Estruturas de bloco ─────────────────────────────────────────
            SclTypes.FUNCTION_BLOCK,
            SclTypes.END_FUNCTION_BLOCK,
            SclTypes.FUNCTION,
            SclTypes.END_FUNCTION,
            SclTypes.ORGANIZATION_BLOCK,
            SclTypes.END_ORGANIZATION_BLOCK,
            SclTypes.DATA_BLOCK,
            SclTypes.END_DATA_BLOCK,
            SclTypes.TYPE,
            SclTypes.END_TYPE,
            SclTypes.STRUCT,
            SclTypes.END_STRUCT,
            SclTypes.BEGIN,

            // ── Secoes de variaveis ─────────────────────────────────────────
            SclTypes.VAR,
            SclTypes.VAR_INPUT,
            SclTypes.VAR_OUTPUT,
            SclTypes.VAR_IN_OUT,
            SclTypes.VAR_STATIC,
            SclTypes.VAR_TEMP,
            SclTypes.VAR_CONSTANT,
            SclTypes.END_VAR,

            // ── Constantes ──────────────────────────────────────────────────
            SclTypes.CONST,
            SclTypes.END_CONST,

            // ── Labels de salto ─────────────────────────────────────────────
            SclTypes.LABEL,
            SclTypes.END_LABEL,
            SclTypes.GOTO,

            // ── Regioes TIA Portal ──────────────────────────────────────────
            SclTypes.REGION,
            SclTypes.END_REGION,

            // ── Controle de fluxo ───────────────────────────────────────────
            SclTypes.IF,
            SclTypes.THEN,
            SclTypes.ELSIF,
            SclTypes.ELSE,
            SclTypes.END_IF,
            SclTypes.CASE,
            SclTypes.OF,
            SclTypes.END_CASE,
            SclTypes.FOR,
            SclTypes.TO,
            SclTypes.BY,
            SclTypes.DO,
            SclTypes.END_FOR,
            SclTypes.WHILE,
            SclTypes.END_WHILE,
            SclTypes.REPEAT,
            SclTypes.UNTIL,
            SclTypes.END_REPEAT,
            SclTypes.RETURN,
            SclTypes.EXIT,
            SclTypes.CONTINUE,

            // ── Operadores logicos / aritmeticos (palavras-chave) ───────────
            SclTypes.AND,
            SclTypes.OR,
            SclTypes.NOT,
            SclTypes.XOR,
            SclTypes.MOD,
            SclTypes.DIV,

            // ── Palavras reservadas adicionais ──────────────────────────────
            SclTypes.VOID,
            SclTypes.NIL,
            SclTypes.EN,
            SclTypes.ENO,
            SclTypes.OK,
            SclTypes.ARRAY ->
                keys(SclHighlighterColors.KEYWORD)

            // ── Tipos de dados elementares ──────────────────────────────────
            SclTypes.TYPE_BOOL,
            SclTypes.TYPE_BYTE,
            SclTypes.TYPE_WORD,
            SclTypes.TYPE_DWORD,
            SclTypes.TYPE_LWORD,
            SclTypes.TYPE_INT,
            SclTypes.TYPE_UINT,
            SclTypes.TYPE_DINT,
            SclTypes.TYPE_UDINT,
            SclTypes.TYPE_LINT,
            SclTypes.TYPE_ULINT,
            SclTypes.TYPE_REAL,
            SclTypes.TYPE_LREAL,
            SclTypes.TYPE_CHAR,
            SclTypes.TYPE_STRING,
            SclTypes.TYPE_TIME,
            SclTypes.TYPE_DATE,
            SclTypes.TYPE_TOD,
            SclTypes.TYPE_DTL,
            SclTypes.TYPE_S5TIME,
            SclTypes.TYPE_DATE_AND_TIME,
            SclTypes.TYPE_TON,
            SclTypes.TYPE_TOF,
            SclTypes.TYPE_TP,
            SclTypes.TYPE_TONR,
            SclTypes.TYPE_CTU,
            SclTypes.TYPE_CTD,
            SclTypes.TYPE_CTUD,

            // ── Tipos de parametro especiais ────────────────────────────────
            SclTypes.TIMER,
            SclTypes.COUNTER,
            SclTypes.POINTER,
            SclTypes.ANY,
            SclTypes.BLOCK_FB,
            SclTypes.BLOCK_FC,
            SclTypes.BLOCK_DB,
            SclTypes.BLOCK_SDB,
            SclTypes.BLOCK_SFB,
            SclTypes.BLOCK_SFC ->
                keys(SclHighlighterColors.DATA_TYPE)

            SclTypes.LINE_COMMENT,
            SclTypes.BLOCK_COMMENT ->
                keys(SclHighlighterColors.COMMENT)

            SclTypes.STRING_LITERAL ->
                keys(SclHighlighterColors.STRING)

            SclTypes.QUOTED_IDENTIFIER ->
                keys(SclHighlighterColors.QUOTED_IDENTIFIER)

            SclTypes.LOCAL_VAR_ID ->
                keys(SclHighlighterColors.LOCAL_VAR)

            SclTypes.INTEGER_LITERAL,
            SclTypes.REAL_LITERAL,
            SclTypes.BOOL_LITERAL ->
                keys(SclHighlighterColors.NUMBER)

            SclTypes.TIME_LITERAL ->
                keys(SclHighlighterColors.TIME_LITERAL)

            SclTypes.MEMORY_ACCESS ->
                keys(SclHighlighterColors.MEMORY_ACCESS)

            SclTypes.ASSIGN,
            SclTypes.PLUS_ASSIGN,
            SclTypes.MINUS_ASSIGN,
            SclTypes.MULTIPLY_ASSIGN,
            SclTypes.DIVIDE_ASSIGN,
            SclTypes.OUTPUT_ASSIGN,
            SclTypes.AMPERSAND,
            SclTypes.PLUS,
            SclTypes.MINUS,
            SclTypes.MULTIPLY,
            SclTypes.DIVIDE,
            SclTypes.POWER,
            SclTypes.EQ,
            SclTypes.NEQ,
            SclTypes.LT,
            SclTypes.GT,
            SclTypes.LEQ,
            SclTypes.GEQ ->
                keys(SclHighlighterColors.OPERATOR)

            TokenType.BAD_CHARACTER ->
                keys(SclHighlighterColors.BAD_CHARACTER)

            else -> emptyArray()
        }

    private fun keys(key: TextAttributesKey): Array<TextAttributesKey> = arrayOf(key)
}
