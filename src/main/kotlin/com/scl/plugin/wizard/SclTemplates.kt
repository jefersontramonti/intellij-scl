package com.scl.plugin.wizard

object SclTemplates {

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

    fun organizationBlock(name: String): String = """
ORGANIZATION_BLOCK "$name"
{ S7_Optimized_Access := 'TRUE' }

VAR_TEMP

END_VAR

BEGIN

END_ORGANIZATION_BLOCK
""".trimIndent()

    fun udt(name: String): String = """
TYPE "$name"
    STRUCT

    END_STRUCT
END_TYPE
""".trimIndent()

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
