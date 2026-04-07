package com.scl.plugin.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.scl.plugin.language.SclFileType

/**
 * Pagina de configuracao de cores para SCL.
 *
 * Aparece em: Settings → Editor → Color Scheme → SCL
 * Permite ao usuario personalizar as cores de cada categoria de token.
 *
 * A demo text e processada pelo SclSyntaxHighlighter normal — sem tags extras.
 * Fonte: https://plugins.jetbrains.com/docs/intellij/syntax-highlighter-and-color-settings-page.html
 */
class SclColorSettingsPage : ColorSettingsPage {

    private companion object {
        val DESCRIPTORS = arrayOf(
            // ── Keywords ─────────────────────────────────────────────────
            AttributesDescriptor("Keywords//Block keywords (FUNCTION_BLOCK, DATA_BLOCK…)",  SclHighlighterColors.KEYWORD),
            AttributesDescriptor("Keywords//Control flow (IF, FOR, WHILE, CASE…)",          SclHighlighterColors.KEYWORD),
            AttributesDescriptor("Keywords//Operators (AND, OR, NOT, MOD, DIV)",            SclHighlighterColors.KEYWORD),

            // ── Types ─────────────────────────────────────────────────────
            AttributesDescriptor("Types//Elementary types (BOOL, INT, REAL, TIME…)",        SclHighlighterColors.DATA_TYPE),
            AttributesDescriptor("Types//FB / Timer / Counter types (TON, CTU…)",           SclHighlighterColors.DATA_TYPE),
            AttributesDescriptor("Types//Parameter types (TIMER, COUNTER, POINTER, ANY)",   SclHighlighterColors.DATA_TYPE),

            // ── Literals ──────────────────────────────────────────────────
            AttributesDescriptor("Literals//Number (integer, real, hex)",                   SclHighlighterColors.NUMBER),
            AttributesDescriptor("Literals//Boolean (TRUE, FALSE)",                         SclHighlighterColors.NUMBER),
            AttributesDescriptor("Literals//Time literal (T#5S, DT#…, TOD#…)",             SclHighlighterColors.TIME_LITERAL),
            AttributesDescriptor("Literals//String ('text')",                               SclHighlighterColors.STRING),

            // ── Identifiers ───────────────────────────────────────────────
            AttributesDescriptor("Identifiers//Global tag — quoted (\"TagName\")",          SclHighlighterColors.QUOTED_IDENTIFIER),
            AttributesDescriptor("Identifiers//Local variable (#varName)",                  SclHighlighterColors.LOCAL_VAR),

            // ── Other ─────────────────────────────────────────────────────
            AttributesDescriptor("Comments//Line comment (//)",                             SclHighlighterColors.COMMENT),
            AttributesDescriptor("Comments//Block comment (* … *)",                         SclHighlighterColors.COMMENT),
            AttributesDescriptor("Operators (symbols: :=, +, <, =>, …)",                   SclHighlighterColors.OPERATOR),
            AttributesDescriptor("Memory access (%MW, %I, %Q)",                            SclHighlighterColors.MEMORY_ACCESS),
            AttributesDescriptor("Invalid character",                                       SclHighlighterColors.BAD_CHARACTER),
        )

        // Demo text — processado pelo lexer SCL normal.
        // Deve cobrir todas as categorias de token para que o preview seja util.
        val DEMO_TEXT = """
// Tank level control — SCL TIA Portal V19
FUNCTION_BLOCK "FB_TankControl"
{ S7_Optimized_Access := 'TRUE' }
VAR_INPUT
    xEnable   : BOOL;
    rSetpoint : REAL := 100.0;
    tDelay    : TIME := T#5S;
    iMax      : INT  := 32767;
END_VAR
VAR_OUTPUT
    xValveOpen : BOOL;
END_VAR
VAR
    tFill   : TON;
    rLevel  : REAL;
    iCount  : INT;
    sStatus : STRING[32];
END_VAR
CONST
    SCALE : REAL := 0.01;
END_CONST

BEGIN
    // --- Main logic ---
    (* Block comment: timer call *)
    tFill(IN := xEnable AND rLevel < rSetpoint, PT := tDelay);

    IF tFill.Q THEN
        xValveOpen := TRUE;
        rLevel := rLevel + 1.5;
        "DB_Diagnostics".level := rLevel * SCALE;
        #localCounter := #localCounter + 1;
    ELSIF rLevel >= rSetpoint THEN
        xValveOpen := FALSE;
    ELSE
        RETURN;
    END_IF;

    CASE iCount OF
        0:      sStatus := 'idle';
        1..10:  sStatus := 'running';
        ELSE:   EXIT;
    END_CASE;

    FOR iCount := 1 TO iMax BY 1 DO
        %MW100 := iCount;
        IF NOT xEnable THEN EXIT; END_IF;
    END_FOR;

    REGION Diagnostics
        "DB_Diagnostics".ok := OK AND ENO;
    END_REGION

END_FUNCTION_BLOCK
""".trimIndent()
    }

    override fun getHighlighter(): SyntaxHighlighter = SclSyntaxHighlighter()

    override fun getDisplayName(): String = "SCL"

    override fun getIcon() = SclFileType.icon

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDemoText(): String = DEMO_TEXT

    // Sem tags adicionais — o lexer SCL faz todo o highlighting
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
}
