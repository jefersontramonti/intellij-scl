package com.scl.plugin.reference

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.scl.plugin.language.SclFileType
import com.scl.plugin.psi.SclElementFactory
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclNamedElement
import com.scl.plugin.psi.SclOrgBlockDecl
import com.scl.plugin.psi.SclStructField
import com.scl.plugin.psi.SclTypes
import com.scl.plugin.psi.SclVarDecl

private val RLOG = Logger.getInstance("SclReference.resolve")

/**
 * Referência PSI que resolve um identificador de USO para a DECLARAÇÃO.
 *
 * Estratégia de resolução (em ordem):
 *   1. **Escopo local** — declarações `VAR*` do FB/FC/OB pai:
 *        FUNCTION_BLOCK FB
 *            VAR  s_Timer : TON;  END_VAR
 *        BEGIN  s_Timer(IN := ...);        ← resolve para a VAR acima
 *   2. **DB de instância** — `"FB_Name_DB"` → FB com nome `FB_Name`:
 *        "FB_TankControl_DB"()              ← resolve para FUNCTION_BLOCK FB_TankControl
 *   3. **Bloco global** — nome puro é FB/FC/OB em outro arquivo .scl:
 *        MyFB()                             ← resolve para FUNCTION_BLOCK MyFB
 *
 * Fase 6 — implementa apenas Go to Definition + Find Usages.
 * Rename será adicionado em fase futura.
 */
class SclReference(
    element: PsiElement,
    rangeInElement: TextRange,
) : PsiReferenceBase<PsiElement>(element, rangeInElement) {

    /** Nome que buscamos (sem aspas, sem `#`). */
    private val referencedName: String
        get() = rangeInElement.substring(element.text).trim('"', '#')

    // ── RESOLVE ───────────────────────────────────────────────────────────────

    override fun resolve(): PsiElement? {
        val name = referencedName
        RLOG.warn("[SCL-RESOLVE] name='$name' element='${element.text}' parent=${element.parent?.javaClass?.simpleName}")
        if (name.isEmpty()) return null

        resolveInLocalScope(name)?.let {
            RLOG.warn("[SCL-RESOLVE]   -> LOCAL: ${it.javaClass.simpleName} '${(it as? SclNamedElement)?.name}'")
            return it
        }
        resolveAsInstanceDb(name)?.let {
            RLOG.warn("[SCL-RESOLVE]   -> INSTANCE_DB: ${(it as? SclNamedElement)?.name}")
            return it
        }
        resolveAsGlobalBlock(name)?.let {
            RLOG.warn("[SCL-RESOLVE]   -> GLOBAL: ${(it as? SclNamedElement)?.name}")
            return it
        }
        RLOG.warn("[SCL-RESOLVE]   -> NULL (not found)")
        return null
    }

    /** Busca VAR/VAR_INPUT/VAR_OUTPUT/etc e structFields do bloco pai. */
    private fun resolveInLocalScope(name: String): PsiElement? {
        val block = PsiTreeUtil.getParentOfType(
            element,
            SclFunctionBlockDecl::class.java,
            SclFunctionDecl::class.java,
            SclOrgBlockDecl::class.java,
        )
        RLOG.warn("[SCL-RESOLVE] '$name': block=${block?.javaClass?.simpleName} elementParent=${element.parent?.javaClass?.simpleName}")
        if (block == null) return null

        val decls = PsiTreeUtil.findChildrenOfType(block, SclVarDecl::class.java)
        RLOG.warn("[SCL-RESOLVE] '$name': ${decls.size} varDecls found: ${decls.map { "'${it.name}'" }}")

        decls.firstOrNull { it.name.equalsIgnoreCase(name) }?.let { return it }

        // Campos de STRUCT dentro do próprio bloco (raro, mas possível em DB).
        val fields = PsiTreeUtil.findChildrenOfType(block, SclStructField::class.java)
        RLOG.warn("[SCL-RESOLVE] '$name': ${fields.size} structFields found: ${fields.map { "'${it.name}'" }}")

        fields.firstOrNull { it.name.equalsIgnoreCase(name) }?.let { return it }

        return null
    }

    /**
     * Heurística TIA Portal: `"FB_TankControl_DB"` é um DB de instância do
     * FB chamado `FB_TankControl`. Remove sufixo `_DB` (case-insensitive) e
     * procura um FUNCTION_BLOCK com o nome base.
     */
    private fun resolveAsInstanceDb(name: String): PsiElement? {
        val suffixes = listOf("_DB", "_db", "_Db", "_dB")
        val base = suffixes.firstOrNull { name.endsWith(it) }
            ?.let { name.removeSuffix(it) }
            ?: return null
        if (base.isEmpty()) return null
        return findGlobalBlock(base, SclFunctionBlockDecl::class.java)
    }

    /** Busca FB, FC ou OB global com o nome exato. */
    private fun resolveAsGlobalBlock(name: String): PsiElement? {
        findGlobalBlock(name, SclFunctionBlockDecl::class.java)?.let { return it }
        findGlobalBlock(name, SclFunctionDecl::class.java)?.let      { return it }
        findGlobalBlock(name, SclOrgBlockDecl::class.java)?.let      { return it }
        return null
    }

    private fun <T : SclNamedElement> findGlobalBlock(
        name: String,
        klass: Class<T>,
    ): T? {
        val project = element.project
        val scope   = GlobalSearchScope.projectScope(project)
        val psiMgr  = PsiManager.getInstance(project)

        return FileTypeIndex.getFiles(SclFileType, scope)
            .asSequence()
            .mapNotNull { psiMgr.findFile(it) }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, klass).asSequence() }
            .firstOrNull { it.name.equalsIgnoreCase(name) }
    }

    // ── IS REFERENCE TO — chave para Find Usages ──────────────────────────────

    override fun isReferenceTo(element: PsiElement): Boolean {
        if (element !is SclNamedElement) return false
        val resolved = resolve() ?: return false
        return resolved.isEquivalentTo(element)
    }

    /**
     * Rename: substitui o token leaf diretamente na AST, evitando a necessidade
     * de um ElementManipulator registrado para LeafPsiElement.
     * Para QUOTED_IDENTIFIER (`"FB_Name"`) preserva as aspas ao redor do novo nome.
     */
    override fun handleElementRename(newName: String): PsiElement {
        val oldNode = element.node
        val rawName = if (oldNode.elementType == SclTypes.QUOTED_IDENTIFIER) "\"$newName\"" else newName
        val newNode = SclElementFactory.createNameNode(element.project, rawName) ?: return element
        oldNode.treeParent.replaceChild(oldNode, newNode)
        return newNode.psi
    }

    /**
     * Completion via Ctrl+Space já é tratado pelo [SclCompletionContributor]
     * da Fase 3 — aqui retornamos vazio para evitar duplicação.
     */
    override fun getVariants(): Array<Any> = emptyArray()

    private fun String?.equalsIgnoreCase(other: String): Boolean =
        this != null && this.equals(other, ignoreCase = true)
}
