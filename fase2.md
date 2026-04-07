Você é um especialista em desenvolvimento de plugins IntelliJ Platform.

## Contexto do Projeto
Plugin "SCL Language Support" para IntelliJ IDEA 2026.1.
Fase 1 concluída: Lexer JFlex + Syntax Highlighting funcionando.
Leia todos os arquivos .kt e .flex existentes antes de começar.

## Referências obrigatórias — leia antes de codificar
Toda implementação deve seguir estritamente a documentação oficial:
- BNF/Parser:  https://plugins.jetbrains.com/docs/intellij/grammar-and-parser.html
- ParserDef:   https://plugins.jetbrains.com/docs/intellij/lexer-and-parser-definition.html
- Folding:     https://plugins.jetbrains.com/docs/intellij/folding-builder.html
- Commenter:   https://plugins.jetbrains.com/docs/intellij/commenter.html

## Fase 2 — Implementar em ordem

### Passo 1 — SclElementType.kt e SclTokenType.kt
Crie em `src/main/kotlin/com/scl/plugin/psi/`:
- `SclElementType.kt` — subclasse de IElementType(debugName, SclLanguage)
- `SclTokenType.kt`   — subclasse de IElementType(debugName, SclLanguage)
  Necessários para o cabeçalho do BNF (elementTypeClass e tokenTypeClass).

### Passo 2 — Scl.bnf
Crie `src/main/resources/grammar/Scl.bnf`.

Cabeçalho obrigatório (adaptar pacotes para com.scl.plugin):
{
parserClass="com.scl.plugin.parser.SclParser"
extends="com.intellij.extapi.psi.ASTWrapperPsiElement"
psiClassPrefix="Scl"
psiImplClassSuffix="Impl"
psiPackage="com.scl.plugin.psi"
psiImplPackage="com.scl.plugin.psi.impl"
elementTypeHolderClass="com.scl.plugin.psi.SclTypes"
elementTypeClass="com.scl.plugin.psi.SclElementType"
tokenTypeClass="com.scl.plugin.psi.SclTokenType"
}

Regras a implementar usando os tokens já definidos em SclTokenTypes.kt:
- sclFile, functionBlock, function, organizationBlock, dataBlock, typeDecl
- varSection (VAR, VAR_INPUT, VAR_OUTPUT, VAR_IN_OUT, VAR_STATIC, VAR_TEMP)
- varDeclaration, typeRef
- statement, ifStatement, caseStatement, forStatement, whileStatement, repeatStatement
- expression, assignment, callStatement

### Passo 3 — SclParserDefinition.kt
Substitua o SclParserPhase1. Seguindo exatamente o padrão da doc oficial:
- createParser() → retorna SclParser() (gerado pelo Grammar-Kit)
- createElement() → retorna SclTypes.Factory.createElement(node)
- getCommentTokens() → TokenSet com LINE_COMMENT e BLOCK_COMMENT

### Passo 4 — SclFoldingBuilder.kt
Crie `src/main/kotlin/com/scl/plugin/folding/SclFoldingBuilder.kt`.

OBRIGATÓRIO pela doc: estender FoldingBuilderEx E implementar DumbAware.
Sem DumbAware o folding não funciona e falha nos testes.

Regiões a recolher (usando PSI tree gerada):
- functionBlock → placeholder: "FB nome { ... }"
- varSection    → placeholder: "VAR { ... }"
- ifStatement   → placeholder: "IF ... { ... }"
- caseStatement → placeholder: "CASE ... { ... }"
- forStatement  → placeholder: "FOR ... { ... }"
- BLOCK_COMMENT longo (> 2 linhas) → placeholder: "(* ... *)"

### Passo 5 — SclCommenter.kt
Crie `src/main/kotlin/com/scl/plugin/SclCommenter.kt`.
Implementar interface Commenter com exatamente estes 5 métodos (conforme doc):
- getLineCommentPrefix()           → "//"
- getBlockCommentPrefix()          → "(*"
- getBlockCommentSuffix()          → "*)"
- getCommentedBlockCommentPrefix() → null
- getCommentedBlockCommentSuffix() → null

### Passo 6 — plugin.xml
Adicionar:
<lang.foldingBuilder language="SCL"
implementationClass="com.scl.plugin.folding.SclFoldingBuilder"/>
<lang.commenter language="SCL"
implementationClass="com.scl.plugin.SclCommenter"/>

## Stack
- IntelliJ Platform Gradle Plugin 2.13.1
- GrammarKit: sub-plugin integrado org.jetbrains.intellij.platform.grammarkit
- Kotlin 2.3.20 / JVM 21 / IntelliJ 2026.1

## Validação
./gradlew generateParser
./gradlew runIde
Verificar: folding na gutter, Ctrl+/ comenta, sem erros no Event Log.