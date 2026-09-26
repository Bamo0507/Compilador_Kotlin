package org.compiler

import org.compiler.types.BooleanType
import org.compiler.types.CharType
import org.compiler.types.DateType
import org.compiler.types.DecimalType
import org.compiler.types.ErrorType
import org.compiler.types.FloatType
import org.compiler.types.IntType
import org.compiler.types.NullType
import org.compiler.types.TextType
import org.compiler.types.TimeType
import org.compiler.types.Type
import org.compiler.types.VarcharType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

/**
 * El sistema de tipos como estructura de datos: igualdad, parametros y nombres.
 *
 * Lo que se puede hacer CON los tipos (comparar, sumar, asignar) no se prueba
 * aqui: eso vive en TypeRules.
 */
class TypeTest {

    // Los ocho sin parametros son instancia unica, asi que comparar es comparar
    // referencias. Es lo que da `data object`.
    @Test
    fun `los tipos sin parametros son instancia unica`() {
        assertSame(IntType, IntType)
        assertSame(FloatType, FloatType)
        assertSame(TextType, TextType)
        assertSame(DateType, DateType)
        assertSame(TimeType, TimeType)
        assertSame(BooleanType, BooleanType)
        assertSame(NullType, NullType)
        assertSame(ErrorType, ErrorType)
    }

    // Aqui esta el cambio de fondo respecto a un lenguaje sin tipos parametricos:
    // la igualdad deja de ser por referencia y pasa a ser por partes.
    @Test
    fun `los tipos con parametros se comparan por sus partes`() {
        assertEquals(DecimalType(10, 2), DecimalType(10, 2))
        assertEquals(CharType(5), CharType(5))
        assertEquals(VarcharType(80), VarcharType(80))
    }

    @Test
    fun `dos DECIMAL con distinta escala son tipos distintos`() {
        assertNotEquals<Type>(DecimalType(10, 2), DecimalType(10, 4))
        assertNotEquals<Type>(DecimalType(10, 2), DecimalType(12, 2))
    }

    // Mismo numero adentro, tipos distintos: CHAR rellena y VARCHAR no.
    @Test
    fun `CHAR y VARCHAR del mismo largo no son el mismo tipo`() {
        assertNotEquals<Type>(CharType(5), VarcharType(5))
    }

    @Test
    fun `el nombre se escribe como en el CREATE TABLE`() {
        assertEquals("INT", IntType.name)
        assertEquals("FLOAT", FloatType.name)
        assertEquals("TEXT", TextType.name)
        assertEquals("DATE", DateType.name)
        assertEquals("TIME", TimeType.name)
        assertEquals("BOOLEAN", BooleanType.name)
        assertEquals("DECIMAL(10,2)", DecimalType(10, 2).name)
        assertEquals("CHAR(5)", CharType(5).name)
        assertEquals("VARCHAR(80)", VarcharType(80).name)
    }

    @Test
    fun `los tipos internos tienen nombre para los mensajes de error`() {
        assertEquals("NULL", NullType.name)
        assertEquals("<error>", ErrorType.name)
    }

    // Si alguien agrega un tipo y olvida este `when`, esto no compila. Ese es el
    // punto de que la jerarquia sea sellada, y por eso el test no lleva `else`.
    @Test
    fun `el when sellado cubre los once`() {
        val todos: List<Type> = listOf(
            IntType, FloatType, DecimalType(10, 2),
            CharType(5), VarcharType(80), TextType,
            DateType, TimeType,
            BooleanType, NullType, ErrorType
        )

        assertEquals(11, todos.size)
        assertEquals(11, todos.map { categoria(it) }.size)
    }

    private fun categoria(type: Type): String = when (type) {
        IntType, FloatType, is DecimalType -> "numerico"
        is CharType, is VarcharType, TextType -> "caracter"
        DateType, TimeType -> "temporal"
        BooleanType -> "logico"
        NullType -> "nulo"
        ErrorType -> "error"
    }
}
