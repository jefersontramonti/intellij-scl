# intellij-scl

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](https://github.com/jefersontramonti/intellij-scl)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2026.1-blue?style=flat-square&logo=intellijidea)](https://plugins.jetbrains.com/docs/intellij/welcome.html)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7f52ff?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

Full language support for **SCL (Structured Control Language)** inside IntelliJ IDEA.  
SCL is the IEC 61131-3 Structured Text dialect used in Siemens **TIA Portal V19** (S7-1200, S7-1500) and the classic **STEP 7** environment (S7-300/400).

---

## What is this plugin?

`intellij-scl` brings industrial PLC programming into a modern IDE. Instead of writing SCL exclusively inside TIA Portal's limited editor, you can use IntelliJ IDEA with syntax highlighting, code folding, smart commenting, and a full PSI tree — the same infrastructure that powers Java, Kotlin, and Python support in JetBrains IDEs.

The plugin is built on the **IntelliJ Platform SDK** and uses **Grammar-Kit + JFlex** to generate a complete lexer and parser from formal grammar files (`Scl.bnf`, `Scl.flex`). The grammar is derived directly from the official Siemens SCLV4 specification (Appendices B and C — Lexical and Syntax Rules) and the TIA Portal V19 programming manual.

---

## Screenshot

> Syntax highlighting in action — keywords, types, memory access, comments, and TIA Portal quoted identifiers all highlighted.

```scl
// Tank level control — TIA Portal V19
FUNCTION_BLOCK "FB_TankControl"
VAR_INPUT
    xStart    : BOOL;
    rSetpoint : REAL;   // desired level in %
END_VAR
VAR_OUTPUT
    xValveOpen : BOOL;
END_VAR
VAR
    tFill  : TON;
    rLevel : REAL;
END_VAR

REGION Main logic
    tFill(IN := xStart AND rLevel < rSetpoint, PT := T#10S);
    xValveOpen := tFill.Q;

    IF rLevel >= rSetpoint THEN
        xValveOpen := FALSE;
    END_IF;
END_REGION

END_FUNCTION_BLOCK
```

---

## Features

### Phase 1 — Foundation ✅
- **File type recognition** — `.scl` and `.SCL` extensions registered with a custom icon
- **Syntax highlighting** for:
  - Block keywords (`FUNCTION_BLOCK`, `FUNCTION`, `DATA_BLOCK`, `ORGANIZATION_BLOCK`, `TYPE`)
  - Variable section keywords (`VAR`, `VAR_INPUT`, `VAR_OUTPUT`, `VAR_IN_OUT`, `VAR_TEMP`, `VAR_STATIC`)
  - Control flow (`IF/THEN/ELSIF/ELSE/END_IF`, `CASE/OF`, `FOR/TO/BY/DO`, `WHILE`, `REPEAT/UNTIL`, `GOTO`, `RETURN`, `EXIT`, `CONTINUE`)
  - All elementary data types (`BOOL`, `INT`, `DINT`, `REAL`, `LREAL`, `WORD`, `DWORD`, `TIME`, `DATE`, `TOD`, `S5TIME`, `DATE_AND_TIME`, …)
  - Parameter types (`TIMER`, `COUNTER`, `POINTER`, `ANY`, `BLOCK_FB`, `BLOCK_FC`, `BLOCK_DB`, …)
  - Logical and arithmetic operators (`AND`, `OR`, `XOR`, `NOT`, `MOD`, `DIV`)
  - Literals — boolean, integer (decimal/hex/octal/binary with underscores), real, string, time
  - Typed time literals (`T#5S`, `DT#2024-01-01-12:00:00`, `TOD#08:30:00`, `S5T#1H`)
  - **TIA Portal global tags** — `"QuotedIdentifier"` highlighted as global variable
  - **TIA Portal local variables** — `#localVar` highlighted as local variable
  - **Siemens memory access** — `%MW10`, `%I0.0`, `%Q2.3`
  - Comments — `//` line comments and `(* block comments *)`
  - Combined assignment operators — `+=`, `-=`, `*=`, `/=`
  - Output parameter operator — `=>`

### Phase 2 — Parser & Editor Basics ✅
- **Full BNF grammar** (`Scl.bnf`) generated via Grammar-Kit, producing a complete PSI tree
  - All block declarations with optional `CONST`/`LABEL` sections
  - `ARRAY[x..y] OF type` type references (up to 6 dimensions)
  - `STRING[n]` with optional length
  - Inline labeled statements (`LABEL1: statement`)
  - `GOTO LABEL` jump statements
  - `REGION … END_REGION` (TIA Portal)
  - DB assignment sections (`BEGIN … END_DATA_BLOCK`)
  - Named block arguments: input (`:=`) and output (`=>`)
  - Correct operator precedence (11 levels per SCLV4 §13.1)
  - Quoted block names: `FUNCTION_BLOCK "FB_Diagnostic"` accepted
- **Code folding** — collapses blocks, var sections, IF/FOR/WHILE/REPEAT/CASE, CONST sections, REGION blocks, and long block comments
- **Line and block commenting** — `Ctrl+/` inserts `//`, `Ctrl+Shift+/` wraps in `(* … *)`

---

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| 1 — Foundation | ✅ Done | Lexer, file type, syntax highlighting |
| 2 — Parser & Editor | ✅ Done | BNF grammar, PSI tree, folding, commenter |
| 3 — Completion & Navigation | 🔜 Next | Code completion, go-to definition, find usages, live templates |
| 4 — HW-Aware Linter | 🔜 Planned | Static analysis rules per CPU family (S7-1200 / S7-1500 / S7-300) |
| 5 — MCP Server | 🔜 Planned | Model Context Protocol server exposing SCL PSI to Claude Code and other AI agents |

---

## Building Locally

### Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21+ | Target bytecode. JDK 25 also works as runtime |
| Gradle | 9.4.1 | Managed by the wrapper (`./gradlew`) |
| IntelliJ IDEA | any | Community or Ultimate for development |

No additional IDE plugins are required — Grammar-Kit and JFlex run as Gradle tasks.

### Steps

```bash
# 1. Clone
git clone https://github.com/jefersontramonti/intellij-scl.git
cd intellij-scl

# 2. Generate the parser and lexer from grammar files
#    (must run before compileKotlin — this is wired into the Gradle task graph)
./gradlew generateSclParser generateSclLexer

# 3. Compile
./gradlew compileKotlin

# 4. Launch a sandbox IntelliJ instance with the plugin loaded
#    Close IntelliJ IDEA first to avoid file-lock on pdfbox-3.0.7.jar
./gradlew runIde

# 5. Build the distributable plugin zip
./gradlew buildPlugin
# Output: build/distributions/scl-language-support-1.0.0.zip
```

> **Note:** `./gradlew runIde` requires IntelliJ IDEA to be closed beforehand.  
> The sandbox caches the PDFBox dependency jar and Windows locks it while IDEA is running.

### Project Structure

```
scl-plugin/
├── build.gradle.kts                    # Gradle build — versions, dependencies, task wiring
├── gradle.properties                   # pluginVersion, pluginSinceBuild
├── settings.gradle.kts                 # IntelliJ Platform Gradle Plugin repository
│
├── src/main/
│   ├── kotlin/com/scl/plugin/
│   │   ├── language/                   # SclLanguage, SclFileType
│   │   ├── psi/                        # SclFile, SclTokenTypes, SclElementType
│   │   ├── lexer/                      # SclLexerAdapter (wraps generated SclLexer)
│   │   ├── parser/                     # SclParserDefinition
│   │   ├── highlighting/               # SclSyntaxHighlighter, SclSyntaxHighlighterFactory
│   │   ├── folding/                    # SclFoldingBuilder
│   │   └── SclCommenter.kt             # Line and block commenter
│   │
│   ├── resources/
│   │   ├── META-INF/plugin.xml         # Extension point registrations
│   │   ├── grammar/
│   │   │   ├── Scl.bnf                 # Grammar-Kit BNF → generates parser + PSI
│   │   │   └── Scl.flex               # JFlex lexer definition
│   │   └── icons/scl.svg              # File type icon
│   │
│   └── gen/                            # ⚠ Generated — do not edit
│       └── com/scl/plugin/
│           ├── lexer/SclLexer.java     # Generated by JFlex from Scl.flex
│           ├── parser/SclParser.java   # Generated by Grammar-Kit from Scl.bnf
│           └── psi/                    # Generated PSI interfaces and implementations
│
└── docs/
    ├── scl4.md                         # SCLV4 reference manual (S7-300/400)
    └── creating SCL programs.md        # TIA Portal V19 programming guide
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
| PDF processing | Apache PDFBox | 3.0.7 |

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
