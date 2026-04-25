# intellij-scl

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](https://github.com/jefersontramonti/intellij-scl)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2026.1-blue?style=flat-square&logo=intellijidea)](https://plugins.jetbrains.com/docs/intellij/welcome.html)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7f52ff?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

Full language support for **SCL (Structured Control Language)** inside IntelliJ IDEA.  
SCL is the IEC 61131-3 Structured Text dialect used in Siemens **TIA Portal V19** (S7-1200, S7-1500) and the classic **STEP 7** environment (S7-300/400).

---

## What is this plugin?

`intellij-scl` brings industrial PLC programming into a modern IDE. Instead of writing SCL exclusively inside TIA Portal's limited editor, you can use IntelliJ IDEA with syntax highlighting, smart completion, go-to-definition, find usages, an HW-aware linter, a code formatter, and a full MCP server — the same infrastructure that powers Java, Kotlin, and Python support in JetBrains IDEs.

The plugin is built on the **IntelliJ Platform SDK** and uses **Grammar-Kit + JFlex** to generate a complete lexer and parser from formal grammar files (`Scl.bnf`, `Scl.flex`). The grammar is derived directly from the official Siemens SCLV4 specification (Appendices B and C — Lexical and Syntax Rules) and the TIA Portal V19 programming manual.

---

## Features

### Phase 1 — Foundation ✅
- **File type recognition** — `.scl` and `.SCL` extensions registered with a custom icon
- **Syntax highlighting** for:
  - Block keywords (`FUNCTION_BLOCK`, `FUNCTION`, `DATA_BLOCK`, `ORGANIZATION_BLOCK`, `TYPE`)
  - Variable section keywords (`VAR`, `VAR_INPUT`, `VAR_OUTPUT`, `VAR_IN_OUT`, `VAR_TEMP`, `VAR_STATIC`)
  - Control flow (`IF/THEN/ELSIF/ELSE/END_IF`, `CASE/OF`, `FOR/TO/BY/DO`, `WHILE`, `REPEAT/UNTIL`, `GOTO`, `RETURN`, `EXIT`, `CONTINUE`)
  - All elementary data types (`BOOL`, `INT`, `DINT`, `REAL`, `LREAL`, `WORD`, `DWORD`, `TIME`, `DATE`, `TOD`, `S5TIME`, `DATE_AND_TIME`, …)
  - Logical and arithmetic operators (`AND`, `OR`, `XOR`, `NOT`, `MOD`, `DIV`)
  - Literals — boolean, integer (decimal/hex/octal/binary), real, string, time
  - Typed time literals (`T#5S`, `DT#2024-01-01-12:00:00`, `TOD#08:30:00`, `S5T#1H`)
  - **TIA Portal global tags** — `"QuotedIdentifier"` highlighted as global variable
  - **TIA Portal local variables** — `#localVar` highlighted as local variable
  - **Siemens memory access** — `%MW10`, `%I0.0`, `%Q2.3`
  - Comments — `//` line comments and `(* block comments *)`
  - Combined assignment operators — `+=`, `-=`, `*=`, `/=`

### Phase 2 — Parser & Editor ✅
- **Full BNF grammar** (`Scl.bnf`) generated via Grammar-Kit, producing a complete PSI tree
  - All block declarations with optional `CONST`/`LABEL` sections
  - `ARRAY[x..y] OF type` (up to 6 dimensions), `STRING[n]`
  - `REGION … END_REGION` (TIA Portal), `GOTO`, labeled statements
  - DB assignment sections (`BEGIN … END_DATA_BLOCK`)
  - Named block arguments: input (`:=`) and output (`=>`)
  - Correct operator precedence (11 levels per SCLV4 §13.1)
  - Quoted block names: `FUNCTION_BLOCK "FB_Diagnostic"` accepted
- **Code folding** — blocks, var sections, IF/FOR/WHILE/REPEAT/CASE, CONST, REGION, block comments
- **Line and block commenting** — `Ctrl+/` inserts `//`, `Ctrl+Shift+/` wraps in `(* … *)`
- **Brace matching** — highlights matching `BEGIN/END`, `IF/END_IF`, `FOR/END_FOR`, etc.

### Phase 3 — Completion & Documentation ✅
- **Code completion** — variables from `VAR_INPUT`, `VAR_OUTPUT`, `VAR_STATIC`, `VAR_TEMP`
- **Dot-completion** — `fbInstance.` triggers output variable suggestions
- **Builtin function catalog** — `SQRT`, `ABS`, `LEN`, `LEFT`, `RIGHT`, `MID`, `CONCAT`, `TON`, `TOF`, `TP`, `CTU`, `CTD`, `CTUD`, and 40+ more
- **Live templates** — `if`, `for`, `while`, `repeat`, `case`, `fb`, `fn`, `ob` expand to full SCL blocks
- **Hover documentation** (`Ctrl+Q`) — shows variable type, section, and description
- **Parameter info popup** (`Ctrl+P`) — shows FB/FC parameter signatures inline

### Phase 4 — Formatter & Structure View ✅
- **Code formatter** (`Ctrl+Alt+L`) — indentation, spacing around operators, alignment of `:=` in var declarations, blank lines between sections
- **Structure View** (`Alt+7`) — tree of blocks → VAR sections → variables, with icons per type
- **Color settings page** — customize all SCL token colors via *Settings → Editor → Color Scheme → SCL*

### Phase 4B — HW-Aware Linter ✅
- **CPU target selector** — per-project setting in *Settings → Languages → SCL → CPU Target*: `S7-1200`, `S7-1500`, `S7-300/400`
- **Status bar widget** — shows the active CPU target in the bottom bar
- **6 inspections** (all configurable per-CPU in *Settings → Editor → Inspections → SCL*):
  - **Absolute address** (`%MW`, `%I`, `%Q`) — flagged on S7-1500 (use symbolic only)
  - **Boolean in IF** — warns when a non-`BOOL` expression is used as an IF condition
  - **CASE without ELSE** — warns on missing `ELSE` branch in CASE statements
  - **FOR index type** — catches `DINT`/`UDINT` loop counters not supported on S7-300
  - **TEMP var uninitialized** — detects `VAR_TEMP` variables read before assignment
  - **Unsupported type** — flags `LREAL`, `LINT`, `ULINT` on S7-1200/S7-300

### Phase 6 — Navigation & Refactoring ✅
- **Go to Declaration** (`Ctrl+Click` / `Ctrl+B`) — jumps to variable or block declaration
- **Find Usages** (`Alt+F7`) — finds all references to a symbol across the project
- **Rename** (`Shift+F6`) — renames variables and blocks in-place across all usages
- **SCL Project View** — dedicated pane showing SCL blocks grouped by type (FB, FC, OB, UDT, DB)

### Phase 7 — MCP Server ✅
Exposes SCL project data to AI agents (Claude Code and any MCP client) via the **IntelliJ MCP Server** protocol.

| Tool | Description |
|------|-------------|
| `scl_list_blocks` | Lists all SCL blocks in the project (name, type, file path) |
| `scl_get_interface` | Returns VAR_INPUT / VAR_OUTPUT / VAR_IN_OUT interface of a block |
| `scl_generate_fb` | Generates a FUNCTION_BLOCK skeleton from an I/O description |
| `scl_validate_file` | Parses a `.scl` file and reports syntax errors |
| `scl_read_io_list` | Reads a CSV/text I/O list and maps tags to SCL variable declarations |
| `scl_project_summary` | Returns a summary of the entire SCL project (block count, types, stats) |

---

## Building Locally

### Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21+ | Target bytecode. JDK 25 also works as runtime |
| Gradle | 9.4.1 | Managed by the wrapper (`./gradlew`) |
| IntelliJ IDEA | any | Community or Ultimate for development |

### Steps

```bash
# 1. Clone
git clone https://github.com/jefersontramonti/intellij-scl.git
cd intellij-scl

# 2. Generate the parser and lexer from grammar files
./gradlew generateSclParser generateSclLexer

# 3. Compile
./gradlew compileKotlin

# 4. Launch a sandbox IntelliJ instance with the plugin loaded
#    Close IntelliJ IDEA first to avoid file-lock on pdfbox jar
./gradlew runIde

# 5. Build the distributable plugin zip
./gradlew buildPlugin
# Output: build/distributions/scl-language-support-1.0.0.zip
```

> **Note:** `./gradlew runIde` requires IntelliJ IDEA to be closed beforehand.

### Project Structure

```
scl-plugin/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
│
├── src/main/
│   ├── kotlin/com/scl/plugin/
│   │   ├── language/          # SclLanguage, SclFileType
│   │   ├── psi/               # SclFile, SclTokenTypes, SclElementType, SclNamedElement
│   │   ├── lexer/             # SclLexerAdapter
│   │   ├── parser/            # SclParserDefinition
│   │   ├── highlighting/      # SclSyntaxHighlighter, SclColorSettingsPage
│   │   ├── folding/           # SclFoldingBuilder
│   │   ├── completion/        # SclCompletionContributor, SclBuiltinFunctions, SclParameterInfoHandler
│   │   ├── documentation/     # SclDocumentationTargetProvider, SclVariableDocumentationTarget
│   │   ├── formatting/        # SclFormattingModelBuilder, SclBlock, SclSpacingBuilder
│   │   ├── structure/         # SclStructureViewFactory, SclStructureViewElement, SclBodyElement
│   │   ├── linter/            # SclHardwareLinter, SclCpuSettings, SclHardwareStatusBarWidgetFactory
│   │   │   └── inspections/   # 6 LocalInspectionTool implementations
│   │   ├── reference/         # SclReference, SclReferenceContributor, SclGotoDeclarationHandler, SclRenameHandler
│   │   ├── findUsages/        # SclFindUsagesProvider, SclUsagesSearcher
│   │   ├── view/              # SclProjectViewPane, SclProjectViewNodeDecorator
│   │   ├── mcp/               # SclMcpBase, SclToolset (6 MCP tools)
│   │   └── SclBraceMatcher.kt
│   │
│   ├── resources/
│   │   ├── META-INF/plugin.xml
│   │   ├── grammar/
│   │   │   ├── Scl.bnf
│   │   │   └── Scl.flex
│   │   ├── icons/scl.svg
│   │   └── liveTemplates/SCL.xml
│   │
│   └── gen/                   # ⚠ Generated — do not edit
│       └── com/scl/plugin/
│           ├── lexer/SclLexer.java
│           ├── parser/SclParser.java
│           └── psi/
│
└── examples/
    ├── FBs/                   # FB_TankControl, FB_Diagnostic, FB_StackLight, FB_TimerControl, FB_Esteira
    ├── OBs/                   # OB_Main
    └── UDTs/                  # UDT_TankStatus, UDT_TankSensors, UDT_TankActuators, UDT_TankCmd, UDT_PID_Params
```

---

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| IDE Platform | IntelliJ Platform SDK | 2026.1 |
| Build System | IntelliJ Platform Gradle Plugin | 2.13.1 |
| Language | Kotlin JVM | 2.3.20 |
| Lexer generator | JFlex (via Grammar-Kit) | 2023.3.0.3 |
| Parser generator | Grammar-Kit (BNF) | 2023.3.0.3 |
| JVM target | Java bytecode | 21 |
| MCP | IntelliJ MCP Server | bundled |

---

## SCL Language Reference

The grammar (`Scl.bnf` + `Scl.flex`) was built against two official Siemens sources:

- **SCLV4** — *SCL for S7-300/400 Programming*, Siemens 1998  
  Appendix B (Lexical Rules) and Appendix C (Syntax Rules) are the primary formal reference.
- **TIA Portal V19** — *Creating SCL Programs*, Siemens 11/2025  
  Source of TIA Portal extensions: `REGION/END_REGION`, combined assignments (`+=`), output operator (`=>`), quoted identifiers (`"TagName"`), local variable prefix (`#var`).

---

## License

[MIT](LICENSE) — free to use, modify, and distribute.