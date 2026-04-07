package com.scl.plugin

import com.intellij.lang.Commenter

/**
 * Suporte a comentarios SCL para Ctrl+/ (linha) e Ctrl+Shift+/ (bloco).
 *
 * SCL suporta dois estilos de comentario:
 *   // comentario de linha
 *   (* comentario de bloco *)
 *
 * Registrado no plugin.xml como lang.commenter para a linguagem SCL.
 * Fonte: https://plugins.jetbrains.com/docs/intellij/commenter.html
 */
class SclCommenter : Commenter {

    /** Prefixo para comentario de linha — Ctrl+/ */
    override fun getLineCommentPrefix(): String = "//"

    /** Prefixo de bloco — Ctrl+Shift+/ */
    override fun getBlockCommentPrefix(): String = "(*"

    /** Sufixo de bloco — Ctrl+Shift+/ */
    override fun getBlockCommentSuffix(): String = "*)"

    /** Nao usado no SCL */
    override fun getCommentedBlockCommentPrefix(): String? = null

    /** Nao usado no SCL */
    override fun getCommentedBlockCommentSuffix(): String? = null
}
