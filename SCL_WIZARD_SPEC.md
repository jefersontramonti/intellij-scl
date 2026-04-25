# ESPECIFICAÇÃO: New Project Wizard SCL — Fase 8
**Documento para Claude Code CLI**

---

## 0. Comportamento Esperado

Ao criar um novo projeto no IntelliJ, o SCL aparece na lista de linguagens:

```
New Project
├── Java
├── Kotlin
├── Python
├── SCL (Siemens TIA Portal)   ← novo
├── ...
```

Ao selecionar SCL, o wizard mostra:

```
┌─────────────────────────────────────────────┐
│  New SCL Project                            │
│  ─────────────────────────────────────────  │
│  Project name:  [MyProject            ]     │
│  Location:      [C:\Projects\...      ] 📁  │
│                                             │
│  CPU Target                                 │
│  ○ S7-1200    ● S7-1500                     │
│                                             │
│  Template                                   │
│  ● Empty Project                            │
│  ○ Basic FB + OB                            │
│  ○ FB with FSM (State Machine)              │
│                                             │
│           [Cancel]  [Create]                │
└─────────────────────────────────────────────┘
```

Ao clicar em **Create**, o projeto é criado com:

```
MyProject/
├── FBs/
│   └── FB_Main.scl
├── FCs/
│   └── FC_Utils.scl
├── OBs/
│   └── OB_Main.scl
└── UDTs/
    └── UDT_Config.scl
```

E o arquivo `FB_Main.scl` é aberto automaticamente no editor.

---

## 1. Arquitetura — 4 Componentes

```
src/main/kotlin/com/scl/plugin/wizard/
├── SclModuleType.kt          ← registra SCL no New Project dialog
├── SclProjectWizardStep.kt   ← UI do wizard (campos + opções)
├── SclProjectGenerator.kt    ← cria os arquivos no disco
└── SclTemplates.kt           ← conteúdo dos arquivos por template
```

---

## 2. SclModuleType.kt

```kotlin
// src/main/kotlin/com/scl/plugin/wizard/SclModuleType.kt

class SclModuleType : ModuleType<SclModuleBuilder>("SCL_MODULE_TYPE") {

    override fun createModuleBuilder(): SclModuleBuilder = SclModuleBuilder()

    override fun getName(): String = "SCL"

    override fun getDescription(): String =
        "Siemens TIA Portal SCL (Structured Control Language) project " +
        "for S7-1200/S7-1500 PLCs"

    // Ícone SCL — usar o ícone do arquivo .scl já definido no plugin
    override fun getNodeIcon(isOpened: Boolean): Icon =
        SclIcons.FILE  // ícone já existente no plugin

    companion object {
        val INSTANCE: SclModuleType
            get() = ModuleTypeManager.getInstance()
                .findByID("SCL_MODULE_TYPE") as SclModuleType
    }
}
```

---

## 3. SclModuleBuilder.kt

```kotlin
// src/main/kotlin/com/scl/plugin/wizard/SclModuleBuilder.kt

class SclModuleBuilder : ModuleBuilder() {

    // Opções configuradas pelo wizard
    var cpuTarget: CpuTarget = CpuTarget.S7_1200
    var template: ProjectTemplate = ProjectTemplate.BASIC_FB_OB

    enum class CpuTarget { S7_1200, S7_1500 }

    enum class ProjectTemplate {
        EMPTY,          // só cria as pastas
        BASIC_FB_OB,    // FB_Main + OB_Main (padrão)
        FSM             // FB com state machine + OB
    }

    override fun getModuleType(): ModuleType<*> = SclModuleType.INSTANCE

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = createAndGetContentEntry()
        modifiableRootModel.addContentEntry(root)

        // Criar estrutura de pastas
        val generator = SclProjectGenerator(
            root = root,
            projectName = modifiableRootModel.module.name,
            cpuTarget = cpuTarget,
            template = template
        )
        generator.generate()
    }

    private fun createAndGetContentEntry(): VirtualFile {
        val path = contentEntryPath ?: throw IllegalStateException("No content entry path")
        VfsUtil.createDirectories(path)
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
            ?: throw IllegalStateException("Cannot find content entry: $path")
    }

    override fun getCustomOptionsStep(
        context: WizardContext,
        parentDisposable: Disposable
    ): ModuleWizardStep = SclProjectWizardStep(this)
}
```

