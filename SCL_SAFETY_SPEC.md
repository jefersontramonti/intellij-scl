# ESPECIFICAÇÃO: Safety Mode SCL — Fase 9
**Baseado em:**
- Programming Guideline Safety for SIMATIC S7-1200/1500 (V1.3, 03/2023)
- SIMATIC Safety — Configuring and Programming (STEP 7 Safety V21, 11/2025)

---

## 0. Por que Safety é diferente

O programa Safety (F-program) não é uma extensão do SCL padrão.
É um subconjunto restrito com regras adicionais obrigatórias para
garantir certificação SIL3 (IEC 61508) e PLe Categoria 4 (ISO 13849-1).

```
Standard SCL           Safety SCL (F-program)
─────────────────────────────────────────────────────
Qualquer tipo          Tipos restritos (sem LINT, LWORD, LTIME)
GOTO permitido         GOTO PROIBIDO
Jumps livres           Evitar jumps (usar state machines)
Dados globais livres   Dados globais apenas no Main Safety
Nesting ilimitado      MÁXIMO 8 níveis de chamada
Sem senha              Senha F-CPU obrigatória
Sem aceitação          Aceitação formal obrigatória (TÜV)
Compilação normal      F-signature gerada a cada compilação
```

---

## 1. CPUs Fail-Safe suportadas

```
S7-1200F (G1): CPU 1211FC, 1212FC, 1214FC, 1215FC, 1217FC
               Artigo: 6ES721x-1xF40....

S7-1200F (G2): CPU 1211FC, 1212FC, 1214FC, 1215FC
               Artigo: 6ES721x-1xF50....
               Novo: suporte a Safety Unit (TIA Portal V18+)

S7-1500F:      CPU 1511F, 1513F, 1515F, 1516F, 1517F, 1518F
               Inclui: HF, ET 200SP F-CPU, Software Controller F
               Inclui: SIMATIC Drive Controller

S7-1500HF:     Redundante (R/H) — máxima disponibilidade
```

---

## 2. Estrutura de Arquivos Safety

```
MeuProjeto/
├── Standard/              ← programa padrão (como hoje)
│   ├── FBs/
│   ├── FCs/
│   └── OBs/
└── Safety/                ← programa fail-safe (novo)
    ├── F_FBs/             ← F-Function Blocks (prefixo F_)
    │   ├── F_EStop.scl
    │   ├── F_GuardDoor.scl
    │   └── F_DriveControl.scl
    ├── F_FCs/             ← F-Functions
    │   └── F_SafetyLogic.scl
    ├── F_UDTs/            ← F-compliant PLC data types
    │   └── F_UDT_Machine.scl
    └── MainSafety.scl     ← Main Safety OB (equivalente ao OB1)
```

---

## 3. Estrutura Mínima dos Blocos Safety

### F-Function Block
```scl
FUNCTION_BLOCK "F_EStop"
// Safety block — STEP 7 Safety V21
// SIL: 3 | PL: e | Category: 4
// Author: [nome] | Date: [data]
// Signature: [gerada automaticamente pelo TIA Portal]
//
// RESTRICTIONS:
//   - No GOTO
//   - Max 8 call levels
//   - No global data access (use parameters only)
//   - F-UDTs only in VAR sections
//   - No LINT, LWORD, LTIME, LREAL in Safety context

VAR_INPUT
    i_EStopCh1   : Bool;   // Canal 1 — NF contact
    i_EStopCh2   : Bool;   // Canal 2 — redundância
    i_AckButton  : Bool;   // Botão de reconhecimento
END_VAR

VAR_OUTPUT
    o_SafetyRelease : Bool;  // TRUE = seguro para operar
    o_DiagCode      : Int;   // Código diagnóstico
END_VAR

VAR
    s_AckEdge : R_TRIG;     // Borda do botão de ack
END_VAR

BEGIN
// Safety logic aqui
// REGRA: use apenas AND/OR — evite lógica complexa
// REGRA: state machines em vez de GOTO
END_FUNCTION_BLOCK
```

### F-UDT (F-compliant PLC Data Type)
```scl
// F-UDTs são declarados igual aos UDTs normais
// mas só podem conter tipos permitidos em Safety
TYPE "F_UDT_Machine"
    STRUCT
        SafetyRelease : Bool;   // ✅ Bool permitido
        DiagCode      : Int;    // ✅ Int permitido
        ProcessValue  : Real;   // ✅ Real permitido
        // LInt        : LInt;  // ❌ PROIBIDO em Safety
        // LTime       : LTime; // ❌ PROIBIDO em Safety
    END_STRUCT
END_TYPE
```

