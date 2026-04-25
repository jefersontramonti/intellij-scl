package com.scl.plugin.formatting

import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.SpacingBuilder
import com.intellij.formatting.Wrap
import com.intellij.formatting.WrapType
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.tree.TokenSet
import com.scl.plugin.formatting.SclCodeStyleSettings
import com.scl.plugin.psi.SclTypes

/**
 * Bloco de formatação SCL — define indentação e delega espaçamento ao SpacingBuilder.
 *
 * Hierarquia PSI real (Grammar-Kit gerado):
 *   functionBlockDecl → FUNCTION_BLOCK name varSection* (BEGIN statementList)? END_FUNCTION_BLOCK
 *   varSection        → varKeyword varDecl* END_VAR          (um nó genérico para todas as seções)
 *   varDecl           → IDENTIFIER COLON typeRef (ASSIGN expr)? SEMICOLON
 *   statementList     → statement*                           (container de corpo usado em IF/FOR/WHILE/etc.)
 *   ifStatement       → IF expr THEN statementList elsifClause* elseClause? END_IF
 *   elsifClause       → ELSIF expr THEN statementList
 *   elseClause        → ELSE statementList
 *   forStatement      → FOR ... DO statementList END_FOR
 *   whileStatement    → WHILE expr DO statementList END_WHILE
 *   repeatStatement   → REPEAT statementList UNTIL expr ;? END_REPEAT
 *   caseStatement     → CASE expr OF caseAlt* caseElseClause? END_CASE
 *   caseAlt           → caseLabel* COLON statementList
 *   caseElseClause    → ELSE statementList
 *   structDecl        → STRUCT structField* END_STRUCT
 *   constSection      → CONST constDecl* END_CONST
 *
 * @param colonAlignment  Alinhamento compartilhado para o ":" de declarações na mesma
 *                        seção VAR. Passado do pai (VAR_SECTION) para os filhos (VAR_DECL).
 */
class SclBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    private val settings: CodeStyleSettings,
    private val spacingBuilder: SpacingBuilder,
    private val indent: Indent,
    private val colonAlignment: Alignment? = null,
) : AbstractBlock(node, wrap, alignment) {

    // ── Constrói os blocos filhos recursivamente ──────────────────────────────
    override fun buildChildren(): List<Block> {
        // runCatching: getCustomSettings() lança RuntimeException se o
        // langCodeStyleSettingsProvider ainda não foi inicializado pelo framework.
        // Fallback ao default (true) garante que o formatter nunca crasha.
        val alignColons: Boolean = runCatching {
            settings.getCustomSettings(SclCodeStyleSettings::class.java).ALIGN_VARIABLE_COLONS
        }.getOrDefault(true)

        // Alinhamento de ":" compartilhado entre todos os varDecl da mesma seção
        val sharedColonAlignment: Alignment? =
            if (myNode.elementType == SclTypes.VAR_SECTION && alignColons)
                Alignment.createAlignment()
            else null

        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode

        while (child != null) {
            if (child.elementType != TokenType.WHITE_SPACE) {
                val childIndent = computeChildIndent(child)

                // Passa o alinhamento de ":" para os varDecl filhos de varSection
                val passedColonAlignment =
                    if (myNode.elementType == SclTypes.VAR_SECTION &&
                        child.elementType == SclTypes.VAR_DECL)
                        sharedColonAlignment
                    else null

                // Dentro de varDecl, aplica o alinhamento ao próprio token COLON
                val childAlignment: Alignment? = when {
                    myNode.elementType == SclTypes.VAR_DECL &&
                    child.elementType == SclTypes.COLON -> colonAlignment
                    else -> null
                }

                blocks.add(
                    SclBlock(
                        node           = child,
                        wrap           = Wrap.createWrap(WrapType.NONE, false),
                        alignment      = childAlignment,
                        settings       = settings,
                        spacingBuilder = spacingBuilder,
                        indent         = childIndent,
                        colonAlignment = passedColonAlignment,
                    )
                )
            }
            child = child.treeNext
        }
        return blocks
    }

    override fun getIndent(): Indent = indent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? =
        spacingBuilder.getSpacing(this, child1, child2)

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    // ── Indentação ao pressionar Enter (novo filho) ───────────────────────────
    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val normalIndent = ChildAttributes(Indent.getNormalIndent(), null)
        val noneIndent   = ChildAttributes(Indent.getNoneIndent(), null)

        return when (myNode.elementType) {
            // Declarações VAR → nova declaração indentada
            SclTypes.VAR_SECTION,
            SclTypes.CONST_SECTION      -> normalIndent

            // Blocos principais → novo statement indentado (após BEGIN)
            SclTypes.FUNCTION_BLOCK_DECL,
            SclTypes.FUNCTION_DECL,
            SclTypes.ORG_BLOCK_DECL     -> normalIndent

            // IF/ELSIF/ELSE → novo statement indentado
            SclTypes.IF_STATEMENT,
            SclTypes.ELSIF_CLAUSE,
            SclTypes.ELSE_CLAUSE        -> normalIndent

            // Loops
            SclTypes.FOR_STATEMENT,
            SclTypes.WHILE_STATEMENT,
            SclTypes.REPEAT_STATEMENT   -> normalIndent

            // CASE branches
            SclTypes.CASE_STATEMENT,
            SclTypes.CASE_ALT,
            SclTypes.CASE_ELSE_CLAUSE   -> normalIndent

            // STRUCT
            SclTypes.STRUCT_DECL        -> normalIndent

            // REGION
            SclTypes.REGION_STMT        -> normalIndent

            else -> noneIndent
        }
    }

    // ── Bloco incompleto? (afeta qual pai recebe o indent do Enter) ───────────
    override fun isIncomplete(): Boolean {
        val lastChild = myNode.lastChildNode ?: return false
        return when (myNode.elementType) {
            SclTypes.IF_STATEMENT      -> lastChild.elementType != SclTypes.END_IF
            SclTypes.FOR_STATEMENT     -> lastChild.elementType != SclTypes.END_FOR
            SclTypes.WHILE_STATEMENT   -> lastChild.elementType != SclTypes.END_WHILE
            SclTypes.CASE_STATEMENT    -> lastChild.elementType != SclTypes.END_CASE
            SclTypes.REPEAT_STATEMENT  -> lastChild.elementType != SclTypes.END_REPEAT
            SclTypes.VAR_SECTION       -> lastChild.elementType != SclTypes.END_VAR
            SclTypes.CONST_SECTION     -> lastChild.elementType != SclTypes.END_CONST
            SclTypes.STRUCT_DECL       -> lastChild.elementType != SclTypes.END_STRUCT
            else -> false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LÓGICA DE INDENTAÇÃO — computeChildIndent
    //
    // O Indent calculado aqui é RELATIVO ao próprio bloco pai.
    // O formatter combina os indents de todos os ancestrais para obter o
    // offset absoluto na linha.
    // ─────────────────────────────────────────────────────────────────────────
    private fun computeChildIndent(child: ASTNode): Indent {
        val parentType = myNode.elementType
        val childType  = child.elementType

        // Tokens de fechamento de bloco sempre no mesmo nível do bloco pai
        if (childType in CLOSING_KEYWORDS) return Indent.getNoneIndent()

        // DUMMY_BLOCK = nó de error-recovery do parser (Grammar-Kit não conseguiu
        // parsear a construção completamente). O formatter não conhece a estrutura
        // real do código dentro dele, então a decisão segura é não modificar o
        // indent — preservar o espaçamento original do arquivo.
        val parentStr = parentType.toString()
        val childStr  = childType.toString()
        if (childStr  == "DUMMY_BLOCK") return Indent.getNoneIndent()
        if (parentStr == "DUMMY_BLOCK") return Indent.getNoneIndent()

        return when {
            // ── VAR_SECTION: varDecl filhos recebem +4 ───────────────────────
            parentType == SclTypes.VAR_SECTION && childType == SclTypes.VAR_DECL ->
                Indent.getNormalIndent()

            // ── CONST_SECTION: constDecl recebem +4 ──────────────────────────
            parentType == SclTypes.CONST_SECTION && childType == SclTypes.CONST_DECL ->
                Indent.getNormalIndent()

            // ── Blocos principais: statementList recebe +4 ────────────────────
            // BEGIN fica sem indent; statementList (corpo) fica +4
            parentType in BLOCK_DECL_TYPES && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── Blocos principais: seções VAR/CONST recebem +4 ────────────────
            // FUNCTION_BLOCK "Nome"
            //     VAR_INPUT           ← nível 1 (indent +4)
            //         i : Bool;       ← nível 2 (VAR_SECTION +4 + VAR_DECL +4)
            //     END_VAR             ← nível 1 (CLOSING herda VAR_SECTION)
            // BEGIN                   ← nível 0 (sem indent no BLOCK_DECL)
            //     stmt;               ← nível 1 (STATEMENT_LIST +4)
            // END_FUNCTION_BLOCK      ← nível 0 (CLOSING)
            parentType in BLOCK_DECL_TYPES &&
                (childType == SclTypes.VAR_SECTION || childType == SclTypes.CONST_SECTION) ->
                Indent.getNormalIndent()

            // ── IF_STATEMENT: statementList direto (corpo after THEN) → +4 ────
            // elsifClause e elseClause → NONE (mesma linha que IF)
            parentType == SclTypes.IF_STATEMENT && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()
            parentType == SclTypes.IF_STATEMENT &&
                (childType == SclTypes.ELSIF_CLAUSE || childType == SclTypes.ELSE_CLAUSE) ->
                Indent.getNoneIndent()

            // ── ELSIF_CLAUSE: statementList → +4 ─────────────────────────────
            parentType == SclTypes.ELSIF_CLAUSE && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── ELSE_CLAUSE: statementList → +4 ──────────────────────────────
            parentType == SclTypes.ELSE_CLAUSE && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── FOR/WHILE: statementList (corpo do loop) → +4 ────────────────
            parentType in LOOP_TYPES && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── REPEAT: statementList → +4 (UNTIL fica sem indent) ───────────
            parentType == SclTypes.REPEAT_STATEMENT && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── CASE_STATEMENT: caseAlt e caseElseClause → +4 ────────────────
            parentType == SclTypes.CASE_STATEMENT &&
                (childType == SclTypes.CASE_ALT || childType == SclTypes.CASE_ELSE_CLAUSE) ->
                Indent.getNormalIndent()

            // ── CASE_ALT: statementList (código da branch) → +4 ─────────────
            // O label (caseLabel) e o COLON ficam sem indent extra (relativo ao caseAlt)
            parentType == SclTypes.CASE_ALT && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── CASE_ELSE_CLAUSE: statementList → +4 ─────────────────────────
            parentType == SclTypes.CASE_ELSE_CLAUSE && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── STRUCT_DECL: structField filhos → +4 ─────────────────────────
            parentType == SclTypes.STRUCT_DECL && childType == SclTypes.STRUCT_FIELD ->
                Indent.getNormalIndent()

            // ── REGION_STMT: statementList → +4 ──────────────────────────────
            parentType == SclTypes.REGION_STMT && childType == SclTypes.STATEMENT_LIST ->
                Indent.getNormalIndent()

            // ── TYPE_DECL → TYPE_DEF: SEM indent extra no typeDef ────────────
            // typeDef começa com o nome que fica inline com TYPE:
            //   TYPE "UDT_Name"        ← TYPE + nome na mesma linha
            //       STRUCT             ← indent vem do TYPE_DEF → STRUCT_DECL abaixo
            //   END_TYPE
            // Aplicar NormalIndent no TYPE_DEF só engana o cálculo: como typeDef
            // fica inline, esse indent nunca se propaga aos filhos em new line
            // (o indent só conta a partir do ancestral que inicia a linha).
            parentType == SclTypes.TYPE_DECL && childType == SclTypes.TYPE_DEF ->
                Indent.getNoneIndent()

            // ── TYPE_DEF → STRUCT_DECL: +4 (nível 1) ─────────────────────────
            // STRUCT_DECL fica em new line (line break forçado pelo SpacingBuilder).
            // NormalIndent aqui posiciona STRUCT na coluna 4. Os structFields
            // recebem +4 adicional via STRUCT_DECL → STRUCT_FIELD → coluna 8.
            parentType == SclTypes.TYPE_DEF && childType == SclTypes.STRUCT_DECL ->
                Indent.getNormalIndent()

            // ── CALL_STMT: argList → +4 (o bloco de argumentos, não RPAREN) ──
            // RPAREN já é capturado por CLOSING_KEYWORDS acima, mas RPAREN não
            // está lá — garantir que argList receba NORMAL e RPAREN receba NONE
            parentType == SclTypes.CALL_STMT && childType == SclTypes.ARG_LIST ->
                Indent.getNormalIndent()
            parentType == SclTypes.CALL_STMT && childType == SclTypes.RPAREN ->
                Indent.getNoneIndent()

            // ── ARG_LIST: cada ARGUMENT filho → sem indent adicional ────────
            // ARG_LIST já recebe +4 do CALL_STMT pai; ARGUMENT fica em +0
            // relativo ao ARG_LIST → +4 absoluto. Exatamente o que TIA Portal espera.
            parentType == SclTypes.ARG_LIST && childType == SclTypes.ARGUMENT ->
                Indent.getNoneIndent()

            // Default: sem indentação extra
            else -> Indent.getNoneIndent()
        }
    }

    companion object {

        /** Tipos de declaração de bloco raiz (FB/FC/OB). */
        val BLOCK_DECL_TYPES: TokenSet = TokenSet.create(
            SclTypes.FUNCTION_BLOCK_DECL,
            SclTypes.FUNCTION_DECL,
            SclTypes.ORG_BLOCK_DECL,
        )

        /** Tipos de loops (FOR, WHILE — REPEAT é tratado separadamente porque
         *  usa UNTIL no meio, não END_FOR no final). */
        val LOOP_TYPES: TokenSet = TokenSet.create(
            SclTypes.FOR_STATEMENT,
            SclTypes.WHILE_STATEMENT,
        )

        /** Keywords de fechamento que devem sempre ficar no mesmo nível do bloco pai. */
        val CLOSING_KEYWORDS: TokenSet = TokenSet.create(
            SclTypes.END_VAR,
            SclTypes.END_CONST,
            SclTypes.END_LABEL,
            SclTypes.END_FUNCTION_BLOCK,
            SclTypes.END_FUNCTION,
            SclTypes.END_ORGANIZATION_BLOCK,
            SclTypes.END_DATA_BLOCK,
            SclTypes.END_IF,
            SclTypes.END_FOR,
            SclTypes.END_WHILE,
            SclTypes.END_REPEAT,
            SclTypes.END_CASE,
            SclTypes.END_STRUCT,
            SclTypes.END_TYPE,
            SclTypes.END_REGION,
            SclTypes.UNTIL,   // REPEAT … UNTIL fecha o corpo do REPEAT
        )
    }
}
