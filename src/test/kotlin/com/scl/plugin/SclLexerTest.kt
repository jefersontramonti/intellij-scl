package com.scl.plugin

import com.intellij.testFramework.LexerTestCase
import com.scl.plugin.lexer.SclLexerAdapter

/**
 * Teste basico do Lexer SCL.
 * Estende LexerTestCase — framework de teste da plataforma IntelliJ.
 *
 * Rodar com: ./gradlew test
 *
 * Adicionar mais casos de teste conforme o lexer evolui.
 */
class SclLexerTest : LexerTestCase() {

    override fun createLexer() = SclLexerAdapter()

    override fun getDirPath() = "src/test/resources/lexer"

    fun testKeywords() {
        doTest(
            "IF xStart THEN\nEND_IF;",
            """
            IF ('IF')
            WHITE_SPACE (' ')
            IDENTIFIER ('xStart')
            WHITE_SPACE (' ')
            THEN ('THEN')
            WHITE_SPACE ('\n')
            END_IF ('END_IF')
            SEMICOLON (';')
            """.trimIndent()
        )
    }

    fun testBlockComment() {
        doTest(
            "(* Comentario de bloco *)",
            "BLOCK_COMMENT ('(* Comentario de bloco *)')"
        )
    }

    fun testTimeLiteral() {
        doTest(
            "T#5S",
            "TIME_LITERAL ('T#5S')"
        )
    }

    fun testMemoryAccess() {
        doTest(
            "%M100.0",
            "MEMORY_ACCESS ('%M100.0')"
        )
    }
}