### Main Safety (OB — F-runtime group)
```scl
ORGANIZATION_BLOCK "Main_Safety_RTG1"
// F-runtime group — executado a cada ciclo de segurança
// Ordem de chamada obrigatória (Guideline Safety §3.1.4):
//   1. Receive blocks (F-CPU to F-CPU communication)
//   2. Error acknowledgment / reintegration of F-modules
//   3. Sensor evaluation blocks
//   4. Operating mode evaluation
//   5. Logic operations
//   6. Actuator control blocks
//   7. Send blocks (F-CPU to F-CPU communication)

VAR_TEMP
END_VAR

BEGIN
    // 1. (comunicação F-para-F se necessário)

    // 2. Reintegração de módulos F
    // "F_ACK_DB"(ACK_REI := "DB_Safety".AckReintegration);

    // 3. Sensores
    "F_EStop_DB"(
        i_EStopCh1  := "F_EStop_Ch1.VS",
        i_EStopCh2  := "F_EStop_Ch2.VS",
        i_AckButton := "DB_Standard".AckCmd
    );

    // 4. Modo de operação
    // "F_OpMode_DB"(...);

    // 5. Lógica
    // "F_SafetyLogic_DB"(...);

    // 6. Atuadores
    // "F_Drive_DB"(...);

END_ORGANIZATION_BLOCK
```

---

## 4. Regras Obrigatórias — Safety Programming

### 4.1 Tipos de dados PROIBIDOS em Safety

| Tipo | Motivo |
|---|---|
| `LINT`, `ULINT` | 64-bit — não suportado |
| `LWORD` | 64-bit — não suportado |
| `LTIME`, `LTOD`, `LDT` | Tipos de tempo 64-bit |
| Nesting de F-UDTs > 3 níveis | Complexidade proibida |

### 4.2 Construções PROIBIDAS em Safety

```scl
// ❌ PROIBIDO — GOTO em Safety:
GOTO myLabel;

// ❌ PROIBIDO — acesso a dados globais em F-FB:
"DB_Global".minhaVar := TRUE;  // somente no Main Safety

// ❌ PROIBIDO — mais de 8 níveis de chamada:
// Main → FB1 → FB2 → FB3 → FB4 → FB5 → FB6 → FB7 → FB8 → FB9 ❌

// ❌ PROIBIDO — nesting de F-UDTs em F-UDTs:
// (apenas um nível de nesting permitido)
```

### 4.3 Construções RECOMENDADAS em Safety

```scl
// ✅ Lógica AND/OR simples (Guideline §3.4):
o_SafetyRelease := i_EStopCh1 AND i_EStopCh2 AND i_GuardDoor;

// ✅ State machine em vez de GOTO (Guideline §4.1.1):
CASE s_State OF
    0: // Safe state
        o_Release := FALSE;
        IF i_AllSafe THEN s_State := 1; END_IF;
    1: // Running
        o_Release := TRUE;
        IF NOT i_AllSafe THEN s_State := 0; END_IF;
    ELSE
        s_State := 0;  // sempre voltar ao estado seguro
END_CASE;

// ✅ Multi-instância (Guideline §4.1.3):
// Declarar instâncias de F-FB dentro da VAR do bloco pai
// em vez de criar DBs separados para cada instância

// ✅ Prefixo para tags de segurança (Guideline §3.2):
// Nome reflete estado TRUE = seguro:
// "maintDoorEnable", "conveyorSafetyRelease" ← ✅
// "maintDoorClosed" ← menos claro sobre o estado lógico
```

### 4.4 Value Status — acesso a F-I/O

No S7-1200F/1500F o value status informa a validade do canal:

```scl
// Acesso correto a F-I/O com value status:
i_EStopCh1    := "DI_Safety".EStop_Ch1;      // valor
i_EStopCh1_VS := "DI_Safety".EStop_Ch1_VS;   // sufixo _VS = válido?

// Lógica com value status:
o_Release := "DI_Safety".EStop_Ch1 AND "DI_Safety".EStop_Ch1_VS;
//                         ↑ valor                    ↑ TRUE se canal válido
```

### 4.5 Troca de dados Standard ↔ Safety

Dados só fluem de forma controlada entre programas (Guideline §3.7):

```
Standard → Safety:
  Via F-DB criado no Safety program
  Copiar via FC no pre-processing do F-runtime group

Safety → Standard:
  Ler diretamente (Safety é mais restritivo, leitura é segura)
  Preferir F-UDTs como interface
```

