package com.scl.plugin.completion

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl
import com.scl.plugin.psi.SclVarSection

/**
 * Resolução de símbolos SCL no escopo léxico do cursor — Fase 4A.
 *
 * Navega a árvore PSI para cima a partir de um elemento e coleta todas
 * as declarações de variáveis nas seções VAR do bloco pai
 * (FUNCTION_BLOCK, FUNCTION ou ORGANIZATION_BLOCK).
 *
 * Uso principal: resolver `myTimer : TON` para que o parameter info popup
 * mostre os parâmetros de TON quando o cursor está em `myTimer(|`.
 *
 * Suporta tanto:
 *   myTimer(IN := ...)   → IDENTIFIER "myTimer" → tipo TON
 *   #myTimer(IN := ...)  → LOCAL_VAR_ID "#myTimer" → tipo TON (# removido)
 */
object SclSymbolResolver {

    // ─────────────────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve o tipo de FB de uma variável no bloco pai de [context].
     *
     * @param context Qualquer elemento PSI dentro do bloco a pesquisar
     * @param varName Nome da variável (sem # se for LOCAL_VAR_ID)
     * @return O [SclBuiltin] do tipo declarado, ou null se não encontrado /
     *         se o tipo não for um builtin reconhecido.
     */
    fun resolveVarType(context: PsiElement, varName: String): SclBuiltin? {
        val sections = findVarSections(context) ?: return null
        return lookupInSections(sections, varName)
    }

    /**
     * Constrói o mapa completo de variáveis declaradas no bloco pai de [context].
     *
     * @return Map de `varName.lowercase() → SclBuiltin` para todas as variáveis
     *         cujo tipo é um builtin reconhecido (TON, CTU, R_TRIG, etc.).
     */
    fun buildSymbolMap(context: PsiElement): Map<String, SclBuiltin> {
        val sections = findVarSections(context) ?: return emptyMap()
        val map = mutableMapOf<String, SclBuiltin>()
        for (section in sections) {
            for (decl in section.varDeclList) {
                val name    = decl.identifier() ?: continue
                val typeTxt = decl.typeName()   ?: continue
                val builtin = SclBuiltinFunctions.findByName(typeTxt) ?: continue
                map[name.lowercase()] = builtin
            }
        }
        return map
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navegação PSI interna
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sobe a árvore PSI até encontrar um bloco de programa e retorna suas
     * seções VAR, ou null se nenhum bloco for encontrado.
     */
    private fun findVarSections(context: PsiElement): List<SclVarSection>? {
        var el: PsiElement? = context.parent
        while (el != null && el !is PsiFile) {
            val sections = varSectionsOf(el)
            if (sections != null) return sections
            el = el.parent
        }
        return null
    }

    /** Retorna as VAR sections do elemento se ele for um bloco de programa. */
    private fun varSectionsOf(el: PsiElement): List<SclVarSection>? = when (el) {
        is SclFunctionBlockDecl -> el.varSectionList
        is SclFunctionDecl      -> el.varSectionList
        is SclOrgBlockDecl      -> el.varSectionList
        else                    -> null
    }

    /** Procura [varName] nas sections e retorna o builtin do tipo declarado. */
    private fun lookupInSections(
        sections: List<SclVarSection>,
        varName: String,
    ): SclBuiltin? {
        for (section in sections) {
            for (decl in section.varDeclList) {
                val name = decl.identifier() ?: continue
                if (name.equals(varName, ignoreCase = true)) {
                    val typeTxt = decl.typeName() ?: continue
                    return SclBuiltinFunctions.findByName(typeTxt)
                }
            }
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Extensões locais em SclVarDecl (acesso ao AST bruto)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nome da variável declarada — primeiro filho IDENTIFIER do nó varDecl.
     * Após o `pin=1` no BNF, o IDENTIFIER está sempre presente se a regra
     * foi iniciada; `typeRef` pode estar ausente em recuperação de erro.
     */
    private fun SclVarDecl.identifier(): String? =
        node.firstChildNode
            ?.takeIf { it.elementType == SclTypes.IDENTIFIER }
            ?.text

    /**
     * Texto bruto do primeiro token do typeRef (ex: "TON", "CTU", "INT").
     * Para tipos builtin, o primeiro token DO typeRef é o próprio nome do tipo.
     * Retorna null se typeRef ausente (erro de parsing) ou sem filhos.
     */
    private fun SclVarDecl.typeName(): String? =
        typeRef?.node?.firstChildNode?.text
}
