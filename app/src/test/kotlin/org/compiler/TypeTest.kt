package org.compiler

import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.NullType
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.frontend.semantic.symbols.VoidType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypeTest {

    @Test
    fun `dos arreglos del mismo elemento son iguales`() {
        assertEquals(ArrayType(IntegerType), ArrayType(IntegerType))
        assertNotEquals(ArrayType(IntegerType), ArrayType(FloatType))
    }

    // Tipado nominal: Perro y Gato son tipos distintos aunque tengan campos identicos.
    @Test
    fun `dos clases con nombre distinto son tipos distintos`() {
        assertEquals(ClassType("Perro"), ClassType("Perro"))
        assertNotEquals(ClassType("Perro"), ClassType("Gato"))
    }

    // Los primitivos son singletons, asi que compararlos no recorre nada.
    @Test
    fun `los primitivos son la misma instancia`() {
        assertSame(IntegerType, IntegerType)
        assertSame(FloatType, FloatType)
        assertSame(StringType, StringType)
        assertSame(BooleanType, BooleanType)
    }

    @Test
    fun `el nombre de un arreglo compone las dimensiones`() {
        assertEquals("integer[]", ArrayType(IntegerType).name)
        assertEquals("integer[][]", ArrayType(ArrayType(IntegerType)).name)
        assertEquals("Perro[]", ArrayType(ClassType("Perro")).name)
    }

    @Test
    fun `el nombre de una funcion lista sus parametros y su retorno`() {
        assertEquals(
            "(integer, integer) -> integer",
            FunctionType(listOf(IntegerType, IntegerType), IntegerType).name
        )
        assertEquals(
            "() -> void",
            FunctionType(emptyList(), VoidType).name
        )
        assertEquals(
            "(string) -> integer[]",
            FunctionType(listOf(StringType), ArrayType(IntegerType)).name
        )
    }

    @Test
    fun `dos funciones con la misma firma son iguales`() {
        assertEquals(
            FunctionType(listOf(IntegerType), StringType),
            FunctionType(listOf(IntegerType), StringType)
        )
        assertNotEquals(
            FunctionType(listOf(IntegerType), StringType),
            FunctionType(listOf(IntegerType), IntegerType)
        )
    }

    @Test
    fun `cada tipo tiene un nombre no vacio`() {
        val todos = listOf(
            IntegerType, FloatType, StringType, BooleanType,
            VoidType, NullType, ErrorType,
            ArrayType(IntegerType), ClassType("Perro"),
            FunctionType(emptyList(), VoidType)
        )

        assertTrue(todos.all { it.name.isNotBlank() })
    }

    // Este `when` no tiene rama `else`: si se agrega un Type nuevo y no se cubre aqui,
    // el test NO COMPILA. Es la prueba de que el sealed esta completo.
    @Test
    fun `un when sobre Type sin else compila`() {
        assertEquals("primitivo", categoria(IntegerType))
        assertEquals("especial", categoria(ErrorType))
        assertEquals("compuesto", categoria(ArrayType(IntegerType)))
    }

    private fun categoria(type: Type): String = when (type) {
        IntegerType, FloatType, StringType, BooleanType -> "primitivo"
        VoidType, NullType, ErrorType -> "especial"
        is ArrayType, is ClassType, is FunctionType -> "compuesto"
    }
}
