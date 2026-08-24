package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.TypeReference
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Type

// Se DERIVA del `name` de cada Type en vez de repetir los strings, asi que Type.name
// queda como la unica definicion de como se escribe cada primitivo.
private val PRIMITIVE_TYPES: Map<String, Type> =
    listOf(IntegerType, FloatType, StringType, BooleanType)
        .associateBy { it.name }

/**
 * Convierte un tipo ESCRITO (TypeReference) en un tipo RESUELTO (Type).
 *
 * Recibe el globalScope porque las clases solo viven en el nivel global: no importa
 * donde este parado el analisis, un nombre de clase se busca ahi.
 */
class TypeResolver(
    private val globalScope: Scope,
    private val diagnostics: Diagnostics
) {

    /**
     * Un TypeReference nulo significa "no se anoto el tipo". Devuelve null para que el
     * LLAMADOR decida que significa: void en un retorno, error en un parametro o un
     * campo, e inferir en una variable local.
     */
    fun resolve(typeRef: TypeReference?): Type? {
        if (typeRef == null) return null

        val baseType = resolveBaseName(typeRef)

        // Si el nombre base ya fallo, no envolverlo: ArrayType(ErrorType) no es
        // ErrorType, y la Fase 4 no lo reconoceria como "error ya reportado".
        if (baseType == ErrorType) return ErrorType

        var result = baseType
        repeat(typeRef.arrayDimensions) {
            result = ArrayType(result)
        }
        return result
    }

    // Primero primitivos, despues clases: el orden decide que si alguien escribe
    // `class integer { }`, el tipo `integer` sigue siendo el primitivo.
    private fun resolveBaseName(typeRef: TypeReference): Type =
        PRIMITIVE_TYPES[typeRef.baseName] ?: resolveClassName(typeRef)

    private fun resolveClassName(typeRef: TypeReference): Type {
        val symbol = globalScope.lookupLocal(typeRef.baseName)

        // Dos condiciones, dos errores distintos:
        //   symbol == null   ->  `let a: Gato;`       el nombre no existe
        //   kind != CLASS    ->  `let b: contador;`   existe, pero es una variable
        if (symbol == null || symbol.kind != DeclarationKind.CLASS) {
            diagnostics.report(
                CompilerError.SemanticError(
                    location = typeRef.location,
                    message = "El tipo '${typeRef.baseName}' no está declarado"
                )
            )
            return ErrorType
        }

        return ClassType(typeRef.baseName)
    }
}
