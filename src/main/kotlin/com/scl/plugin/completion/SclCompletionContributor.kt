package com.scl.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import com.scl.plugin.language.SclFileType
import com.scl.plugin.language.SclLanguage
import com.scl.plugin.psi.SclCallStmt
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarSection

/**
 * Code completion para SCL — Fase 3 + Fase 4A + variáveis locais.
 *
 * Quatro providers registrados:
 *
 *   1. [SclKeywordCompletionProvider] — palavras-chave, tipos, constantes,
 *      operadores e builtins (com template de chamada ao completar).
 *
 *   2. [SclParamNameCompletionProvider] — dentro de `BUILTIN(` ou `"FB_DB"(`
 *      sugere scaffold completo dos parâmetros (em bloco) ou nomes individuais.
 *
 *   3. [SclVariableCompletionProvider] — variáveis declaradas nas seções VAR
 *      do bloco atual. Suporta notação TIA Portal (#var) e SCL clássico (var).
 *
 *   4. [SclProjectNamesCompletionProvider] — nomes de FUNCTION_BLOCK e FUNCTION
 *      declarados em qualquer arquivo .scl do projeto aberto (cross-file).
 *
 * DumbAware: todos funcionam durante indexação do projeto.
 */
class SclCompletionContributor : CompletionContributor(), DumbAware {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclKeywordCompletionProvider,
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclParamNameCompletionProvider,
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclVariableCompletionProvider,
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java).withLanguage(SclLanguage),
            SclProjectNamesCompletionProvider,
        )
        // Provider 5 — acesso a membros via ponto: instancia.Q, timer.ET, etc.
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement::class.java)
                .withLanguage(SclLanguage)
                .afterLeaf(PlatformPatterns.psiElement(SclTypes.DOT)),
            SclMemberAccessCompletionProvider,
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

    // ── Estruturas de bloco ───────────────────────────────────────────────────
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

    // ── Tipos elementares ─────────────────────────────────────────────────────
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

    // ── FB builtins Siemens — com template de scaffold ────────────────────────
    private val TYPES_BLOCKS: List<LookupElement> = buildList {
        for (builtin in SclBuiltinFunctions.catalog.filter { it.kind == Kind.FB }) {
            val suffix = builtin.parameters
                .filter { it.direction != Direction.OUTPUT }
                .joinToString(prefix = "(", postfix = ")") { "${it.name}:=" }
            add(
                LookupElementBuilder.create(builtin.name as Any)
                    .bold()
                    .withTypeText(builtin.kindLabel)
                    .withTailText(suffix, true)
                    .withInsertHandler(builtinInsertHandler(builtin))
            )
        }
        add(tp("SR",       "FB type", "(S1:=, R:=)"))
        add(tp("RS",       "FB type", "(S:=, R1:=)"))
        add(tp("TIMER",    "param type"))
        add(tp("COUNTER",  "param type"))
        add(tp("POINTER",  "param type"))
        add(tp("ANY",      "param type"))
        add(tp("BLOCK_FB", "param type"))
        add(tp("BLOCK_FC", "param type"))
        add(tp("BLOCK_DB", "param type"))
    }

    // ── FC builtins Siemens — com template de scaffold ────────────────────────
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

    // ── Constantes / flags ────────────────────────────────────────────────────
    private val CONSTANTS = listOf(
        cn("TRUE"),
        cn("FALSE"),
        cn("NIL",  " // null pointer"),
        cn("EN",   " // enable flag"),
        cn("ENO",  " // enable output flag"),
        cn("OK",   " // operation status"),
    )

    // ── Operadores ────────────────────────────────────────────────────────────
    private val OPERATORS = listOf(
        kw("AND"), kw("OR"), kw("XOR"), kw("NOT"),
        kw("MOD"), kw("DIV"),
    )

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
// Provider 2 — scaffold e nomes de parâmetros dentro de chamada FB/FC
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Quando o cursor está dentro de `BUILTIN(` ou `"FB_DB"(`, Ctrl+Space oferece:
 *
 *   1. Item de SCAFFOLD (prioridade 200): insere TODOS os parâmetros de uma vez,
 *      multi-linha com operadores alinhados e placeholders vazios navegáveis.
 *
 *   2. Itens individuais (prioridade 100): um por parâmetro, para inserção avulsa.
 *
 * Fonte de parâmetros:
 *   - Builtins (TON, CTU, …) → [SclBuiltinFunctions]
 *   - FBs de projeto ("FB_StackLight_DB") → [SclProjectFbResolver]
 *   - Instâncias de VAR section (myTimer : TON) → [SclSymbolResolver]
 */