---

## 4. SclProjectWizardStep.kt

```kotlin
// src/main/kotlin/com/scl/plugin/wizard/SclProjectWizardStep.kt

class SclProjectWizardStep(
    private val builder: SclModuleBuilder
) : ModuleWizardStep() {

    private val panel = JPanel(GridBagLayout())

    // CPU Target radio buttons
    private val rbS7_1200 = JRadioButton("S7-1200")
    private val rbS7_1500 = JRadioButton("S7-1500")

    // Template radio buttons
    private val rbEmpty   = JRadioButton("Empty Project")
    private val rbBasic   = JRadioButton("Basic FB + OB (recommended)")
    private val rbFsm     = JRadioButton("FB with State Machine + OB")

    init {
        // CPU Target group
        val cpuGroup = ButtonGroup()
        cpuGroup.add(rbS7_1200)
        cpuGroup.add(rbS7_1500)
        rbS7_1200.isSelected = true  // default S7-1200

        // Template group
        val templateGroup = ButtonGroup()
        templateGroup.add(rbEmpty)
        templateGroup.add(rbBasic)
        templateGroup.add(rbFsm)
        rbBasic.isSelected = true  // default Basic

        buildUI()
    }

    private fun buildUI() {
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 8, 4, 8)
        }

        // Seção CPU Target
        gbc.gridy = 0; gbc.gridx = 0; gbc.gridwidth = 2
        panel.add(JLabel("CPU Target").also {
            it.font = it.font.deriveFont(Font.BOLD)
        }, gbc)

        gbc.gridy = 1; gbc.gridwidth = 1
        panel.add(rbS7_1200, gbc)
        gbc.gridx = 1
        panel.add(rbS7_1500, gbc)

        // Separador
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 2
        panel.add(JSeparator(), gbc)

        // Seção Template
        gbc.gridy = 3
        panel.add(JLabel("Template").also {
            it.font = it.font.deriveFont(Font.BOLD)
        }, gbc)

        gbc.gridy = 4; gbc.gridwidth = 2
        panel.add(rbEmpty, gbc)
        gbc.gridy = 5
        panel.add(rbBasic, gbc)
        gbc.gridy = 6
        panel.add(rbFsm, gbc)

        // Descrição do template selecionado
        val descLabel = JLabel("Creates FB_Main.scl + OB_Main.scl")
        descLabel.foreground = UIManager.getColor("Label.disabledForeground")
        gbc.gridy = 7; gbc.insets = Insets(0, 24, 8, 8)
        panel.add(descLabel, gbc)

        // Atualizar descrição ao mudar template
        listOf(rbEmpty, rbBasic, rbFsm).forEach { rb ->
            rb.addActionListener {
                descLabel.text = when {
                    rbEmpty.isSelected -> "Creates empty folder structure only"
                    rbBasic.isSelected -> "Creates FB_Main.scl + OB_Main.scl + FC_Utils.scl + UDT_Config.scl"
                    rbFsm.isSelected   -> "Creates FB with 5-state FSM (IDLE/RUNNING/FAULT/EMERGENCY) + OB_Main.scl"
                    else -> ""
                }
            }
        }
    }

    override fun getComponent(): JComponent = panel

    override fun updateDataModel() {
        builder.cpuTarget = when {
            rbS7_1500.isSelected -> SclModuleBuilder.CpuTarget.S7_1500
            else -> SclModuleBuilder.CpuTarget.S7_1200
        }
        builder.template = when {
            rbEmpty.isSelected -> SclModuleBuilder.ProjectTemplate.EMPTY
            rbFsm.isSelected   -> SclModuleBuilder.ProjectTemplate.FSM
            else               -> SclModuleBuilder.ProjectTemplate.BASIC_FB_OB
        }
    }
}
```

---

## 5. SclProjectGenerator.kt

