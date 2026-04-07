/* ─────────────────────────────────────────────────────────────────────────────
   Scl.flex — Lexer JFlex para SCL (Structured Control Language)
   Siemens SCLV4 (S7-300/400) + TIA Portal V19

   Como usar:
     ./gradlew generateSclLexer  (generateSclParser deve rodar antes)

   Referencia JFlex: https://jflex.de/manual.html
   Referencia formal: Apendice B (Lexical Rules) do SCLV4

   NOTA: SCL e case-INSENSITIVE (%ignorecase cobre todas as regras)
   ───────────────────────────────────────────────────────────────────────────── */

package com.scl.plugin.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.scl.plugin.psi.SclTypes;

%%

%class SclLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

/* SCL e case-insensitive: IF = if = If */
%ignorecase

/* Estados do lexer */
%state BLOCK_COMMENT_STATE

%%

/* ── Whitespace ─────────────────────────────────────────────────────────── */
[ \t\r\n\f]+                 { return TokenType.WHITE_SPACE; }

/* ── Comentarios ────────────────────────────────────────────────────────── */
"//"[^\r\n]*                  { return SclTypes.LINE_COMMENT; }

"(*"                          { yybegin(BLOCK_COMMENT_STATE); }
<BLOCK_COMMENT_STATE> {
    "*)"                      { yybegin(YYINITIAL); return SclTypes.BLOCK_COMMENT; }
    [^*\n]+                   { /* acumulando corpo do comentario */ }
    "*"                       { /* asterisco isolado dentro do comentario */ }
    \n                        { /* nova linha dentro do comentario */ }
    <<EOF>>                   { yybegin(YYINITIAL); return SclTypes.BLOCK_COMMENT; }
}

/* ── Literais tipados (devem vir ANTES das palavras-chave para que         ── */
/* ── DT#, DATE#, T# etc. nao sejam tokenizados como keyword + BAD_CHAR    ── */
/* Literais de tempo/data — SCLV4 Apendice B.1.1                            */
("S5TIME"|"S5T")"#"[0-9A-Za-z_.]+              { return SclTypes.TIME_LITERAL; }
("DATE_AND_TIME"|"DT")"#"[0-9\-:._]+           { return SclTypes.TIME_LITERAL; }
("TIME_OF_DAY"|"TOD")"#"[0-9:._]+              { return SclTypes.TIME_LITERAL; }
("DATE"|"D")"#"[0-9\-.]+                        { return SclTypes.TIME_LITERAL; }
("TIME"|"T")"#"[0-9A-Za-z_.]+                  { return SclTypes.TIME_LITERAL; }

/* Literais de tipo com prefixo (BOOL#TRUE, INT#42, WORD#16#FF etc.) */
[A-Za-z_][A-Za-z0-9_]*"#"[^ \t\r\n;,\)\]]+    { return SclTypes.INTEGER_LITERAL; }

/* ── Literais numericos ─────────────────────────────────────────────────── */
/* Real antes de Integer (capturar o ponto decimal)                          */
/* Notacao cientifica: 3.0E+10, 3e10 (SCLV4 Sec. 11.3)                     */
[0-9][0-9_]*"."[0-9][0-9_]*([eE][+\-]?[0-9]+)?   { return SclTypes.REAL_LITERAL; }
/* Decimais com underscore: 1_120_200, 32_211 (SCLV4 Sec. 11.3)             */
[0-9][0-9_]*                                        { return SclTypes.INTEGER_LITERAL; }
/* Hex: 16#FF, Octal: 8#77, Binario: 2#1010 — com underscores opccionais    */
(16|8|2)"#"[0-9A-Fa-f_]+                           { return SclTypes.INTEGER_LITERAL; }

/* ── Identificadores especiais TIA Portal ───────────────────────────────── */
/* LOCAL_VAR_ID deve vir antes de IDENTIFIER para capturar #varName          */
"#"[A-Za-z_][A-Za-z0-9_]*   { return SclTypes.LOCAL_VAR_ID; }

