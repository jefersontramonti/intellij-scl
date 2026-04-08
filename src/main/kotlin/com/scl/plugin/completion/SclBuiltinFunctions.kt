package com.scl.plugin.completion

import com.intellij.lang.ASTNode
import com.scl.plugin.psi.SclCallStmt
import com.scl.plugin.psi.SclTypes

// ─────────────────────────────────────────────────────────────────────────────
// Modelos de dados
// ─────────────────────────────────────────────────────────────────────────────

/** Direção de um parâmetro (INPUT usa `:=`, OUTPUT usa `=>`, INOUT usa `:=`). */
enum class Direction { INPUT, OUTPUT, INOUT }

/** Categoria da instrução Siemens. */
enum class Kind { FB, FC, OPERATOR }

/** Descrição de um único parâmetro formal de uma instrução builtin. */
data class SclParameter(
    val name: String,
    val type: String,
    val direction: Direction,
    val required: Boolean = true,
    val description: String = "",
)

/** Instrução builtin completa com catálogo de parâmetros. */
data class SclBuiltin(
    val name: String,
    val kind: Kind,
    val parameters: List<SclParameter>,
    val returnType: String? = null,
    val description: String = "",
    val s7_1500Only: Boolean = false,
) {
    /** Label para exibição na lista de completion. */
    val kindLabel: String get() = when (kind) {
        Kind.FB       -> "Timer FB"
        Kind.FC       -> "Function"
        Kind.OPERATOR -> "Operator"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Catálogo de instruções builtin Siemens TIA Portal / STEP 7
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Catálogo completo de instruções builtin SCL Siemens.
 *
 * Fonte: TIA Portal V19 STEP 7 SCL Reference Manual
 *   – Timers    : Seção 4.4
 *   – Counters  : Seção 4.5
 *   – Edge Trig : Seção 4.6
 *   – Math      : Seção 4.7
 *   – String    : Seção 4.8
 *   – Selection : Seção 4.9
 *   – System    : Seção 4.10
 */
object SclBuiltinFunctions {

    // ── Helpers de construção de parâmetros ───────────────────────────────────

    private fun inp(name: String, type: String, required: Boolean = true, desc: String = "") =
        SclParameter(name, type, Direction.INPUT,  required, desc)

    private fun out(name: String, type: String, desc: String = "") =
        SclParameter(name, type, Direction.OUTPUT, required = false, desc)

    private fun inout(name: String, type: String, desc: String = "") =
        SclParameter(name, type, Direction.INOUT,  required = true, desc)

    // ── Catálogo ──────────────────────────────────────────────────────────────

    val catalog: List<SclBuiltin> = buildList {

        // ── Timers (FB — requerem instância declarada) ────────────────────────

        add(SclBuiltin("TON", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "TIME"),
            out("Q",  "BOOL"),   out("ET", "TIME"),
        ), description = "Timer On Delay"))

        add(SclBuiltin("TOF", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "TIME"),
            out("Q",  "BOOL"),   out("ET", "TIME"),
        ), description = "Timer Off Delay"))

        add(SclBuiltin("TP", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "TIME"),
            out("Q",  "BOOL"),   out("ET", "TIME"),
        ), description = "Timer Pulse"))

        add(SclBuiltin("TONR", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("R", "BOOL"),  inp("PT", "TIME"),
            out("Q",  "BOOL"),   out("ET", "TIME"),
        ), description = "Timer On Delay Retentive"))

        // Variantes LTIME (S7-1500 apenas)
        add(SclBuiltin("TON_LTIME", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "LTIME"),
            out("Q",  "BOOL"),   out("ET", "LTIME"),
        ), s7_1500Only = true, description = "Timer On Delay (LTIME)"))

        add(SclBuiltin("TOF_LTIME", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "LTIME"),
            out("Q",  "BOOL"),   out("ET", "LTIME"),
        ), s7_1500Only = true, description = "Timer Off Delay (LTIME)"))

        add(SclBuiltin("TP_LTIME", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("PT", "LTIME"),
            out("Q",  "BOOL"),   out("ET", "LTIME"),
        ), s7_1500Only = true, description = "Timer Pulse (LTIME)"))

        add(SclBuiltin("TONR_LTIME", Kind.FB, listOf(
            inp("IN", "BOOL"),   inp("R", "BOOL"),  inp("PT", "LTIME"),
            out("Q",  "BOOL"),   out("ET", "LTIME"),
        ), s7_1500Only = true, description = "Timer On Delay Retentive (LTIME)"))

        // ── Contadores (FB) ───────────────────────────────────────────────────

        add(SclBuiltin("CTU", Kind.FB, listOf(
            inp("CU", "BOOL"),  inp("R",  "BOOL"),  inp("PV", "INT"),
            out("Q",  "BOOL"),  out("CV", "INT"),
        ), description = "Counter Up"))

        add(SclBuiltin("CTD", Kind.FB, listOf(
            inp("CD", "BOOL"),  inp("LD", "BOOL"),  inp("PV", "INT"),
            out("Q",  "BOOL"),  out("CV", "INT"),
        ), description = "Counter Down"))

        add(SclBuiltin("CTUD", Kind.FB, listOf(
            inp("CU", "BOOL"),  inp("CD", "BOOL"),
            inp("R",  "BOOL"),  inp("LD", "BOOL"),  inp("PV", "INT"),
            out("QU", "BOOL"),  out("QD", "BOOL"),  out("CV", "INT"),
        ), description = "Counter Up/Down"))

        // ── Detecção de borda (FB) ────────────────────────────────────────────

        add(SclBuiltin("R_TRIG", Kind.FB, listOf(
            inp("CLK", "BOOL"),
            out("Q",   "BOOL"),
        ), description = "Rising Edge Detect"))

        add(SclBuiltin("F_TRIG", Kind.FB, listOf(
            inp("CLK", "BOOL"),
            out("Q",   "BOOL"),
        ), description = "Falling Edge Detect"))

        // ── Matemática (FC) ───────────────────────────────────────────────────

        add(SclBuiltin("ABS",  Kind.FC, listOf(inp("IN", "ANY_NUM")), returnType = "ANY_NUM",
            description = "Absolute value"))
        add(SclBuiltin("SQRT", Kind.FC, listOf(inp("IN", "REAL")),    returnType = "REAL",
            description = "Square root"))
        add(SclBuiltin("SQR",  Kind.FC, listOf(inp("IN", "REAL")),    returnType = "REAL",
            description = "Square (x²)"))

        add(SclBuiltin("SIN",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Sine (rad)"))
        add(SclBuiltin("COS",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Cosine (rad)"))
        add(SclBuiltin("TAN",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Tangent (rad)"))
        add(SclBuiltin("ASIN", Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Arcsine"))
        add(SclBuiltin("ACOS", Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Arccosine"))
        add(SclBuiltin("ATAN", Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Arctangent"))
        add(SclBuiltin("EXP",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "e^x"))
        add(SclBuiltin("LN",   Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Natural log"))
        add(SclBuiltin("LOG",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL", description = "Log base 10"))
        add(SclBuiltin("FRAC", Kind.FC, listOf(inp("IN", "REAL")), returnType = "REAL",
            s7_1500Only = true, description = "Fractional part"))

        // ── Conversão (FC) ────────────────────────────────────────────────────

        add(SclBuiltin("INT_TO_REAL",   Kind.FC, listOf(inp("IN", "INT")),   returnType = "REAL"))
        add(SclBuiltin("DINT_TO_REAL",  Kind.FC, listOf(inp("IN", "DINT")),  returnType = "REAL"))
        add(SclBuiltin("REAL_TO_INT",   Kind.FC, listOf(inp("IN", "REAL")),  returnType = "INT"))
        add(SclBuiltin("REAL_TO_DINT",  Kind.FC, listOf(inp("IN", "REAL")),  returnType = "DINT"))
        add(SclBuiltin("TIME_TO_DINT",  Kind.FC, listOf(inp("IN", "TIME")),  returnType = "DINT"))
        add(SclBuiltin("DINT_TO_TIME",  Kind.FC, listOf(inp("IN", "DINT")),  returnType = "TIME"))
        add(SclBuiltin("BOOL_TO_INT",   Kind.FC, listOf(inp("IN", "BOOL")),  returnType = "INT"))
        add(SclBuiltin("INT_TO_BOOL",   Kind.FC, listOf(inp("IN", "INT")),   returnType = "BOOL"))
        add(SclBuiltin("ROUND", Kind.FC, listOf(inp("IN", "REAL")), returnType = "DINT",
            description = "Round to nearest"))
        add(SclBuiltin("TRUNC", Kind.FC, listOf(inp("IN", "REAL")), returnType = "DINT",
            description = "Truncate to integer"))
        add(SclBuiltin("CEIL",  Kind.FC, listOf(inp("IN", "REAL")), returnType = "DINT",
            description = "Round up"))
        add(SclBuiltin("FLOOR", Kind.FC, listOf(inp("IN", "REAL")), returnType = "DINT",
            description = "Round down"))
        add(SclBuiltin("NORM_X",  Kind.FC, listOf(
            inp("MIN", "REAL"), inp("VALUE", "REAL"), inp("MAX", "REAL"),
        ), returnType = "REAL", description = "Normalize → 0.0..1.0"))
        add(SclBuiltin("SCALE_X", Kind.FC, listOf(
            inp("MIN", "REAL"), inp("VALUE", "REAL"), inp("MAX", "REAL"),
        ), returnType = "REAL", description = "Scale from 0.0..1.0"))

        // ── String (FC) ───────────────────────────────────────────────────────

        add(SclBuiltin("LEN",     Kind.FC, listOf(inp("IN", "STRING")), returnType = "INT",
            description = "String length"))
        add(SclBuiltin("LEFT",    Kind.FC, listOf(inp("IN", "STRING"), inp("L", "INT")),
            returnType = "STRING", description = "Left n chars"))
        add(SclBuiltin("RIGHT",   Kind.FC, listOf(inp("IN", "STRING"), inp("L", "INT")),
            returnType = "STRING", description = "Right n chars"))
        add(SclBuiltin("MID",     Kind.FC, listOf(inp("IN", "STRING"), inp("L", "INT"), inp("P", "INT")),
            returnType = "STRING", description = "Mid substring"))
        add(SclBuiltin("CONCAT",  Kind.FC, listOf(inp("IN1", "STRING"), inp("IN2", "STRING")),
            returnType = "STRING", description = "Concatenate"))
        add(SclBuiltin("FIND",    Kind.FC, listOf(inp("IN1", "STRING"), inp("IN2", "STRING")),
            returnType = "INT", description = "Find substring position"))
        add(SclBuiltin("REPLACE", Kind.FC, listOf(
            inp("IN1", "STRING"), inp("IN2", "STRING"), inp("L", "INT"), inp("P", "INT"),
        ), returnType = "STRING", description = "Replace substring"))
        add(SclBuiltin("INSERT",  Kind.FC, listOf(
            inp("IN1", "STRING"), inp("IN2", "STRING"), inp("P", "INT"),
        ), returnType = "STRING", description = "Insert substring"))
        add(SclBuiltin("DELETE",  Kind.FC, listOf(inp("IN", "STRING"), inp("L", "INT"), inp("P", "INT")),
            returnType = "STRING", description = "Delete substring"))

        // ── Seleção (FC) ──────────────────────────────────────────────────────

        add(SclBuiltin("SEL",   Kind.FC, listOf(inp("G", "BOOL"), inp("IN0", "ANY"), inp("IN1", "ANY")),
            returnType = "ANY", description = "Select by boolean"))
        add(SclBuiltin("MUX",   Kind.FC, listOf(
            inp("K", "INT"), inp("IN0", "ANY"), inp("IN1", "ANY"), inp("IN2", "ANY"),
        ), returnType = "ANY", description = "Multiplex"))
        add(SclBuiltin("LIMIT", Kind.FC, listOf(inp("MN", "ANY"), inp("IN", "ANY"), inp("MX", "ANY")),
            returnType = "ANY", description = "Clamp to [MN..MX]"))
        add(SclBuiltin("MIN",   Kind.FC, listOf(inp("IN1", "ANY"), inp("IN2", "ANY")),
            returnType = "ANY", description = "Minimum of two values"))
        add(SclBuiltin("MAX",   Kind.FC, listOf(inp("IN1", "ANY"), inp("IN2", "ANY")),
            returnType = "ANY", description = "Maximum of two values"))

        // ── Sistema (FC/FB) ───────────────────────────────────────────────────

        add(SclBuiltin("RD_SYS_T", Kind.FC, listOf(out("OUT", "DTL")),
            returnType = "INT", description = "Read system time"))
        add(SclBuiltin("WR_SYS_T", Kind.FC, listOf(inp("IN", "DTL")),
            returnType = "INT", description = "Write system time"))
        add(SclBuiltin("RUNTIME",   Kind.FC, emptyList(),
            returnType = "LREAL", s7_1500Only = true, description = "Runtime measurement"))
        add(SclBuiltin("GET_ERROR", Kind.FC, emptyList(),
            returnType = "BOOL", s7_1500Only = true, description = "Get last error flag"))
    }

    // ── Lookup por nome (case-insensitive) ────────────────────────────────────

    private val byName: Map<String, SclBuiltin> =
        catalog.associateBy { it.name.uppercase() }

    /** Localiza um builtin pelo nome (case-insensitive). Retorna null se não for builtin. */
    fun findByName(name: String): SclBuiltin? = byName[name.uppercase()]
}

// ─────────────────────────────────────────────────────────────────────────────
// Extensão PSI: extrai nome e resolve builtin de um SclCallStmt
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Retorna o último identificador antes do LPAREN em um callStmt.
 *
 * Para `TON(...)` → "TON"
 * Para `myInst.call(...)` → "call"
 * Para `#localFB(...)` → "localFB" (sem o #)
 */
internal fun SclCallStmt.callName(): String? {
    var lastName: String? = null
    var node: ASTNode? = this.node.firstChildNode
    while (node != null && node.elementType != SclTypes.LPAREN) {
        when (node.elementType) {
            SclTypes.IDENTIFIER   -> lastName = node.text
            SclTypes.LOCAL_VAR_ID -> lastName = node.text.trimStart('#')
        }
        node = node.treeNext
    }
    return lastName
}

/**
 * Resolve o [SclBuiltin] correspondente a um callStmt em duas etapas:
 *   1. Lookup direto pelo nome:   `TON(` → builtin TON
 *   2. Resolução via VAR section: `myTimer(` / `#myTimer(` → busca
 *      `myTimer : TON` no bloco pai via [SclSymbolResolver]
 */
internal fun SclCallStmt.resolveBuiltin(): SclBuiltin? {
    val name = callName() ?: return null
    return SclBuiltinFunctions.findByName(name)
        ?: SclSymbolResolver.resolveVarType(this, name)
}