private object SclParamNameCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (isInsideComment(parameters.position)) return
        if (isInsideString(parameters.position))  return

        val callStmt = PsiTreeUtil.getParentOfType(
            parameters.position, SclCallStmt::class.java
        ) ?: return

        val lparen = callStmt.node.findChildByType(SclTypes.LPAREN) ?: return
        if (parameters.position.textRange.startOffset <= lparen.startOffset) return

        // Resolve parâmetros: builtin direto → VAR section → projeto
        val params = resolveParams(callStmt, parameters) ?: return

        val r = result.caseInsensitive()

        // ── Item 1: scaffold completo ─────────────────────────────────────────
        val scaffoldItem = LookupElementBuilder
            .create("\u0000scaffold")           // \u0000 → nunca digitado → sempre visível
            .withPresentableText("↵ scaffold all params")
            .withTypeText("${params.size} param(s)")
            .bold()
            .withInsertHandler(scaffoldInsertHandler(params))
        r.addElement(PrioritizedLookupElement.withPriority(scaffoldItem, 200.0))

        // ── Itens 2…N: parâmetro individual ──────────────────────────────────
        for (param in params) {
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

    /**
     * Tenta resolver os parâmetros em três passos:
     *   1. Lookup direto no catálogo builtin (TON, CTU, …)
     *   2. Resolução via VAR section do bloco pai (myTimer : TON)
     *   3. Busca no projeto SCL (FB_StackLight_DB → FB_StackLight)
     */
    private fun resolveParams(
        callStmt: SclCallStmt,
        parameters: CompletionParameters,
    ): List<SclParameter>? {
        val builtin = callStmt.resolveBuiltin()
        if (builtin != null) return builtin.parameters

        val name = callStmt.callName() ?: return null
        return SclProjectFbResolver.findParameters(parameters.position.project, name)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider 3 — variáveis declaradas no bloco atual (VAR_INPUT, VAR_OUTPUT, …)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sugere variáveis declaradas nas seções VAR do bloco pai (FB/FC/OB).
 *
 * Exemplos:
 *   - `i_StartCmd` declarada em VAR_INPUT → aparece como "i_StartCmd" [INPUT : BOOL]
 *   - `s_FillTimer` declarada em VAR_STATIC → aparece como "s_FillTimer" [STATIC : TON]
 *
 * Não dispara dentro de seções VAR (ao declarar uma variável nova).
 * Prioridade 150 → aparece acima de keywords (0) mas abaixo do scaffold (200).
 */
private object SclVariableCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val pos = parameters.position
        if (isInsideComment(pos)) return
        if (isInsideString(pos))  return

        // Suprime dentro de seções VAR (ao declarar uma variável nova).
        val origPos = parameters.originalPosition
        if (origPos != null && isInsideVarSection(origPos)) return

        // ── Prefixo e estilo de notação (#var = TIA Portal, var = SCL clássico) ──
        val rawPrefix = pos.text.removeSuffix(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)
        val isHashStyle = rawPrefix.startsWith("#")
        val r = result.withPrefixMatcher(rawPrefix).caseInsensitive()

        // ── Escopo: bloco pai (FB/FC/OB) do cursor no arquivo original ───────────
        // Usa originalPosition para encontrar o bloco no PSI limpo.
        // Fallback: arquivo inteiro (para arquivos com um único bloco).
        val scanRoot: PsiElement = run {
            val elem = origPos
                ?: parameters.originalFile.findElementAt(
                    parameters.offset.coerceAtMost(parameters.originalFile.textLength - 1)
                )
            elem?.let {
                PsiTreeUtil.getParentOfType(
                    it,
                    SclFunctionBlockDecl::class.java,
                    SclFunctionDecl::class.java,
                    SclOrgBlockDecl::class.java,
                )
            } ?: parameters.originalFile
        }

        val sections = PsiTreeUtil.findChildrenOfType(scanRoot, SclVarSection::class.java)
        if (sections.isEmpty()) return

        for (section in sections) {
            val sectionLabel = sectionKindLabel(section)
            val icon         = sectionIcon(section)
            val priority     = sectionPriority(section)
            for (decl in section.varDeclList) {
                val baseName = decl.node.firstChildNode
                    ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
                    ?.text ?: continue
                val typeTxt = decl.typeRef?.text?.trim() ?: ""
                val completionName = if (isHashStyle) "#$baseName" else baseName

                val element = LookupElementBuilder
                    .create(completionName as Any)
                    .bold()
                    .withIcon(icon)
                    .withTypeText(typeTxt)
                    .withTailText("  [$sectionLabel]", true)

                r.addElement(PrioritizedLookupElement.withPriority(element, priority))
            }
        }
    }

    /** Rótulo legível da seção a partir do token inicial. */
    private fun sectionKindLabel(section: SclVarSection): String =
        when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT    -> "INPUT"
            SclTypes.VAR_OUTPUT   -> "OUTPUT"
            SclTypes.VAR_IN_OUT   -> "IN_OUT"
            SclTypes.VAR_STATIC   -> "STATIC"
            SclTypes.VAR_TEMP     -> "TEMP"
            SclTypes.VAR_CONSTANT -> "CONST"
            else                  -> "VAR"
        }

    private fun sectionIcon(section: SclVarSection) =
        when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT    -> AllIcons.Nodes.Parameter
            SclTypes.VAR_OUTPUT   -> AllIcons.Nodes.Property
            SclTypes.VAR_IN_OUT   -> AllIcons.Nodes.PropertyRead
            SclTypes.VAR_STATIC   -> AllIcons.Nodes.Field
            SclTypes.VAR_TEMP     -> AllIcons.Nodes.Variable
            SclTypes.VAR_CONSTANT -> AllIcons.Nodes.Constant
            else                  -> AllIcons.Nodes.Variable
        }

    /** Prioridades por seção: INPUT>OUTPUT>IN_OUT>STATIC>TEMP>CONST>VAR */
    private fun sectionPriority(section: SclVarSection): Double =
        when (section.node.firstChildNode?.elementType) {
            SclTypes.VAR_INPUT    -> 100.0
            SclTypes.VAR_OUTPUT   -> 98.0
            SclTypes.VAR_IN_OUT   -> 96.0
            SclTypes.VAR_STATIC   -> 94.0
            SclTypes.VAR_TEMP     -> 92.0
            SclTypes.VAR_CONSTANT -> 90.0
            else                  -> 88.0
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// InsertHandler: scaffold ao selecionar builtin do catálogo (fora de VAR)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Ao completar um builtin fora de VAR section, insere scaffold multi-linha:
 *
 *   TON → TON(
 *       IN := ,
 *       PT := ,
 *       Q  => ,
 *       ET =>
 *   );$END$
 *
 * Operadores alinhados pela coluna do nome mais longo.
 * Placeholders vazio navegáveis com TAB.
 * Inferência de valores: DESATIVADA (placeholders sempre vazios).
 */
private fun builtinInsertHandler(builtin: SclBuiltin): InsertHandler<LookupElement> =
    InsertHandler { ctx, _ ->
        val elementAtStart = ctx.file.findElementAt(ctx.startOffset)

        // Não insere template em seções VAR (declaração de tipo)
        if (isInsideVarSection(elementAtStart)) return@InsertHandler

        // Não insere template se já estamos DENTRO do arglist de outra chamada:
        // ex.: s_StartingTimer( → usuário seleciona TON da lista → não duplicar como TON(…)
        val enclosingCall = PsiTreeUtil.getParentOfType(elementAtStart, SclCallStmt::class.java)
        if (enclosingCall != null) {
            val lp = enclosingCall.node.findChildByType(SclTypes.LPAREN)
            if (lp != null && ctx.startOffset > lp.startOffset) return@InsertHandler
        }

        val doc    = ctx.editor.document
        val offset = ctx.tailOffset

        // Se `(` já foi digitado logo após, não duplica
        if (offset < doc.textLength && doc.charsSequence[offset] == '(') return@InsertHandler

        if (builtin.parameters.isEmpty()) {
            doc.insertString(offset, "();")
            ctx.editor.caretModel.moveToOffset(offset + 2)
            return@InsertHandler
        }

        val mgr      = TemplateManager.getInstance(ctx.project)
        val template = mgr.createTemplate("", "scl", buildScaffoldTemplate(builtin.parameters))
        template.isToReformat = false
        mgr.startTemplate(ctx.editor, template)
    }

// ─────────────────────────────────────────────────────────────────────────────
// InsertHandler: scaffold ao selecionar "↵ scaffold all params" dentro de `(`
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Seleciona o item de scaffold dentro de um arglist existente:
 *   1. Apaga o texto inserido pelo completion (o texto "\u0000scaffold")
 *   2. Move o caret de volta à posição original
 *   3. Inicia o template com os parâmetros formatados
 *
 * O template começa com `\n` → o `(` do caller já está no documento.
 */
private fun scaffoldInsertHandler(params: List<SclParameter>): InsertHandler<LookupElement> =
    InsertHandler { ctx, _ ->
        // Remove o texto inserido pelo completion engine
        ctx.document.deleteString(ctx.startOffset, ctx.tailOffset)
        ctx.editor.caretModel.moveToOffset(ctx.startOffset)

        val body = buildString {
            val maxLen = params.maxOf { it.name.length }
            append("\n")
            for ((i, param) in params.withIndex()) {
                val op      = if (param.direction == Direction.OUTPUT) "=>" else ":="
                val padding = " ".repeat(maxLen - param.name.length)
                append("    ${param.name}$padding $op \$${param.name}\$")
                if (i < params.size - 1) append(",")
                append("\n")
            }
            append(");\$END\$")
        }

        val mgr      = TemplateManager.getInstance(ctx.project)
        val template = mgr.createTemplate("", "scl", body)
        template.isToReformat = false
        mgr.startTemplate(ctx.editor, template)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Scaffold template builder — usado por ambos os handlers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gera o texto do live template para o scaffold completo de um FB/FC.
 *
 * Formato (exemplo TON, 4 params):
 *   (
 *       IN := $IN$,
 *       PT := $PT$,
 *       Q  => $Q$,
 *       ET => $ET$
 *   );$END$
 *
 * Regras:
 *   - INPUT / INOUT → `:=`  |  OUTPUT → `=>`
 *   - Nomes alinhados pela coluna do nome mais longo
 *   - Vírgula em todas as linhas exceto a última
 *   - Sem valores — placeholders sempre vazios
 */
private fun buildScaffoldTemplate(params: List<SclParameter>): String = buildString {
    val maxLen = params.maxOf { it.name.length }
    append("(\n")
    for ((i, param) in params.withIndex()) {
        val op      = if (param.direction == Direction.OUTPUT) "=>" else ":="
        val padding = " ".repeat(maxLen - param.name.length)
        append("    ${param.name}$padding $op \$${param.name}\$")
        if (i < params.size - 1) append(",")
        append("\n")
    }
    append(");\$END\$")
}

// ─────────────────────────────────────────────────────────────────────────────
// Guardas de contexto
// ─────────────────────────────────────────────────────────────────────────────

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

internal fun isInsideString(element: PsiElement): Boolean {
    var current: PsiElement? = element
    while (current != null) {
        val type = current.node?.elementType
        if (type == SclTypes.STRING_LITERAL || type == SclTypes.QUOTED_IDENTIFIER) return true
        current = current.parent
    }
    return false
}

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
// Factories
// ─────────────────────────────────────────────────────────────────────────────

private fun kw(text: String, tail: String? = null) = elem(text, "keyword", tail)
private fun tp(text: String, typeText: String = "type", tail: String? = null) =
    elem(text, typeText, tail)
private fun cn(text: String, tail: String? = null) = elem(text, "constant", tail)

private fun elem(text: String, typeText: String, tail: String?): LookupElementBuilder {
    var e = LookupElementBuilder.create(text as Any).bold().withTypeText(typeText)
    if (tail != null) e = e.withTailText(tail, true)
    return e
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider 4 — nomes de FUNCTION_BLOCK e FUNCTION declarados no projeto
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sugere nomes de blocos SCL (FB/FC) declarados em QUALQUER arquivo .scl do
 * projeto aberto — incluindo outros arquivos além do arquivo atual.
 *
 * Útil para:
 *   - Declarar instâncias na seção VAR: `myTank : FB_TankControl;`
 *   - Referenciar FCs pelo nome: `myResult := FC_Calc(...);`
 *
 * Prioridade 120 — entre keywords (0) e variáveis locais (150).
 * Não duplica entradas que já existem no mesmo arquivo (o provider inclui
 * todos os arquivos, incluindo o atual; o completion engine desuplica por nome).
 *
 * DumbAware: usa FileTypeIndex que funciona durante indexação.
 */
private object SclProjectNamesCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (isInsideComment(parameters.position)) return
        if (isInsideString(parameters.position))  return

        val project = parameters.position.project
        val scope   = GlobalSearchScope.projectScope(project)
        val files   = FileTypeIndex.getFiles(SclFileType, scope)

        val r = result.caseInsensitive()

        for (vf in files) {
            val psiFile = PsiManager.getInstance(project).findFile(vf) ?: continue

            // FUNCTION_BLOCK names
            PsiTreeUtil.findChildrenOfType(psiFile, SclFunctionBlockDecl::class.java).forEach { fb ->
                val name = blockNameText(fb) ?: return@forEach
                val entry = LookupElementBuilder.create(name as Any)
                    .bold()
                    .withTypeText("FUNCTION_BLOCK")
                    .withTailText("  [${vf.name}]", true)
                r.addElement(PrioritizedLookupElement.withPriority(entry, 120.0))
            }

            // FUNCTION names
            PsiTreeUtil.findChildrenOfType(psiFile, SclFunctionDecl::class.java).forEach { fc ->
                val name = blockNameText(fc) ?: return@forEach
                val retType = fc.typeRef?.text?.trim() ?: ""
                val entry = LookupElementBuilder.create(name as Any)
                    .bold()
                    .withTypeText("FUNCTION${if (retType.isNotEmpty()) " : $retType" else ""}")
                    .withTailText("  [${vf.name}]", true)
                r.addElement(PrioritizedLookupElement.withPriority(entry, 120.0))
            }
        }
    }

    /** Extrai o nome de texto de um FUNCTION_BLOCK node (IDENTIFIER ou QUOTED sem aspas). */
    private fun blockNameText(fb: SclFunctionBlockDecl): String? {
        var node = fb.node.firstChildNode
        var seenKeyword = false
        while (node != null) {
            when {
                node.elementType == SclTypes.FUNCTION_BLOCK -> seenKeyword = true
                seenKeyword && node.elementType == SclTypes.IDENTIFIER ->
                    return node.text
                seenKeyword && node.elementType == SclTypes.QUOTED_IDENTIFIER ->
                    return node.text.trim('"')
            }
            node = node.treeNext
        }
        return null
    }

    /** Extrai o nome de texto de um FUNCTION node. */
    private fun blockNameText(fc: SclFunctionDecl): String? {
        var node = fc.node.firstChildNode
        var seenKeyword = false
        while (node != null) {
            when {
                node.elementType == SclTypes.FUNCTION -> seenKeyword = true
                seenKeyword && node.elementType == SclTypes.IDENTIFIER ->
                    return node.text
                seenKeyword && node.elementType == SclTypes.QUOTED_IDENTIFIER ->
                    return node.text.trim('"')
            }
            node = node.treeNext
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider 5 — acesso a membros via ponto: instancia.Q, timer.ET, etc.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dispara após o usuário digitar `.` depois de um identificador.
 *
 * Exemplos:
 *   s_FillTimer.  → sugere Q (BOOL), ET (TIME), IN (BOOL), PT (TIME)
 *   s_StartEdge.  → sugere Q (BOOL)
 *
 * Passos:
 *   1. Encontra o identificador imediatamente antes do ponto
 *   2. Procura a declaração dessa variável nas seções VAR do bloco pai
 *   3. Resolve o tipo declarado (ex: TON) → lista os membros do FB builtin
 *   4. Se o tipo for um FB de projeto, busca suas VAR_OUTPUT/VAR_INPUT
 *
 * Tipos builtin suportados: TON, TOF, TP, TONR, CTU, CTD, CTUD, R_TRIG, F_TRIG
 */
private object SclMemberAccessCompletionProvider : CompletionProvider<CompletionParameters>() {

    // ── Membros de FBs builtin Siemens ────────────────────────────────────────

    private data class FbMember(val name: String, val type: String, val isOutput: Boolean)

    private val FB_MEMBERS: Map<String, List<FbMember>> = mapOf(
        "TON"    to listOf(
            FbMember("IN", "BOOL", false), FbMember("PT", "TIME", false),
            FbMember("Q",  "BOOL", true),  FbMember("ET", "TIME", true),
        ),
        "TOF"    to listOf(
            FbMember("IN", "BOOL", false), FbMember("PT", "TIME", false),
            FbMember("Q",  "BOOL", true),  FbMember("ET", "TIME", true),
        ),
        "TP"     to listOf(
            FbMember("IN", "BOOL", false), FbMember("PT", "TIME", false),
            FbMember("Q",  "BOOL", true),  FbMember("ET", "TIME", true),
        ),
        "TONR"   to listOf(
            FbMember("IN", "BOOL", false), FbMember("R",  "BOOL", false),
            FbMember("PT", "TIME", false),
            FbMember("Q",  "BOOL", true),  FbMember("ET", "TIME", true),
        ),
        "CTU"    to listOf(
            FbMember("CU", "BOOL", false), FbMember("R",  "BOOL", false),
            FbMember("PV", "INT",  false),
            FbMember("Q",  "BOOL", true),  FbMember("CV", "INT",  true),
        ),
        "CTD"    to listOf(
            FbMember("CD", "BOOL", false), FbMember("LD", "BOOL", false),
            FbMember("PV", "INT",  false),
            FbMember("Q",  "BOOL", true),  FbMember("CV", "INT",  true),
        ),
        "CTUD"   to listOf(
            FbMember("CU", "BOOL", false), FbMember("CD", "BOOL", false),
            FbMember("R",  "BOOL", false), FbMember("LD", "BOOL", false),
            FbMember("PV", "INT",  false),
            FbMember("QU", "BOOL", true),  FbMember("QD", "BOOL", true),
            FbMember("CV", "INT",  true),
        ),
        "R_TRIG" to listOf(
            FbMember("CLK", "BOOL", false),
            FbMember("Q",   "BOOL", true),
        ),
        "F_TRIG" to listOf(
            FbMember("CLK", "BOOL", false),
            FbMember("Q",   "BOOL", true),
        ),
        "SR"     to listOf(
            FbMember("S1", "BOOL", false), FbMember("R",  "BOOL", false),
            FbMember("Q1", "BOOL", true),
        ),
        "RS"     to listOf(
            FbMember("S",  "BOOL", false), FbMember("R1", "BOOL", false),
            FbMember("Q1", "BOOL", true),
        ),
    )

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        if (isInsideComment(parameters.position)) return
        if (isInsideString(parameters.position))  return

        // Encontrar o elemento antes do ponto no arquivo ORIGINAL
        val dotOffset = parameters.offset - 1   // posição do DOT no original
        if (dotOffset < 1) return
        val dotElem = parameters.originalFile.findElementAt(dotOffset) ?: return
        if (dotElem.node?.elementType != SclTypes.DOT) return

        // Identificador imediatamente antes do DOT
        val identElem = parameters.originalFile.findElementAt(dotOffset - 1) ?: return
        val instanceName = identElem.text.trim() .ifEmpty { return }

        // Encontrar o bloco pai para escopo correto
        val block: PsiElement = PsiTreeUtil.getParentOfType(
            identElem,
            SclFunctionBlockDecl::class.java,
            SclFunctionDecl::class.java,
            SclOrgBlockDecl::class.java,
        ) ?: parameters.originalFile

        // Resolver o tipo declarado da variável
        val declaredType = resolveVarType(instanceName, block) ?: return

        val r = result.caseInsensitive()

        // Caso 1 — FB builtin (TON, R_TRIG, CTU, …)
        val members = FB_MEMBERS[declaredType.uppercase()]
        if (members != null) {
            for (m in members) {
                val icon = if (m.isOutput) AllIcons.Nodes.Property else AllIcons.Nodes.Parameter
                val entry = LookupElementBuilder.create(m.name as Any)
                    .bold()
                    .withIcon(icon)
                    .withTypeText(m.type)
                    .withTailText("  [$declaredType]", true)
                r.addElement(PrioritizedLookupElement.withPriority(entry, 110.0))
            }
            return
        }

        // Caso 2 — FB do projeto (busca VAR_OUTPUT + VAR_INPUT do tipo declarado)
        val fbSections = findFbSectionsForType(declaredType, parameters.position.project)
        for ((section, label) in fbSections) {
            for (decl in section.varDeclList) {
                val name = decl.node.firstChildNode
                    ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
                    ?.text ?: continue
                val typeTxt = decl.typeRef?.text?.trim() ?: ""
                val icon = if (label == "OUTPUT") AllIcons.Nodes.Property else AllIcons.Nodes.Parameter
                val entry = LookupElementBuilder.create(name as Any)
                    .bold()
                    .withIcon(icon)
                    .withTypeText(typeTxt)
                    .withTailText("  [$label]", true)
                r.addElement(PrioritizedLookupElement.withPriority(entry, 110.0))
            }
        }
    }

    /** Procura o tipo declarado de [instanceName] nas seções VAR de [block]. */
    private fun resolveVarType(instanceName: String, block: PsiElement): String? {
        val sections = PsiTreeUtil.findChildrenOfType(block, SclVarSection::class.java)
        for (section in sections) {
            for (decl in section.varDeclList) {
                val name = decl.node.firstChildNode
                    ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
                    ?.text ?: continue
                if (name.equals(instanceName, ignoreCase = true)) {
                    return decl.typeRef?.text?.trim()
                }
            }
        }
        return null
    }

    /**
     * Busca no projeto todas as seções VAR_INPUT e VAR_OUTPUT do FB cujo nome
     * coincide com [typeName] (ex: "FB_TankControl").
     * Retorna pares (SclVarSection, labelString).
     */
    private fun findFbSectionsForType(
        typeName: String,
        project: com.intellij.openapi.project.Project,
    ): List<Pair<SclVarSection, String>> {
        val scope = GlobalSearchScope.projectScope(project)
        val result = mutableListOf<Pair<SclVarSection, String>>()
        for (vf in FileTypeIndex.getFiles(SclFileType, scope)) {
            val psi = PsiManager.getInstance(project).findFile(vf) ?: continue
            PsiTreeUtil.findChildrenOfType(psi, SclFunctionBlockDecl::class.java).forEach { fb ->
                val fbName = blockNameOf(fb) ?: return@forEach
                if (!fbName.equals(typeName, ignoreCase = true)) return@forEach
                for (sec in PsiTreeUtil.findChildrenOfType(fb, SclVarSection::class.java)) {
                    when (sec.node.firstChildNode?.elementType) {
                        SclTypes.VAR_INPUT  -> result.add(sec to "INPUT")
                        SclTypes.VAR_OUTPUT -> result.add(sec to "OUTPUT")
                        else -> {}
                    }
                }
            }
        }
        return result
    }

    private fun blockNameOf(fb: SclFunctionBlockDecl): String? {
        var node = fb.node.firstChildNode
        var seenKw = false
        while (node != null) {
            when {
                node.elementType == SclTypes.FUNCTION_BLOCK -> seenKw = true
                seenKw && node.elementType == SclTypes.IDENTIFIER       -> return node.text
                seenKw && node.elementType == SclTypes.QUOTED_IDENTIFIER -> return node.text.trim('"')
            }
            node = node.treeNext
        }
        return null
    }
}
