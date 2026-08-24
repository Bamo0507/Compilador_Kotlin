package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.TypeReference
import org.compiler.frontend.semantic.TypeResolver
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.models.LexemeLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TypeResolverTest {

    private val location = LexemeLocation(1, 1)

    private fun globalConPerro(): Scope {
        val global = Scope(ScopeKind.GLOBAL, "global", parent = null)
        global.declare(
            Symbol(
                name = "Perro",
                kind = DeclarationKind.CLASS,
                type = ClassType("Perro"),
                location = location,
                scopeName = "global",
                offset = 0,
                memberScope = global.openChild(ScopeKind.CLASS, "Perro")
            )
        )
        return global
    }

    private fun ref(baseName: String, dimensions: Int = 0) =
        TypeReference(baseName, dimensions, location)

    @Test
    fun `resuelve los primitivos`() {
        val diagnostics = Diagnostics()
        val resolver = TypeResolver(globalConPerro(), diagnostics)

        assertEquals(IntegerType, resolver.resolve(ref("integer")))
        assertTrue(diagnostics.all().isEmpty())
    }

    @Test
    fun `cada dimension envuelve en un ArrayType`() {
        val resolver = TypeResolver(globalConPerro(), Diagnostics())

        assertEquals(ArrayType(IntegerType), resolver.resolve(ref("integer", 1)))
        assertEquals(
            ArrayType(ArrayType(IntegerType)),
            resolver.resolve(ref("integer", 2))
        )
    }

    @Test
    fun `resuelve una clase declarada`() {
        val diagnostics = Diagnostics()
        val resolver = TypeResolver(globalConPerro(), diagnostics)

        assertEquals(ClassType("Perro"), resolver.resolve(ref("Perro")))
        assertTrue(diagnostics.all().isEmpty())
    }

    @Test
    fun `una clase no declarada es ErrorType y se reporta`() {
        val diagnostics = Diagnostics()
        val resolver = TypeResolver(globalConPerro(), diagnostics)

        assertEquals(ErrorType, resolver.resolve(ref("Gato")))
        assertEquals(1, diagnostics.count)
        assertEquals(location, diagnostics.all().first().location)
    }

    // Existe el nombre pero no es una clase. Sin la segunda condicion del if, esto
    // produciria ClassType("contador"): un tipo que apunta a una clase inexistente.
    @Test
    fun `un nombre que existe pero no es clase es ErrorType`() {
        val global = globalConPerro()
        global.declare(
            Symbol(
                name = "contador",
                kind = DeclarationKind.VARIABLE,
                type = IntegerType,
                location = location,
                scopeName = "global",
                offset = 0
            )
        )
        val diagnostics = Diagnostics()

        assertEquals(ErrorType, TypeResolver(global, diagnostics).resolve(ref("contador")))
        assertEquals(1, diagnostics.count)
    }

    // ArrayType(ErrorType) no es ErrorType, y la Fase 4 no lo reconoceria como error ya
    // reportado: emitiria un segundo error por la misma equivocacion.
    @Test
    fun `un arreglo de tipo inexistente corta en ErrorType, sin envolver`() {
        val diagnostics = Diagnostics()
        val resolver = TypeResolver(globalConPerro(), diagnostics)

        assertEquals(ErrorType, resolver.resolve(ref("Gato", 1)))
        assertEquals(1, diagnostics.count)
    }

    @Test
    fun `un TypeReference nulo devuelve null sin reportar`() {
        val diagnostics = Diagnostics()
        val resolver = TypeResolver(globalConPerro(), diagnostics)

        assertNull(resolver.resolve(null))
        assertTrue(diagnostics.all().isEmpty())
    }

    @Test
    fun `dos resoluciones del mismo tipo son iguales`() {
        val resolver = TypeResolver(globalConPerro(), Diagnostics())

        assertEquals(resolver.resolve(ref("integer", 1)), resolver.resolve(ref("integer", 1)))
    }

    // Ningun string de tipo primitivo esta escrito a mano en TypeResolver: el mapa se
    // deriva de Type.name.
    @Test
    fun `los cuatro primitivos se resuelven por su name`() {
        val resolver = TypeResolver(globalConPerro(), Diagnostics())

        listOf("integer", "float", "string", "boolean").forEach { nombre ->
            assertEquals(nombre, resolver.resolve(ref(nombre))?.name)
        }
    }
}