```kotlin
// src/main/kotlin/com/scl/plugin/wizard/SclProjectGenerator.kt

class SclProjectGenerator(
    private val root: VirtualFile,
    private val projectName: String,
    private val cpuTarget: SclModuleBuilder.CpuTarget,
    private val template: SclModuleBuilder.ProjectTemplate
) {

    fun generate() {
        ApplicationManager.getApplication().runWriteAction {
            // Criar pastas
            val fbsDir  = root.createChildDirectory(this, "FBs")
            val fcsDir  = root.createChildDirectory(this, "FCs")
            val obsDir  = root.createChildDirectory(this, "OBs")
            val udtsDir = root.createChildDirectory(this, "UDTs")

            when (template) {
                SclModuleBuilder.ProjectTemplate.EMPTY -> {
                    // Só pastas — sem arquivos
                }
                SclModuleBuilder.ProjectTemplate.BASIC_FB_OB -> {
                    createFile(fbsDir,  "FB_Main.scl",    SclTemplates.functionBlock("FB_Main"))
                    createFile(fcsDir,  "FC_Utils.scl",   SclTemplates.function("FC_Utils"))
                    createFile(obsDir,  "OB_Main.scl",    SclTemplates.organizationBlock("OB_Main"))
                    createFile(udtsDir, "UDT_Config.scl", SclTemplates.udt("UDT_Config"))
                }
                SclModuleBuilder.ProjectTemplate.FSM -> {
                    createFile(fbsDir,  "FB_Main.scl",    SclTemplates.functionBlockFsm("FB_Main"))
                    createFile(obsDir,  "OB_Main.scl",    SclTemplates.organizationBlock("OB_Main"))
                }
            }
        }

        // Abrir FB_Main.scl no editor após criação
        val fbMain = root.findFileByRelativePath("FBs/FB_Main.scl") ?: return
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(
                ProjectManager.getInstance().openProjects.firstOrNull() ?: return@invokeLater
            ).openFile(fbMain, true)
        }
    }

    private fun createFile(dir: VirtualFile, name: String, content: String) {
        val file = dir.createChildData(this, name)
        file.setBinaryContent(content.toByteArray(Charsets.UTF_8))
    }
}
```

---

## 6. SclTemplates.kt

```kotlin
// src/main/kotlin/com/scl/plugin/wizard/SclTemplates.kt

object SclTemplates {

    // ── FUNCTION_BLOCK mínimo ──────────────────────────────────────
    fun functionBlock(name: String): String = """
FUNCTION_BLOCK "$name"
{ S7_Optimized_Access := 'TRUE' }

VAR_INPUT

END_VAR

VAR_OUTPUT

END_VAR

VAR

END_VAR

BEGIN

END_FUNCTION_BLOCK
""".trimIndent()

    // ── FUNCTION mínimo ───────────────────────────────────────────
    fun function(name: String): String = """
FUNCTION "$name" : Void
{ S7_Optimized_Access := 'TRUE' }

VAR_INPUT

END_VAR

VAR_TEMP

END_VAR

BEGIN

END_FUNCTION
""".trimIndent()

    // ── ORGANIZATION_BLOCK mínimo ─────────────────────────────────
    fun organizationBlock(name: String): String = """
ORGANIZATION_BLOCK "$name"
{ S7_Optimized_Access := 'TRUE' }

VAR_TEMP

END_VAR

BEGIN

END_ORGANIZATION_BLOCK
""".trimIndent()

    // ── TYPE (UDT) mínimo ─────────────────────────────────────────
    fun udt(name: String): String = """
TYPE "$name"
    STRUCT

    END_STRUCT
END_TYPE
""".trimIndent()

    // ── FUNCTION_BLOCK com FSM de 5 estados ──────────────────────
    fun functionBlockFsm(name: String): String = """
FUNCTION_BLOCK "$name"
{ S7_Optimized_Access := 'TRUE' }
//=============================================================
// $name
// Estados FSM:
//   0  IDLE      → aguardando comando
//   1  RUNNING   → em execução
//  98  FAULT     → falha ativa
//  99  EMERGENCY → parada de emergência
//=============================================================

VAR_INPUT
    i_EStop : Bool;   // E-Stop NF — FALSE = emergência
    i_Start : Bool;   // Comando Start
    i_Stop  : Bool;   // Comando Stop
    i_Reset : Bool;   // Reset de falha
END_VAR

VAR_OUTPUT
    o_Running   : Bool;   // TRUE quando em operação
    o_Fault     : Bool;   // TRUE quando há falha
    o_State     : Int;    // Estado FSM atual
    o_FaultCode : Int;    // Código de falha
END_VAR

VAR
    s_State     : Int;    // Estado FSM interno
    s_StartEdge : R_TRIG; // Detecção borda Start
END_VAR

BEGIN
    REGION Edge Detection
        s_StartEdge(CLK := i_Start);
    END_REGION

    REGION E-Stop Check
        IF NOT i_EStop THEN
            s_State     := 99;
            o_FaultCode := 9800;
        END_IF;
    END_REGION

    REGION State Machine
        CASE s_State OF

            0: // IDLE
                o_Running := FALSE;
                IF s_StartEdge.Q AND i_EStop THEN
                    s_State := 1;
                END_IF;

            1: // RUNNING
                o_Running := TRUE;
                IF i_Stop THEN
                    s_State := 0;
                END_IF;

            98: // FAULT
                o_Running := FALSE;
                o_Fault   := TRUE;
                IF i_Reset AND i_EStop THEN
                    s_State     := 0;
                    o_FaultCode := 0;
                    o_Fault     := FALSE;
                END_IF;

            99: // EMERGENCY
                o_Running := FALSE;
                o_Fault   := TRUE;
                IF i_EStop THEN
                    s_State := 98;
                END_IF;

            ELSE
                s_State     := 98;
                o_FaultCode := 9999;

        END_CASE;
    END_REGION

    REGION Output Assignment
        o_State := s_State;
    END_REGION

END_FUNCTION_BLOCK
""".trimIndent()
}
```

