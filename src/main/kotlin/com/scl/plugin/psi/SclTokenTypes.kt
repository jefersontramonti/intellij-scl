package com.scl.plugin.psi

/**
 * Aliases para os tipos de token gerados pelo Grammar-Kit em SclTypes.
 *
 * Fase 2: SclTypes (gerado por generateSclParser) e a fonte autoritativa
 * de todas as instancias IElementType. SclTokenTypes delega para SclTypes,
 * garantindo que lexer, parser e highlighter compartilhem as MESMAS instancias.
 */
@Suppress("unused")
object SclTokenTypes {

    // Blocos
    val FUNCTION_BLOCK         get() = SclTypes.FUNCTION_BLOCK
    val END_FUNCTION_BLOCK     get() = SclTypes.END_FUNCTION_BLOCK
    val FUNCTION               get() = SclTypes.FUNCTION
    val END_FUNCTION           get() = SclTypes.END_FUNCTION
    val ORGANIZATION_BLOCK     get() = SclTypes.ORGANIZATION_BLOCK
    val END_ORGANIZATION_BLOCK get() = SclTypes.END_ORGANIZATION_BLOCK
    val DATA_BLOCK             get() = SclTypes.DATA_BLOCK
    val END_DATA_BLOCK         get() = SclTypes.END_DATA_BLOCK
    val TYPE                   get() = SclTypes.TYPE
    val END_TYPE               get() = SclTypes.END_TYPE
    val STRUCT                 get() = SclTypes.STRUCT
    val END_STRUCT             get() = SclTypes.END_STRUCT
    val BEGIN                  get() = SclTypes.BEGIN

    // Secoes de variaveis
    val VAR          get() = SclTypes.VAR
    val VAR_INPUT    get() = SclTypes.VAR_INPUT
    val VAR_OUTPUT   get() = SclTypes.VAR_OUTPUT
    val VAR_IN_OUT   get() = SclTypes.VAR_IN_OUT
    val VAR_STATIC   get() = SclTypes.VAR_STATIC
    val VAR_TEMP     get() = SclTypes.VAR_TEMP
    val VAR_CONSTANT get() = SclTypes.VAR_CONSTANT
    val END_VAR      get() = SclTypes.END_VAR

    // Controle de fluxo
    val IF         get() = SclTypes.IF
    val THEN       get() = SclTypes.THEN
    val ELSIF      get() = SclTypes.ELSIF
    val ELSE       get() = SclTypes.ELSE
    val END_IF     get() = SclTypes.END_IF
    val CASE       get() = SclTypes.CASE
    val OF         get() = SclTypes.OF
    val END_CASE   get() = SclTypes.END_CASE
    val FOR        get() = SclTypes.FOR
    val TO         get() = SclTypes.TO
    val BY         get() = SclTypes.BY
    val DO         get() = SclTypes.DO
    val END_FOR    get() = SclTypes.END_FOR
    val WHILE      get() = SclTypes.WHILE
    val END_WHILE  get() = SclTypes.END_WHILE
    val REPEAT     get() = SclTypes.REPEAT
    val UNTIL      get() = SclTypes.UNTIL
    val END_REPEAT get() = SclTypes.END_REPEAT
    val RETURN     get() = SclTypes.RETURN
    val EXIT       get() = SclTypes.EXIT
    val CONTINUE   get() = SclTypes.CONTINUE

    // Tipos de dados
    val TYPE_BOOL   get() = SclTypes.TYPE_BOOL
    val TYPE_BYTE   get() = SclTypes.TYPE_BYTE
    val TYPE_WORD   get() = SclTypes.TYPE_WORD
    val TYPE_DWORD  get() = SclTypes.TYPE_DWORD
    val TYPE_LWORD  get() = SclTypes.TYPE_LWORD
    val TYPE_INT    get() = SclTypes.TYPE_INT
    val TYPE_UINT   get() = SclTypes.TYPE_UINT
    val TYPE_DINT   get() = SclTypes.TYPE_DINT
    val TYPE_UDINT  get() = SclTypes.TYPE_UDINT
    val TYPE_LINT   get() = SclTypes.TYPE_LINT
    val TYPE_ULINT  get() = SclTypes.TYPE_ULINT
    val TYPE_REAL   get() = SclTypes.TYPE_REAL
    val TYPE_LREAL  get() = SclTypes.TYPE_LREAL
    val TYPE_CHAR   get() = SclTypes.TYPE_CHAR
    val TYPE_STRING get() = SclTypes.TYPE_STRING
    val TYPE_TIME   get() = SclTypes.TYPE_TIME
    val TYPE_DATE   get() = SclTypes.TYPE_DATE
    val TYPE_TOD    get() = SclTypes.TYPE_TOD
    val TYPE_DTL    get() = SclTypes.TYPE_DTL
    val TYPE_TON    get() = SclTypes.TYPE_TON
    val TYPE_TOF    get() = SclTypes.TYPE_TOF
    val TYPE_TP     get() = SclTypes.TYPE_TP
    val TYPE_TONR   get() = SclTypes.TYPE_TONR
    val TYPE_CTU    get() = SclTypes.TYPE_CTU
    val TYPE_CTD    get() = SclTypes.TYPE_CTD
    val TYPE_CTUD   get() = SclTypes.TYPE_CTUD

    // Operadores logicos
    val AND get() = SclTypes.AND
    val OR  get() = SclTypes.OR
    val NOT get() = SclTypes.NOT
    val XOR get() = SclTypes.XOR
    val MOD get() = SclTypes.MOD

    // Literais
    val BOOL_LITERAL    get() = SclTypes.BOOL_LITERAL
    val INTEGER_LITERAL get() = SclTypes.INTEGER_LITERAL
    val REAL_LITERAL    get() = SclTypes.REAL_LITERAL
    val STRING_LITERAL  get() = SclTypes.STRING_LITERAL
    val TIME_LITERAL    get() = SclTypes.TIME_LITERAL

    // Comentarios
    val LINE_COMMENT  get() = SclTypes.LINE_COMMENT
    val BLOCK_COMMENT get() = SclTypes.BLOCK_COMMENT

    // Pontuacao
    val ASSIGN    get() = SclTypes.ASSIGN
    val SEMICOLON get() = SclTypes.SEMICOLON
    val COLON     get() = SclTypes.COLON
    val DOT       get() = SclTypes.DOT
    val DOTDOT    get() = SclTypes.DOTDOT
    val COMMA     get() = SclTypes.COMMA
    val LPAREN    get() = SclTypes.LPAREN
    val RPAREN    get() = SclTypes.RPAREN
    val LBRACKET  get() = SclTypes.LBRACKET
    val RBRACKET  get() = SclTypes.RBRACKET
    val PLUS      get() = SclTypes.PLUS
    val MINUS     get() = SclTypes.MINUS
    val MULTIPLY  get() = SclTypes.MULTIPLY
    val DIVIDE    get() = SclTypes.DIVIDE
    val POWER     get() = SclTypes.POWER
    val EQ        get() = SclTypes.EQ
    val NEQ       get() = SclTypes.NEQ
    val LT        get() = SclTypes.LT
    val GT        get() = SclTypes.GT
    val LEQ       get() = SclTypes.LEQ
    val GEQ       get() = SclTypes.GEQ

    // Identificadores
    val IDENTIFIER    get() = SclTypes.IDENTIFIER
    val MEMORY_ACCESS get() = SclTypes.MEMORY_ACCESS
}
