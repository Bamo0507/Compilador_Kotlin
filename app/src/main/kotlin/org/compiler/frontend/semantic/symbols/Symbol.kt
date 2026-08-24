package org.compiler.frontend.semantic.symbols

import org.compiler.models.LexemeLocation

// La gramatica no tiene sintaxis de constructor: una clase lo declara como una funcion
// que se LLAMA asi. Este es el unico lugar donde ese nombre esta escrito.
const val CONSTRUCTOR_NAME = "constructor"

/**
 * La ficha de un nombre declarado. Es la unidad de informacion de la tabla de
 * simbolos.
 *
 * Los `val` son lo que se sabe al declararlo; los `var`, lo que se descubre despues
 * recorriendo el programa.
 */
data class Symbol(
    val name: String,
    val kind: DeclarationKind,
    val type: Type,

    // Linea y columna de la DECLARACION, no del uso.
    val location: LexemeLocation,

    val scopeName: String,

    // Declarado dentro de una clase. Lo pone Scope.declare.
    val isMember: Boolean = false,

    // Posicion dentro de su ambito. Indice de ranura, no de bytes.
    val offset: Int,

    // Cuantos ambitos de funcion habia encima al declararlo. Lo pone Scope.declare.
    val declarationFunctionDepth: Int = 0,

    // Solo para kind == CLASS: el ambito con sus campos y metodos.
    val memberScope: Scope? = null,

    var initialized: Boolean = false,

    // Solo para kind == CONSTANT.
    var constantValue: Any? = null,

    var useCount: Int = 0,
    var lastUseLine: Int? = null,
    var usedInNestedFunction: Boolean = false
)
