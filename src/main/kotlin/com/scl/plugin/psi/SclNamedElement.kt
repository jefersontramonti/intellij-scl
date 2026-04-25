package com.scl.plugin.psi

import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Marker interface para elementos PSI SCL que têm um nome identificável —
 * variáveis, campos de STRUCT, FBs, FCs, OBs, DBs e UDTs.
 *
 * [PsiNameIdentifierOwner] estende [com.intellij.psi.PsiNamedElement] e
 * adiciona `getNameIdentifier()`, usado pelo framework para highlighting
 * de uso e navegação precisa até o token do nome.
 *
 * Recursos habilitados:
 *   • Ctrl+Click / Ctrl+B (Go to Definition)
 *   • Alt+F7 (Find Usages)
 *   • Rename refactoring (Shift+F6) — básico
 *
 * [NavigatablePsiElement] garante que `navigate()` abra o editor no
 * offset do nome ao clicar em resultados de Find Usages.
 */
interface SclNamedElement : PsiNameIdentifierOwner, NavigatablePsiElement
