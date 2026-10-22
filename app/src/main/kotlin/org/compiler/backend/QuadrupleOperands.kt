package org.compiler.backend

import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Allocate
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.IndexedLoad
import org.compiler.frontend.intermediate.models.IndexedStore
import org.compiler.frontend.intermediate.models.IndirectCall
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.ListPush
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.Param
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.intermediate.models.VirtualTableDefinition

// Lo que lee y lo que escribe cada instruccion del TAC. El analisis de proximo uso
// necesita separarlos: en `x = y + z` se leen y y z, y se escribe x.

// Las direcciones que la instruccion lee, en orden.
fun usesOf(quadruple: Quadruple): List<Address> = when (quadruple) {
    is Arithmetic -> listOf(quadruple.left, quadruple.right)
    is Relational -> listOf(quadruple.left, quadruple.right)
    is Concat -> listOf(quadruple.left, quadruple.right)
    is Unary -> listOf(quadruple.operand)
    is Copy -> listOf(quadruple.source)
    is IfGoto -> listOf(quadruple.condition)
    is IfFalseGoto -> listOf(quadruple.condition)
    is IfRelationalGoto -> listOf(quadruple.left, quadruple.right)
    is Param -> listOf(quadruple.value)
    is Return -> listOfNotNull(quadruple.value)
    is IndexedLoad -> listOf(quadruple.base, quadruple.offset)
    is IndexedStore -> listOf(quadruple.base, quadruple.offset, quadruple.value)
    is ListPush -> listOf(quadruple.list, quadruple.value)
    is Print -> listOf(quadruple.value)
    is Throw -> listOf(quadruple.message)
    is Allocate -> listOf(quadruple.size)
    is IndirectCall -> listOf(quadruple.target)
    is Call, is TryBegin, is LabelDefinition, is Goto, is FunctionBegin, is FunctionEnd,
    TryEnd, is VirtualTableDefinition -> emptyList()
}

// La direccion que la instruccion escribe, si escribe alguna. `try L, e` escribe e solo
// si salta al manejador, lo que ocurre en otro bloque, asi que aqui no cuenta.
fun definitionOf(quadruple: Quadruple): Address? = when (quadruple) {
    is Arithmetic -> quadruple.result
    is Relational -> quadruple.result
    is Concat -> quadruple.result
    is Unary -> quadruple.result
    is Copy -> quadruple.result
    is IndexedLoad -> quadruple.result
    is Allocate -> quadruple.result
    is Call -> quadruple.result
    is IndirectCall -> quadruple.result
    is IfGoto, is IfFalseGoto, is IfRelationalGoto, is Param, is Return, is IndexedStore,
    is ListPush, is Print, is Throw, is TryBegin, is LabelDefinition, is Goto,
    is FunctionBegin, is FunctionEnd, TryEnd, is VirtualTableDefinition -> null
}

// Todo lo que aparece en la instruccion, leido o escrito.
fun addressesOf(quadruple: Quadruple): List<Address> {
    val exceptionVariable = (quadruple as? TryBegin)?.exceptionVariable
    return usesOf(quadruple) + listOfNotNull(definitionOf(quadruple), exceptionVariable)
}

// Una variable del programa o un temporal: lo que getReg puede guardar en un registro.
// Las constantes y las direcciones de las tablas de metodos no lo son.
fun isVariable(address: Address): Boolean = address is Name || address is Temporary