```scl
// CORRETO — passar dado do Standard para Safety via DB:
// 1. No programa Standard:
"DB_ToSafety".AckCmd := "HMI_AckButton";

// 2. No Main Safety (pre-processing):
"DB_Safety".AckCmd := "DB_ToSafety".AckCmd;

// 3. No F-FB:
VAR_INPUT
    i_AckButton : Bool;  // recebe de DB_Safety
END_VAR
```

---

## 5. Cabeçalho Obrigatório para Blocos Safety

Baseado no template do Guideline §3.1.6:

```scl
FUNCTION_BLOCK "F_NomeDoBloco"
(*
 * Company: [sua empresa]
 * (c) Copyright [ano]
 * ─────────────────────────────────────────────────
 * Title:            F_NomeDoBloco
 * Function:         [descrição da função de segurança]
 * Library/Family:   Safety
 * Author:           [nome]
 * Tested with:      TIA Portal V19 / STEP 7 Safety V21
 * Engineering:      S7-1200F FW V4.x / S7-1500F FW V2.x
 * Safety Level:     SIL [1/2/3] / PL [a-e] Cat [1-4]
 * DC Measures:      [medidas de cobertura de diagnóstico]
 * CCF Measures:     [medidas contra falha de causa comum]
 * Restrictions:     [restrições de uso]
 * Requirements:     STEP 7 Safety Advanced
 * ─────────────────────────────────────────────────
 * Change log:
 * Version | Date       | Author    | Changes
 * --------|------------|-----------|------------------
 * 01.00   | [data]     | [nome]    | First release
 * ─────────────────────────────────────────────────
 * F-Signature: [preenchida automaticamente pelo TIA Portal após aceitação]
*)
```

---

## 6. Linter Safety — Regras para o Plugin

### 6.1 Detecção de arquivo Safety

O plugin deve detectar Safety pela presença de comentário específico
ou pelo prefixo `F_` no nome do bloco:

```kotlin
fun isSafetyBlock(element: SclFunctionBlockDeclImpl): Boolean {
    val name = element.name ?: return false
    // Convenção Siemens: blocos Safety têm prefixo F_
    return name.startsWith("F_") || name.startsWith("\"F_")
}
```

### 6.2 Regras ERRO em Safety

| ID | Condição | Mensagem |
|---|---|---|
| `SafetyGoto` | `GOTO` em bloco F_ | GOTO is prohibited in Safety programs |
| `SafetyLint` | `LINT`, `ULINT`, `LWORD` em F-block | Type not supported in Safety (SIL3) |
| `SafetyLtime` | `LTIME`, `LTOD`, `LDT` em F-block | Time type not supported in Safety |
| `SafetyGlobalData` | Acesso a `"DB_Global"` em F-FB | Global DB access not allowed in F-FB — use parameters |
| `SafetyCallDepth` | Nesting > 8 níveis | Safety call depth exceeds maximum (8 levels) |

### 6.3 Regras WARNING em Safety

| ID | Condição | Mensagem |
|---|---|---|
| `SafetyJump` | EXIT/CONTINUE em loop Safety | Jumps should be minimized in Safety programs |
| `SafetyTimerCount` | Mais de 3 timers em F-block | Reduce timer blocks in Safety — use multi-instance |
| `SafetyNoValueStatus` | Acesso a F-I/O sem `_VS` | Check value status for F-I/O channel validity |
| `SafetyNoHeader` | Bloco F_ sem cabeçalho obrigatório | Safety block missing required header (Guideline §3.1.6) |
| `SafetyNoElse` | CASE sem ELSE em F-block | CASE in Safety must have ELSE returning to safe state |

### 6.4 Ícones no Project View

```
🔴 F_EStop.scl         ← bloco Safety (vermelho = atenção)
🔴 F_GuardDoor.scl
🟦 FB_TankControl.scl  ← bloco Standard (azul)
🟩 OB_Main.scl         ← OB Standard (verde)
```

---

## 7. Live Templates Safety

Adicionar ao `SCL.xml`:

| Abreviação | Expansão |
|---|---|
| `fsafety` | Template completo de F-FB com cabeçalho |
| `festop` | F-FB para E-Stop com lógica AND dos canais |
| `fguard` | F-FB para porta de segurança |
| `fmain` | Main Safety OB com ordem de chamada correta |
| `fudtq` | F-UDT mínimo |