---

## 7. Registrar no plugin.xml

```xml
<!-- New Project Wizard SCL -->
<moduleType
    id="SCL_MODULE_TYPE"
    implementationClass="com.scl.plugin.wizard.SclModuleType"/>
```

---

## 8. Checklist de Testes

```
[ ] File → New Project → lista mostra "SCL" com ícone
[ ] Wizard abre com campos: CPU Target + Template
[ ] S7-1200 selecionado por padrão
[ ] Template "Basic FB + OB" selecionado por padrão
[ ] Criar projeto "TestProject" com Basic template:
    → FBs/FB_Main.scl existe
    → FCs/FC_Utils.scl existe
    → OBs/OB_Main.scl existe
    → UDTs/UDT_Config.scl existe
[ ] FB_Main.scl abre automaticamente no editor após criação
[ ] FB_Main.scl tem syntax highlighting correto
[ ] FB_Main.scl compila no TIA Portal sem erros
[ ] Template Empty → só cria pastas, sem arquivos
[ ] Template FSM → FB_Main.scl com CASE de 5 estados
[ ] Pasta SCL aparece na aba SCL do Project View
```

---

## 9. Problemas Comuns

### ❌ ERRO 1 — ModuleType não aparece no wizard
```
CAUSA: id no plugin.xml diferente do retornado por getId()
FIX: garantir que o id="SCL_MODULE_TYPE" bate exatamente
     com o string retornado pelo companion object
```

### ❌ ERRO 2 — Arquivo criado mas sem conteúdo
```
CAUSA: setBinaryContent chamado fora de runWriteAction
FIX: toda criação de VirtualFile deve estar dentro de:
     ApplicationManager.getApplication().runWriteAction { ... }
```

### ❌ ERRO 3 — Pasta não encontrada após criação
```
CAUSA: VirtualFile não foi refreshed após criação
FIX: chamar LocalFileSystem.getInstance().refresh(false)
     após criar os arquivos
```

### ❌ ERRO 4 — Editor não abre FB_Main.scl
```
CAUSA: FileEditorManager chamado antes do projeto estar pronto
FIX: usar invokeLater para atrasar a abertura do editor:
     ApplicationManager.getApplication().invokeLater { ... }
```
