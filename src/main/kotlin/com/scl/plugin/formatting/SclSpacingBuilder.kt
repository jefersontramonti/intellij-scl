package com.scl.plugin.formatting

import com.intellij.formatting.SpacingBuilder
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclTypes

/**
 * Constrói as regras de espaçamento entre tokens SCL para o formatter.
 *
 * Nota de mapeamento (spec → token real):
 *   MUL      → MULTIPLY  (asterisco *)
 *   DIV      → DIVIDE    (barra / ), DIV é a keyword de divisão inteira
 *   LE/GE    → LEQ / GEQ
 *   AND_KW   → AND
 *   OR_KW    → OR
 *   NOT_KW   → NOT
 *   XOR_KW   → XOR
 *   MOD_KW   → MOD
 *   RANGE    → DOTDOT    (..)
 */
object SclSpacingBuilder {

    fun create(settings: CodeStyleSettings): SpacingBuilder =
        SpacingBuilder(settings, SclLanguage)

            // ── OPERADORES DE ATRIBUIÇÃO ──────────────────────────────────────
            // := → 1 espaço antes e depois
            .around(SclTypes.ASSIGN).spaces(1)
            // => (output assign) → 1 espaço antes e depois: Q => q_bDone
            .around(SclTypes.OUTPUT_ASSIGN).spaces(1)

            // ── OPERADORES DE COMPARAÇÃO ──────────────────────────────────────
            .around(SclTypes.EQ).spaces(1)
            .around(SclTypes.NEQ).spaces(1)
            .around(SclTypes.LT).spaces(1)
            .around(SclTypes.GT).spaces(1)
            .around(SclTypes.LEQ).spaces(1)
            .around(SclTypes.GEQ).spaces(1)

            // ── OPERADORES ARITMÉTICOS ────────────────────────────────────────
            .around(SclTypes.PLUS).spaces(1)
            .around(SclTypes.MINUS).spaces(1)
            .around(SclTypes.MULTIPLY).spaces(1)   // * (não confundir com DIV keyword)
            .around(SclTypes.DIVIDE).spaces(1)     // / (barra, não a keyword DIV)
            .around(SclTypes.DIV).spaces(1)        // DIV keyword (divisão inteira)
            .around(SclTypes.MOD).spaces(1)
            .around(SclTypes.POWER).spaces(1)      // ** (potência)

            // ── DECLARAÇÕES DE VARIÁVEIS ──────────────────────────────────────
            // "nomeVar : TIPO" → 1 espaço antes e depois do ":"
            .around(SclTypes.COLON).spaces(1)

            // ── PONTO-E-VÍRGULA ───────────────────────────────────────────────
            // "stmt;" → sem espaço antes do ";"
            .before(SclTypes.SEMICOLON).none()

            // ── VÍRGULAS ──────────────────────────────────────────────────────
            // "TON(IN:=x, PT:=T#5S)" → sem espaço antes, 1 depois
            .before(SclTypes.COMMA).none()
            .after(SclTypes.COMMA).spaces(1)

            // ── PARÊNTESES ────────────────────────────────────────────────────
            // "ABS(x)" → sem espaço após "(" e antes de ")"
            .after(SclTypes.LPAREN).none()
            .before(SclTypes.RPAREN).none()

            // ── COLCHETES ─────────────────────────────────────────────────────
            .after(SclTypes.LBRACKET).none()
            .before(SclTypes.RBRACKET).none()

            // ── OPERADORES LÓGICOS ────────────────────────────────────────────
            // AND, OR, XOR → 1 espaço; NOT → 1 espaço depois (unário)
            .around(SclTypes.AND).spaces(1)
            .around(SclTypes.AMPERSAND).spaces(1)  // & alternativo ao AND
            .around(SclTypes.OR).spaces(1)
            .around(SclTypes.XOR).spaces(1)
            .before(SclTypes.NOT).spaces(1)
            .after(SclTypes.NOT).spaces(1)

            // ── PONTO (acesso de membro) ──────────────────────────────────────
            // "timer.Q" → sem espaços em volta do "."
            .around(SclTypes.DOT).none()

            // ── RANGE (..) ────────────────────────────────────────────────────
            // "ARRAY[1..10]" → sem espaços em volta de ".."
            .around(SclTypes.DOTDOT).none()

            // ═════════════════════════════════════════════════════════════════
            // QUEBRAS DE LINHA ESTRUTURAIS — lineBreakInCode() força newline
            // obrigatório entre elementos independente do layout de entrada.
            // Sem estas regras, o formatter mantém tudo na mesma linha se o
            // usuário escreveu assim (ou colapsa em alguns casos).
            // ═════════════════════════════════════════════════════════════════