```xml
<template name="festop" 
          value="FUNCTION_BLOCK &quot;F_$NAME$&quot;&#10;(* Safety — $DESCRIPTION$ *)&#10;&#10;VAR_INPUT&#10;    i_Ch1 : Bool;   // Canal 1 NF&#10;    i_Ch2 : Bool;   // Canal 2 redundância&#10;    i_Ack : Bool;   // Reconhecimento&#10;END_VAR&#10;&#10;VAR_OUTPUT&#10;    o_Release  : Bool;&#10;    o_DiagCode : Int;&#10;END_VAR&#10;&#10;VAR&#10;    s_AckEdge : R_TRIG;&#10;END_VAR&#10;&#10;BEGIN&#10;    s_AckEdge(CLK := i_Ack);&#10;&#10;    o_Release := i_Ch1 AND i_Ch2;&#10;&#10;    $END$&#10;END_FUNCTION_BLOCK"
          description="F-FB E-Stop com dois canais"
          toReformat="true" toShortenFQNames="true">
</template>
```

---

## 8. Completion Safety

Quando o arquivo for detectado como Safety, o completion deve:

**Adicionar:**
```
F_ESTOP1          ← LSafe library block
F_TRIG            ← Fail-safe edge detection
ACK_GL            ← Global acknowledge
FDBACK            ← Feedback monitoring
NSMP              ← Non-safe messaging
```

**Remover da lista:**
```
LINT, ULINT, LWORD    ← não suportados
LTIME, LTOD, LDT      ← não suportados
GOTO                  ← proibido
```

---

## 9. Registrar no plugin.xml

```xml
<!-- Safety Inspections -->
<localInspection
    language="SCL"
    groupName="SCL Safety"
    displayName="GOTO in Safety program"
    enabledByDefault="true"
    level="ERROR"
    implementationClass="com.scl.plugin.linter.safety.SclSafetyGotoInspection"/>

<localInspection
    language="SCL"
    groupName="SCL Safety"
    displayName="Unsupported type in Safety program"
    enabledByDefault="true"
    level="ERROR"
    implementationClass="com.scl.plugin.linter.safety.SclSafetyTypeInspection"/>

<localInspection
    language="SCL"
    groupName="SCL Safety"
    displayName="Global data access in F-FB"
    enabledByDefault="true"
    level="ERROR"
    implementationClass="com.scl.plugin.linter.safety.SclSafetyGlobalDataInspection"/>

<localInspection
    language="SCL"
    groupName="SCL Safety"
    displayName="Missing Safety block header"
    enabledByDefault="true"
    level="WARNING"
    implementationClass="com.scl.plugin.linter.safety.SclSafetyHeaderInspection"/>
```

---

## 10. Checklist de Testes

```
[ ] Arquivo F_EStop.scl detectado como Safety → ícone vermelho
[ ] GOTO em F-block → sublinhado vermelho + mensagem
[ ] LINT em F-block → sublinhado vermelho
[ ] LTIME em F-block → sublinhado vermelho
[ ] "DB_Global".var em F-FB → sublinhado vermelho
[ ] CASE sem ELSE em F-block → sublinhado amarelo
[ ] Live template festop → expande F-FB com dois canais
[ ] Completion em F-block → não sugere LINT, LWORD, GOTO
[ ] Project View → F_*.scl com ícone vermelho
[ ] Standard *.scl → ícone azul inalterado
```

---

## 11. Limitações — O que o Plugin NÃO pode fazer

```
❌ Gerar a F-Signature (calculada internamente pelo TIA Portal)
❌ Validar o F-change history
❌ Executar o processo de aceitação (System Acceptance)
❌ Configurar PROFIsafe addresses
❌ Verificar se o F-CPU tem licença STEP 7 Safety instalada
❌ Gerar código Safety para S7-300/400 (SCLV4 antigo)

✅ O que o plugin PODE fazer:
✅ Detectar blocos Safety pelo prefixo F_
✅ Linter com regras obrigatórias do Guideline Safety
✅ Templates com cabeçalho obrigatório
✅ Completion restrito ao subconjunto Safety
✅ Ícones diferenciados no Project View
✅ Avisos de boas práticas do Programming Guideline Safety
```

---

## 12. Referências Oficiais

| Documento | Link |
|---|---|
| Programming Guideline Safety V1.3 (2023) | https://support.industry.siemens.com/cs/ww/en/view/109750255 |
| SIMATIC Safety Configuring & Programming V21 | https://support.industry.siemens.com/cs/attachments/54110126/ProgFAILenUS_en-US.pdf |
| LSafe Library (TÜV-checked) | https://support.industry.siemens.com/cs/ww/en/view/109793462 |
| LDrvSafe Library (drives) | https://support.industry.siemens.com/cs/ww/en/view/109485794 |
| S7-1200 Functional Safety Manual | https://support.industry.siemens.com/cs/ww/en/view/104547552 |
| Response Time Calculator | https://support.industry.siemens.com/cs/ww/en/view/93839056 |