/* QUOTED_IDENTIFIER deve vir ANTES de STRING_LITERAL (ambos usam aspas)    */
/* "TagName" — tag global TIA Portal (SCLV4 Apendice A.10 — Symbol rule)    */
"\""[^\"]*"\""               { return SclTypes.QUOTED_IDENTIFIER; }

/* ── Literais string — aspas simples (SCLV4 Apendice B.1.1)  ─────────────── */
"'"([^'$]|\$[^\r\n])*"'"     { return SclTypes.STRING_LITERAL; }

/* ── Acesso direto a memoria Siemens: %MW10, %I0.0, %Q2.3 ─────────────── */
"%"[IQMABCDEFGHLMNPQa-z]+"W"?[0-9]+("."[0-9]+)?  { return SclTypes.MEMORY_ACCESS; }

/* ─────────────────────────────────────────────────────────────────────────── */
/* PALAVRAS-CHAVE — todas ANTES da regra IDENTIFIER                          */
/* JFlex usa longest-match: FUNCTION_BLOCK (14 chars) ganha sobre FUNCTION   */
/* (8 chars) para o texto "FUNCTION_BLOCK". Ordem de declaracao nao importa   */
/* para tokens de comprimento diferente, mas organizamos por clareza.         */
/* ─────────────────────────────────────────────────────────────────────────── */

/* ── Estruturas de bloco ─────────────────────────────────────────────────── */
"FUNCTION_BLOCK"          { return SclTypes.FUNCTION_BLOCK; }
"END_FUNCTION_BLOCK"      { return SclTypes.END_FUNCTION_BLOCK; }
"FUNCTION"                { return SclTypes.FUNCTION; }
"END_FUNCTION"            { return SclTypes.END_FUNCTION; }
"ORGANIZATION_BLOCK"      { return SclTypes.ORGANIZATION_BLOCK; }
"END_ORGANIZATION_BLOCK"  { return SclTypes.END_ORGANIZATION_BLOCK; }
"DATA_BLOCK"              { return SclTypes.DATA_BLOCK; }
"END_DATA_BLOCK"          { return SclTypes.END_DATA_BLOCK; }
"TYPE"                    { return SclTypes.TYPE; }
"END_TYPE"                { return SclTypes.END_TYPE; }
"STRUCT"                  { return SclTypes.STRUCT; }
"END_STRUCT"              { return SclTypes.END_STRUCT; }
"BEGIN"                   { return SclTypes.BEGIN; }

/* ── Secoes de variaveis ─────────────────────────────────────────────────── */
"VAR_INPUT"               { return SclTypes.VAR_INPUT; }
"VAR_OUTPUT"              { return SclTypes.VAR_OUTPUT; }
"VAR_IN_OUT"              { return SclTypes.VAR_IN_OUT; }
"VAR_STATIC"              { return SclTypes.VAR_STATIC; }
"VAR_TEMP"                { return SclTypes.VAR_TEMP; }
"VAR_CONSTANT"            { return SclTypes.VAR_CONSTANT; }
"VAR"                     { return SclTypes.VAR; }
"END_VAR"                 { return SclTypes.END_VAR; }

/* ── Constantes (SCLV4 Apendice C.2) ────────────────────────────────────── */
"END_CONST"               { return SclTypes.END_CONST; }
"CONST"                   { return SclTypes.CONST; }

/* ── Labels de salto (SCLV4 Sec. 15.9) ─────────────────────────────────── */
"END_LABEL"               { return SclTypes.END_LABEL; }
"LABEL"                   { return SclTypes.LABEL; }

/* ── Regioes TIA Portal ──────────────────────────────────────────────────── */
"END_REGION"              { return SclTypes.END_REGION; }
"REGION"                  { return SclTypes.REGION; }

/* ── Controle de fluxo ───────────────────────────────────────────────────── */
"IF"                      { return SclTypes.IF; }
"THEN"                    { return SclTypes.THEN; }
"ELSIF"                   { return SclTypes.ELSIF; }
"ELSE"                    { return SclTypes.ELSE; }
"END_IF"                  { return SclTypes.END_IF; }
"CASE"                    { return SclTypes.CASE; }
"OF"                      { return SclTypes.OF; }
"END_CASE"                { return SclTypes.END_CASE; }
"FOR"                     { return SclTypes.FOR; }
"TO"                      { return SclTypes.TO; }
"BY"                      { return SclTypes.BY; }
"DO"                      { return SclTypes.DO; }
"END_FOR"                 { return SclTypes.END_FOR; }
"WHILE"                   { return SclTypes.WHILE; }
"END_WHILE"               { return SclTypes.END_WHILE; }
"REPEAT"                  { return SclTypes.REPEAT; }
"UNTIL"                   { return SclTypes.UNTIL; }
"END_REPEAT"              { return SclTypes.END_REPEAT; }
"RETURN"                  { return SclTypes.RETURN; }
"EXIT"                    { return SclTypes.EXIT; }
"CONTINUE"                { return SclTypes.CONTINUE; }
"GOTO"                    { return SclTypes.GOTO; }

/* ── Tipos de dados elementares ──────────────────────────────────────────── */
/* Ordem: mais longos antes (DATE_AND_TIME antes de DATE, TIME_OF_DAY antes de TIME) */
"DATE_AND_TIME"           { return SclTypes.TYPE_DATE_AND_TIME; }
"TIME_OF_DAY"             { return SclTypes.TYPE_TOD; }
"S5TIME"                  { return SclTypes.TYPE_S5TIME; }
"BOOL"                    { return SclTypes.TYPE_BOOL; }
"BYTE"                    { return SclTypes.TYPE_BYTE; }
"WORD"                    { return SclTypes.TYPE_WORD; }
"DWORD"                   { return SclTypes.TYPE_DWORD; }
"LWORD"                   { return SclTypes.TYPE_LWORD; }
"SINT"                    { return SclTypes.TYPE_INT; }
"USINT"                   { return SclTypes.TYPE_UINT; }
"UDINT"                   { return SclTypes.TYPE_UDINT; }
"ULINT"                   { return SclTypes.TYPE_ULINT; }
"UINT"                    { return SclTypes.TYPE_UINT; }
"DINT"                    { return SclTypes.TYPE_DINT; }
"LINT"                    { return SclTypes.TYPE_LINT; }
"INT"                     { return SclTypes.TYPE_INT; }
"LREAL"                   { return SclTypes.TYPE_LREAL; }
"REAL"                    { return SclTypes.TYPE_REAL; }
"CHAR"                    { return SclTypes.TYPE_CHAR; }
"STRING"                  { return SclTypes.TYPE_STRING; }
"TIME"                    { return SclTypes.TYPE_TIME; }
"DATE"                    { return SclTypes.TYPE_DATE; }
"TOD"                     { return SclTypes.TYPE_TOD; }
"DT"                      { return SclTypes.TYPE_DATE_AND_TIME; }
"DTL"                     { return SclTypes.TYPE_DTL; }

/* ── Timers e Contadores Siemens ─────────────────────────────────────────── */
"TONR"                    { return SclTypes.TYPE_TONR; }
"TON"                     { return SclTypes.TYPE_TON; }
"TOF"                     { return SclTypes.TYPE_TOF; }
"CTUD"                    { return SclTypes.TYPE_CTUD; }
"CTU"                     { return SclTypes.TYPE_CTU; }
"CTD"                     { return SclTypes.TYPE_CTD; }
"TP"                      { return SclTypes.TYPE_TP; }

/* ── Tipos de parametro especiais (SCLV4 Apendice C.3) ──────────────────── */
"BLOCK_SDB"               { return SclTypes.BLOCK_SDB; }
"BLOCK_SFB"               { return SclTypes.BLOCK_SFB; }
"BLOCK_SFC"               { return SclTypes.BLOCK_SFC; }
"BLOCK_FB"                { return SclTypes.BLOCK_FB; }
"BLOCK_FC"                { return SclTypes.BLOCK_FC; }
"BLOCK_DB"                { return SclTypes.BLOCK_DB; }
"COUNTER"                 { return SclTypes.COUNTER; }
"POINTER"                 { return SclTypes.POINTER; }
"TIMER"                   { return SclTypes.TIMER; }
"ANY"                     { return SclTypes.ANY; }
"ARRAY"                   { return SclTypes.ARRAY; }
"VOID"                    { return SclTypes.VOID; }
"NIL"                     { return SclTypes.NIL; }

/* ── Operadores logicos (palavras-chave) ─────────────────────────────────── */
"AND"                     { return SclTypes.AND; }
"XOR"                     { return SclTypes.XOR; }
"OR"                      { return SclTypes.OR; }
"NOT"                     { return SclTypes.NOT; }
"MOD"                     { return SclTypes.MOD; }
"DIV"                     { return SclTypes.DIV; }

/* ── Literais booleanos ──────────────────────────────────────────────────── */
"TRUE"                    { return SclTypes.BOOL_LITERAL; }
"FALSE"                   { return SclTypes.BOOL_LITERAL; }

/* ── Flags predefinidas (EN, ENO, OK) ───────────────────────────────────── */
"ENO"                     { return SclTypes.ENO; }
"EN"                      { return SclTypes.EN; }
"OK"                      { return SclTypes.OK; }

/* ── Simbolos e operadores (longer tokens MUST come first) ──────────────── */
/* Atribuicoes combinadas TIA Portal */
"+="                      { return SclTypes.PLUS_ASSIGN; }
"-="                      { return SclTypes.MINUS_ASSIGN; }
"*="                      { return SclTypes.MULTIPLY_ASSIGN; }
"/="                      { return SclTypes.DIVIDE_ASSIGN; }
/* Operador de saida TIA Portal: param => var */
"=>"                      { return SclTypes.OUTPUT_ASSIGN; }
/* Operadores relacionais e de atribuicao (multi-char antes de single-char) */
":="                      { return SclTypes.ASSIGN; }
"<>"                      { return SclTypes.NEQ; }
"<="                      { return SclTypes.LEQ; }
">="                      { return SclTypes.GEQ; }
".."                      { return SclTypes.DOTDOT; }
"**"                      { return SclTypes.POWER; }
/* Single-char operators */
";"                       { return SclTypes.SEMICOLON; }
":"                       { return SclTypes.COLON; }
"."                       { return SclTypes.DOT; }
","                       { return SclTypes.COMMA; }
"("                       { return SclTypes.LPAREN; }
")"                       { return SclTypes.RPAREN; }
"["                       { return SclTypes.LBRACKET; }
"]"                       { return SclTypes.RBRACKET; }
"+"                       { return SclTypes.PLUS; }
"-"                       { return SclTypes.MINUS; }
"*"                       { return SclTypes.MULTIPLY; }
"/"                       { return SclTypes.DIVIDE; }
"="                       { return SclTypes.EQ; }
"<"                       { return SclTypes.LT; }
">"                       { return SclTypes.GT; }
"&"                       { return SclTypes.AMPERSAND; }

/* ── Identificadores (SCLV4 Apendice B.1 — IDENTIFIER rule) ─────────────── */
/* Deve vir DEPOIS de todas as palavras-chave                                 */
/* Regra: letra ou underscore, seguido de letras/digitos/underscores          */
[A-Za-z_][A-Za-z0-9_]*    { return SclTypes.IDENTIFIER; }

/* ── Caractere invalido — nunca deve abortar ─────────────────────────────── */
/* Fonte: "Lexers must never abort prematurely because of an invalid char"    */
/*   https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html      */
[^]                        { return TokenType.BAD_CHARACTER; }
