package com.scl.plugin.linter.safety

import com.intellij.psi.PsiElement
import com.scl.plugin.psi.SclFunctionBlockDecl
import com.scl.plugin.psi.SclFunctionDecl
import com.scl.plugin.psi.SclOrgBlockDecl

object SclSafetyUtils {

    fun isSafetyBlock(name: String?): Boolean {
        if (name == null) return false
        val clean = name.trim('"')
        return clean.startsWith("F_")
    }

    /** True if [element] is nested inside any F_* block (FB, FC, or OB). */
    fun isInSafetyBlock(element: PsiElement): Boolean {
        var cur: PsiElement? = element.parent
        while (cur != null) {
            when (cur) {
                is SclFunctionBlockDecl -> return isSafetyBlock(cur.name)
                is SclFunctionDecl      -> return isSafetyBlock(cur.name)
                is SclOrgBlockDecl      -> return isSafetyBlock(cur.name)
            }
            cur = cur.parent
        }
        return false
    }

    /**
     * True if [element] is inside an F_* FB or FC — NOT an OB.
     * Used by SafetyGlobalData: Main Safety OB is the only place allowed to touch global DBs.
     */
    fun isInSafetyFunctionBlock(element: PsiElement): Boolean {
        var cur: PsiElement? = element.parent
        while (cur != null) {
            when (cur) {
                is SclFunctionBlockDecl -> return isSafetyBlock(cur.name)
                is SclFunctionDecl      -> return isSafetyBlock(cur.name)
                is SclOrgBlockDecl      -> return false
            }
            cur = cur.parent
        }
        return false
    }
}
