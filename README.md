# intellij-scl

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](https://github.com/jefersontramonti/intellij-scl)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2026.1-blue?style=flat-square&logo=intellijidea)](https://plugins.jetbrains.com/docs/intellij/welcome.html)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7f52ff?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green?style=flat-square)](LICENSE)

Full language support for **SCL (Structured Control Language)** inside IntelliJ IDEA.  
SCL is the IEC 61131-3 Structured Text dialect used in Siemens **TIA Portal V19** (S7-1200, S7-1500) and the classic **STEP 7** environment (S7-300/400).

---

## What is this plugin?

`intellij-scl` brings industrial PLC programming into a modern IDE. Instead of writing SCL exclusively inside TIA Portal's limited editor, you can use IntelliJ IDEA with syntax highlighting, smart completion, go-to-definition, find usages, a hardware-aware linter, a Safety linter, a code formatter, a new-project wizard, and a full MCP server — the same infrastructure that powers Java, Kotlin, and Python support in JetBrains IDEs.

The plugin is built on the **IntelliJ Platform SDK** and uses **Grammar-Kit + JFlex** to generate a complete lexer and parser from formal grammar files (`Scl.bnf`, `Scl.flex`). The grammar is derived directly from the official Siemens SCLV4 specification (Appendices B and C) and the TIA Portal V19 programming manual.

---

## Features

### Phase 1 — Foundation ✅
- **File type recognition** — `.scl` and `.SCL` extensions registered with a custom icon
- **Syntax highlighting** for keywords, types, operators, literals, comments, global tags (`"QuotedId"`), local variables (`#var`), and Siemens memory addresses (`%MW10`, `%I0.0`, `%Q2.3`)
- **Combined assignment operators** — `+=`, `-=`, `*=`, `/=`

### Phase 2 — Parser & Editor ✅
- **Full BNF grammar** (`Scl.bnf`) generating a complete PSI tree via Grammar-Kit
  - All block types: `FUNCTION_BLOCK`, `FUNCTION`, `ORGANIZATION_BLOCK`, `DATA_BLOCK`, `TYPE`
  - `ARRAY[x..y] OF type` up to 6 dimensions, `STRING[n]`, `STRUCT`
  - `CONST` and `LABEL` sections (SCLV4 Appendix C.2)
  - `REGION … END_REGION` with **multi-word names** — `REGION Edge Detection` accepted (TIA Portal parity)
  - `GOTO`, labeled statements, correct operator precedence (11 levels per SCLV4 §13.1)
  - Quoted block names: `FUNCTION_BLOCK "FB_Motor"` accepted
- **Code folding** — blocks, var sections, IF/FOR/WHILE/REPEAT/CASE, CONST, REGION, block comments
- **Line and block commenting** — `Ctrl+/` for `//`, `Ctrl+Shift+/` for `(* … *)`
- **Brace matching** — `BEGIN/END`, `IF/END_IF`, `FOR/END_FOR`, `FUNCTION_BLOCK/END_FUNCTION_BLOCK`, etc.

### Phase 3 — Completion & Documentation ✅
- **Code completion** (`Ctrl+Space`) — variables, block names, all SCL keywords and types
- **Dot-completion** — `fbInstance.` suggests output parameters
- **Builtin function catalog** — `SQRT`, `ABS`, `LEN`, `LEFT`, `RIGHT`, `MID`, `CONCAT`, `TON`, `TOF`, `TP`, `CTU`, `CTD`, `CTUD`, and 40+ more with full signatures
- **Live templates** — `if`, `ife`, `case`, `for`, `while`, `repeat`, `fb`, `var`, `region`, `ton` expand to full SCL blocks
- **Hover documentation** (`Ctrl+Q`) — variable type, section, and inline comment
- **Parameter info popup** (`Ctrl+P`) — FB/FC parameter signatures inline

### Phase 4 — Formatter & Structure View ✅
- **Code formatter** (`Ctrl+Alt+L`) — indentation, spacing around operators, alignment of `:=` in var declarations, blank lines between sections
- **Structure View** (`Alt+7`) — tree of blocks → VAR sections → variables → body statements (REGION, CASE, IF, FOR, WHILE)
- **Color settings page** — customize all SCL token colors via *Settings → Editor → Color Scheme → SCL*

### Phase 4B — HW-Aware Linter ✅
- **CPU target selector** — per-project setting in *Settings → Tools → SCL CPU Target*: family (`S7-1200`, `S7-1500`, `S7-300/400`) + firmware version
- **Status bar widget** — shows the active target in the bottom bar (click to switch)
- **7 inspections** (group **SCL**, configurable in *Settings → Editor → Inspections*):

| Inspection | Level | Description |
|---|---|---|
| Absolute memory address | WARNING | `%MW`, `%I`, `%Q` — use symbolic tags instead |
| Unnecessary boolean IF | WARNING | `IF boolVar = TRUE THEN` → simplify to `IF boolVar THEN` |
| CASE without ELSE | WARNING | Missing ELSE to handle unexpected states |
| FOR index type | ERROR | `DINT`/`UDINT` index not supported on S7-300/400 |
| VAR_TEMP uninitialized | WARNING | `VAR_TEMP` read before first assignment |
| Unsupported type | ERROR | `LREAL`, `LINT`, `ULINT` not supported on selected CPU |
| HW Compatibility (5 rules) | WARNING/ERROR | `TIME_TO_DINT`, `LREAL`, `%Mx` conflict, blink bug, CASE deadlock |

### Phase 6 — Navigation & Refactoring ✅
- **Go to Declaration** (`Ctrl+Click` / `Ctrl+B`) — jumps to variable or block declaration
- **Find Usages** (`Alt+F7`) — finds all references to a symbol across the project
- **Rename** (`Shift+F6`) — renames variables and blocks across all usages
- **SCL Project View** — dedicated pane grouping `.scl` files by block type (FB, FC, OB, UDT, DB)
- **Project View decorator** — standard Project View shows block-type icons (FB = class, FC = function, OB = plugin, UDT = record)

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

### Phase 8 — New Project Wizard ✅
- **New SCL Project** dialog in *File → New → Project → SCL*
- **CPU Target selection** — S7-1200 / S7-1500 / S7-300/400 at project creation
- **Template selection**:
  - *Empty* — bare project structure
  - *Basic FB + OB* — starter `FUNCTION_BLOCK` and `ORGANIZATION_BLOCK`
  - *State Machine (FSM)* — FB with a CASE-based state machine and REGION sections
- **Auto-generates** the standard folder structure: `FBs/`, `FCs/`, `OBs/`, `UDTs/`, `DBs/`

### Phase 9 — Safety Mode SCL Linter ✅
Enforces Siemens **Programming Guideline Safety V1.3** and **STEP 7 Safety V21** rules for fail-safe F-programs targeting S7-1200F / S7-1500F CPUs.

**Detection:** any block whose name starts with `F_` is treated as a Safety block (e.g. `F_EStop`, `"F_GuardDoor"`).

**Project View:** `F_*.scl` files are decorated with a red Safety icon, distinct from standard blue/green/orange block icons.

**8 inspections** (group **SCL Safety**, configurable in *Settings → Editor → Inspections*):

| ID | Level | Rule |
|---|---|---|
| `SclSafetyGoto` | ERROR | `GOTO` is prohibited in Safety programs (Guideline §4.1.1) |
| `SclSafetyType` | ERROR | `LINT`, `ULINT`, `LWORD`, `LTIME`, `LTOD`, `LDT`, `WSTRING` not supported (SIL3 — IEC 61508) |
| `SclSafetyType` | WARNING | `LREAL` has limited support — verify F-CPU firmware |
| `SclSafetyGlobalData` | ERROR | Global DB access (`"DB_x".field`) not allowed inside F-FB — pass as parameter (Guideline §3.7) |
| `SclSafetyJump` | WARNING | `EXIT` / `CONTINUE` jumps should be minimized in Safety programs |
| `SclSafetyHeader` | WARNING | Safety block missing required header (SIL level, F-Signature, author — Guideline §3.1.6) |
| `SclSafetyCaseElse` | WARNING | `CASE` without `ELSE` — Safety programs must return to a safe state |
| `SclSafetyValueStatus` | WARNING | F-I/O channel (`DI_*` / `DO_*`) accessed without `_VS` value-status check (Guideline §3.4) |

**5 Safety live templates** (`fsafety`, `festop`, `fguard`, `fmain`, `fudtq`) for quick scaffolding of Safety blocks with the mandatory header pre-filled.

**References:** [Programming Guideline Safety V1.3](https://support.industry.siemens.com/cs/ww/en/view/109750255) · [SIMATIC Safety V21](https://support.industry.siemens.com/cs/attachments/54110126/ProgFAILenUS_en-US.pdf)

---

## Building Locally

### Requirements

| Tool | Version |
|------|---------|
| JDK | 21+ |
| Gradle | 9.4.1 (wrapper) |
| IntelliJ IDEA | Community or Ultimate |

### Steps

```bash
# 1. Clone
git clone https://github.com/jefersontramonti/intellij-scl.git
cd intellij-scl

# 2. Generate lexer and parser from grammar files
./gradlew generateSclParser generateSclLexer

# 3. Launch a sandbox IntelliJ with the plugin loaded
#    Close any running IntelliJ IDEA instance first (Windows file-lock)
./gradlew runIde

# 4. Build the distributable plugin zip
./gradlew buildPlugin
# Output: build/distributions/scl-language-support-1.0.0.zip
```

### Project Structure

```
scl-plugin/
├── build.gradle.kts
├── gradle.properties
│
├── src/main/
│   ├── kotlin/com/scl/plugin/
│   │   ├── language/          # SclLanguage, SclFileType
│   │   ├── psi/               # SclFile, SclTokenTypes, SclNamedElement
│   │   ├── lexer/             # SclLexerAdapter
│   │   ├── parser/            # SclParserDefinition
│   │   ├── highlighting/      # SclSyntaxHighlighter, SclColorSettingsPage
│   │   ├── folding/           # SclFoldingBuilder
│   │   ├── completion/        # SclCompletionContributor, SclBuiltinFunctions
│   │   ├── documentation/     # SclDocumentationTargetProvider
│   │   ├── formatting/        # SclFormattingModelBuilder, SclBlock, SclSpacingBuilder
│   │   ├── structure/         # SclStructureViewFactory, SclBodyElement
│   │   ├── linter/            # HW-aware linter, CPU settings, status bar widget
│   │   │   ├── inspections/   # 6 standard LocalInspectionTool implementations
│   │   │   └── safety/        # 8 Safety inspections + SclSafetyUtils
│   │   ├── reference/         # GotoDeclarationHandler, RenameHandler, FindUsages
│   │   ├── view/              # SclProjectViewPane, SclProjectViewNodeDecorator
│   │   ├── wizard/            # SclModuleBuilder, SclProjectGenerator, SclTemplates
│   │   ├── mcp/               # SclToolset (6 MCP tools)
│   │   └── SclIcons.kt
│   │
│   ├── resources/
│   │   ├── META-INF/plugin.xml
│   │   ├── grammar/
│   │   │   ├── Scl.bnf        # Grammar-Kit BNF grammar
│   │   │   └── Scl.flex       # JFlex lexer
│   │   ├── icons/scl.svg
│   │   └── liveTemplates/SCL.xml   # 15 live templates (10 standard + 5 Safety)
│   │
│   └── gen/                   # ⚠ Generated — do not edit
│       └── com/scl/plugin/
│           ├── lexer/SclLexer.java
│           ├── parser/SclParser.java
│           └── psi/
│
└── examples/
    ├── FBs/                   # FB_PluginTest_Safe
    ├── FCs/                   # FC_ScaleValue
    ├── OBs/                   # OB1
    ├── DBs/                   # DB_GlobalData
    ├── UDTs/                  # UDT_ConveyorStatus, UDT_MotorData
    └── F_SafetyTest.scl       # Safety linter test file (all 8 rules)
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
  TIA Portal extensions: `REGION/END_REGION` (multi-word names), combined assignments (`+=`), output operator (`=>`), quoted identifiers (`"TagName"`), local variable prefix (`#var`).
- **Programming Guideline Safety V1.3** — Siemens 03/2023  
  Basis for all Phase 9 Safety linter rules.

---

## Known Limitations

- Tested on **IntelliJ IDEA 2026.1** only — older platform versions untested
- **MCP Server** requires the *IntelliJ MCP Server* plugin to be enabled in the host IDE
- **Safety F-Signature** validation requires TIA Portal — not generated by this plugin (by design; F-Signature is computed internally by the TIA Portal Safety compiler after formal acceptance)
- **No unit tests yet** — parser edge cases may produce unexpected PSI trees
- **Project paths with spaces** may cause `scl_validate_file` (MCP tool) to fail

---

## License

[MIT](LICENSE) — free to use, modify, and distribute.
