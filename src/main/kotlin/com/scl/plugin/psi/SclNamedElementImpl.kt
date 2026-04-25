package com.scl.plugin.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * Implementação base (mixin) para elementos PSI SCL que têm nome —
 * apontada no BNF via `mixin="com.scl.plugin.psi.SclNamedElementImpl"`.
 *
 * Extrai o nome do primeiro filho que seja [SclTypes.IDENTIFIER] ou
 * [SclTypes.QUOTED_IDENTIFIER]. Para QUOTED_IDENTIFIER, remove as aspas:
 *   `"FB_TankControl"` → `FB_TankControl`
 *
 * Coberta por esta classe (via BNF):
 *   • varDecl       — variáveis dentro de VAR sections
 *   • structField   — campos de UDT (STRUCT ... END_STRUCT)
 *   • typeDef       — declaração de UDT dentro de TYPE ... END_TYPE
 *   • functionBlockDecl / functionDecl / orgBlockDecl / dataBlockDecl
 *
 * Grammar-Kit gera a classe impl concreta (ex.: SclVarDeclImpl) estendendo
 * esta classe, e adiciona os métodos listados em `methods=[...]`.
 */
abstract class SclNamedElementImpl(node: ASTNode) :
    ASTWrapperPsiElement(node), SclNamedElement {

    /**
     * Nome do elemento — primeiro IDENTIFIER ou QUOTED_IDENTIFIER entre os filhos.
     *
     * Percorre FILHOS DIRETOS do nó: o IDENTIFIER/QUOTED_IDENTIFIER pode não
     * ser o primeiríssimo filho (ex.: functionBlockDecl tem FUNCTION_BLOCK antes).
     * Por isso não usamos `firstChildNode`.
     */
    override fun getName(): String? {
        val id = findNameNode() ?: return null
        return when (id.elementType) {
            SclTypes.QUOTED_IDENTIFIER -> id.text.removeSurrounding("\"")
            else                       -> id.text
        }
    }

    override fun setName(name: String): PsiElement {
        val nameNode = findNameNode() ?: return this
        val rawName = if (nameNode.elementType == SclTypes.QUOTED_IDENTIFIER) "\"$name\"" else name
        val newNode = SclElementFactory.createNameNode(project, rawName) ?: return this
        nameNode.treeParent.replaceChild(nameNode, newNode)
        return this
    }

    /**
     * Nó PSI do identificador — alvo de navegação (Ctrl+Click).
     * Usado também pelo framework de rename e highlighting de uso.
     */
    override fun getNameIdentifier(): PsiElement? = findNameNode()?.psi

    /**
     * Offset de navegação — pula direto ao nome (não ao início do FB inteiro).
     */
    override fun getTextOffset(): Int =
        findNameNode()?.psi?.textOffset ?: super.getTextOffset()

    /**
     * Procura entre os filhos DIRETOS o primeiro nó IDENTIFIER ou
     * QUOTED_IDENTIFIER. Retorna null se o nó não tiver nome (erro de parse).
     */
    private fun findNameNode(): ASTNode? {
        var child: ASTNode? = node.firstChildNode
        while (child != null) {
            val t = child.elementType
            if (t == SclTypes.IDENTIFIER || t == SclTypes.QUOTED_IDENTIFIER) {
                return child
            }
            child = child.treeNext
        }
        return null
    }
}
