package org.compiler.interpreter

// Señales de control de flujo. 
// Heredan de RuntimeException pero sin stack trace 
sealed class ControlFlowSignal : RuntimeException(null, null, false, false)

class BreakSignal : ControlFlowSignal()

class ContinueSignal : ControlFlowSignal()

class ReturnSignal(val value: RuntimeValue) : ControlFlowSignal()