            // ── TYPE / STRUCT (UDT) ───────────────────────────────────────────
            // Estrutura PSI (blockName é private → name é filho direto de typeDef):
            //   typeDecl  → TYPE typeDef* END_TYPE
            //   typeDef   → (QUOTED_IDENTIFIER | IDENTIFIER) structDecl
            //   structDecl→ STRUCT structField* END_STRUCT
            //
            // Resultado esperado:
            //   TYPE "Nome"           ← nível 0
            //       STRUCT            ← nível 1 (line break antes do STRUCT_DECL)
            //           campo : T;    ← nível 2 (line break após STRUCT, entre fields)
            //       END_STRUCT        ← nível 1 (line break antes do END_STRUCT)
            //   END_TYPE              ← nível 0 (line break antes do END_TYPE)
            //
            // .betweenInside é o mais preciso: casa só quando child1=name e
            // child2=STRUCT_DECL DENTRO de typeDef — preserva o formato de alias
            // (TYPE "MyInt" : INT; END_TYPE) em uma única linha.
            .betweenInside(SclTypes.QUOTED_IDENTIFIER, SclTypes.STRUCT_DECL, SclTypes.TYPE_DEF).lineBreakInCode()
            .betweenInside(SclTypes.IDENTIFIER, SclTypes.STRUCT_DECL, SclTypes.TYPE_DEF).lineBreakInCode()
            .afterInside(SclTypes.STRUCT, SclTypes.STRUCT_DECL).lineBreakInCode()
            .beforeInside(SclTypes.END_STRUCT, SclTypes.STRUCT_DECL).lineBreakInCode()
            .between(SclTypes.STRUCT_FIELD, SclTypes.STRUCT_FIELD).lineBreakInCode()
            .beforeInside(SclTypes.END_TYPE, SclTypes.TYPE_DECL).lineBreakInCode()
            // Line break entre typeDefs consecutivos (múltiplos UDTs em um único
            // TYPE...END_TYPE, forma SCLV4 clássica).
            .afterInside(SclTypes.TYPE_DEF, SclTypes.TYPE_DECL).lineBreakInCode()

            // ── Cabeçalho de bloco / atributos TIA Portal { S7_... } ──────────
            // FUNCTION_BLOCK "Nome"
            // { S7_Optimized_Access := 'TRUE' }   ← atributos em linha própria
            .before(SclTypes.LBRACE).lineBreakInCode()
            .after(SclTypes.RBRACE).lineBreakInCode()

            // ── Seções VAR / CONST ────────────────────────────────────────────
            // Seção em linha própria; primeiro decl após a keyword; decls
            // separados por newline; END_VAR/END_CONST também em linha própria.
            .before(SclTypes.VAR_SECTION).lineBreakInCode()
            .before(SclTypes.CONST_SECTION).lineBreakInCode()
            .after(SclTypes.VAR).lineBreakInCode()
            .after(SclTypes.VAR_INPUT).lineBreakInCode()
            .after(SclTypes.VAR_OUTPUT).lineBreakInCode()
            .after(SclTypes.VAR_IN_OUT).lineBreakInCode()
            .after(SclTypes.VAR_STATIC).lineBreakInCode()
            .after(SclTypes.VAR_TEMP).lineBreakInCode()
            .after(SclTypes.VAR_CONSTANT).lineBreakInCode()
            .after(SclTypes.CONST).lineBreakInCode()
            .before(SclTypes.END_VAR).lineBreakInCode()
            .before(SclTypes.END_CONST).lineBreakInCode()
            .between(SclTypes.VAR_DECL, SclTypes.VAR_DECL).lineBreakInCode()
            .between(SclTypes.CONST_DECL, SclTypes.CONST_DECL).lineBreakInCode()

            // ── BEGIN ... END_* de FB/FC/OB/DB ────────────────────────────────
            .before(SclTypes.BEGIN).lineBreakInCode()
            .after(SclTypes.BEGIN).lineBreakInCode()
            .before(SclTypes.END_FUNCTION_BLOCK).lineBreakInCode()
            .before(SclTypes.END_FUNCTION).lineBreakInCode()
            .before(SclTypes.END_ORGANIZATION_BLOCK).lineBreakInCode()
            .before(SclTypes.END_DATA_BLOCK).lineBreakInCode()

            // ── Controle de fluxo: corpo em linhas próprias ───────────────────
            // IF cond THEN\n body\n ELSIF ...\n ELSE ...\n END_IF
            .after(SclTypes.THEN).lineBreakInCode()
            .after(SclTypes.DO).lineBreakInCode()
            .after(SclTypes.OF).lineBreakInCode()
            .before(SclTypes.ELSIF_CLAUSE).lineBreakInCode()
            .before(SclTypes.ELSE_CLAUSE).lineBreakInCode()
            .before(SclTypes.CASE_ALT).lineBreakInCode()
            .before(SclTypes.CASE_ELSE_CLAUSE).lineBreakInCode()
            .before(SclTypes.END_IF).lineBreakInCode()
            .before(SclTypes.END_FOR).lineBreakInCode()
            .before(SclTypes.END_WHILE).lineBreakInCode()
            .before(SclTypes.END_REPEAT).lineBreakInCode()
            .before(SclTypes.END_CASE).lineBreakInCode()
            .before(SclTypes.END_REGION).lineBreakInCode()
            .before(SclTypes.UNTIL).lineBreakInCode()

            // ── Statements em linhas próprias dentro do statementList ─────────
            // SEMICOLON aqui está DIRETAMENTE em statementList (statement é
            // regra private, então assignStmt/callStmt e o SEMICOLON final
            // aparecem como filhos diretos). afterInside() escopa o newline
            // apenas ao contexto statementList, não dentro de varDecl.
            .afterInside(SclTypes.SEMICOLON, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.IF_STATEMENT, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.FOR_STATEMENT, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.WHILE_STATEMENT, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.REPEAT_STATEMENT, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.CASE_STATEMENT, SclTypes.STATEMENT_LIST).lineBreakInCode()
            .afterInside(SclTypes.REGION_STMT, SclTypes.STATEMENT_LIST).lineBreakInCode()
}
