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
import org.compiler.types.TypeRules
import org.compiler.types.TypeRules.Family
import org.compiler.types.BinaryOperator
import org.compiler.types.UnaryOperator
import org.compiler.types.VarcharType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Las reglas de compatibilidad. Es el archivo que se consulta cuando alguien
 * pregunta por que DATE no se compara con TIME.
 */
class TypeRulesTest {

    // ── Familias ───────────────────────────────────────────────────────────

    @Test
    fun `cada tipo cae en su familia`() {
        assertEquals(Family.NUMERIC, TypeRules.familyOf(IntType))
        assertEquals(Family.NUMERIC, TypeRules.familyOf(FloatType))
        assertEquals(Family.NUMERIC, TypeRules.familyOf(DecimalType(10, 2)))

        assertEquals(Family.CHARACTER, TypeRules.familyOf(CharType(5)))
        assertEquals(Family.CHARACTER, TypeRules.familyOf(VarcharType(80)))
        assertEquals(Family.CHARACTER, TypeRules.familyOf(TextType))

        assertEquals(Family.TEMPORAL, TypeRules.familyOf(DateType))
        assertEquals(Family.TEMPORAL, TypeRules.familyOf(TimeType))

        assertEquals(Family.LOGICAL, TypeRules.familyOf(BooleanType))
        assertEquals(Family.NULL, TypeRules.familyOf(NullType))
        assertEquals(Family.ERROR, TypeRules.familyOf(ErrorType))
    }

    @Test
    fun `entre familias distintas no hay nada`() {
        val unaDeCada = listOf(IntType, TextType, DateType, BooleanType)

        unaDeCada.forEach { izquierda ->
            unaDeCada.filter { it != izquierda }.forEach { derecha ->
                assertFalse(
                    TypeRules.isAssignable(izquierda, derecha),
                    "${izquierda.name} no deberia caber en ${derecha.name}"
                )
                assertNull(
                    TypeRules.unify(izquierda, derecha),
                    "${izquierda.name} y ${derecha.name} no deberian unificar"
                )
            }
        }
    }

    // Comparten familia, pero son cosas distintas. Dejarlo pasar esconde errores.
    @Test
    fun `DATE y TIME no se mezclan aunque compartan familia`() {
        assertFalse(TypeRules.isAssignable(DateType, TimeType))
        assertFalse(TypeRules.isAssignable(TimeType, DateType))
        assertNull(TypeRules.unify(DateType, TimeType))

        assertTrue(TypeRules.isAssignable(DateType, DateType))
        assertEquals(TimeType, TypeRules.unify(TimeType, TimeType))
    }

    // ── La torre numerica ──────────────────────────────────────────────────

    @Test
    fun `la torre numerica ensancha en un solo sentido`() {
        assertTrue(TypeRules.isAssignable(IntType, DecimalType(10, 2)))
        assertTrue(TypeRules.isAssignable(IntType, FloatType))
        assertTrue(TypeRules.isAssignable(DecimalType(10, 2), FloatType))

        assertFalse(TypeRules.isAssignable(DecimalType(10, 2), IntType))
        assertFalse(TypeRules.isAssignable(FloatType, IntType))
        assertFalse(TypeRules.isAssignable(FloatType, DecimalType(10, 2)))
    }

    @Test
    fun `unify toma el mas ancho sin importar el orden`() {
        assertEquals(FloatType, TypeRules.unify(IntType, FloatType))
        assertEquals(FloatType, TypeRules.unify(FloatType, IntType))
        assertEquals(DecimalType(10, 2), TypeRules.unify(IntType, DecimalType(10, 2)))
        assertEquals(DecimalType(10, 2), TypeRules.unify(DecimalType(10, 2), IntType))
    }

    // isAssignable responde "cabe este valor en esta columna" y es direccional.
    // unify responde "de que tipo es a + b" y es simetrica. Confundirlas es el
    // error clasico.
    @Test
    fun `isAssignable es direccional y unify no`() {
        assertTrue(TypeRules.isAssignable(IntType, FloatType))
        assertFalse(TypeRules.isAssignable(FloatType, IntType))

        assertEquals(TypeRules.unify(IntType, FloatType), TypeRules.unify(FloatType, IntType))
    }

    // ── DECIMAL ────────────────────────────────────────────────────────────

    @Test
    fun `dos DECIMAL unifican en el mas chico que contiene a los dos`() {
        // 8 enteros + 2 decimales contra 4 enteros + 4 decimales.
        assertEquals(
            DecimalType(12, 4),
            TypeRules.unify(DecimalType(10, 2), DecimalType(8, 4))
        )
        assertEquals(
            DecimalType(10, 2),
            TypeRules.unify(DecimalType(10, 2), DecimalType(10, 2))
        )
    }

    // La precision y la escala NO se revisan al asignar: eso lo hace ValueCodec
    // con el valor concreto, igual que el largo de un VARCHAR.
    @Test
    fun `la escala no bloquea la asignacion entre DECIMAL`() {
        assertTrue(TypeRules.isAssignable(DecimalType(21, 4), DecimalType(10, 2)))
        assertTrue(TypeRules.isAssignable(DecimalType(10, 2), DecimalType(21, 4)))
    }

    // ── Caracter ───────────────────────────────────────────────────────────

    @Test
    fun `los tres tipos de caracter son intercambiables entre si`() {
        val caracteres = listOf(CharType(5), VarcharType(80), TextType)

        caracteres.forEach { izquierda ->
            caracteres.forEach { derecha ->
                assertTrue(TypeRules.isAssignable(izquierda, derecha))
                assertEquals(TextType, TypeRules.unify(izquierda, derecha))
            }
        }
    }

    // ── NULL y ErrorType ───────────────────────────────────────────────────

    @Test
    fun `NULL cabe en cualquier columna`() {
        listOf(IntType, TextType, DateType, BooleanType, DecimalType(10, 2)).forEach {
            assertTrue(TypeRules.isAssignable(NullType, it), "NULL deberia caber en ${it.name}")
            assertEquals(it, TypeRules.unify(NullType, it))
            assertEquals(it, TypeRules.unify(it, NullType))
        }
    }

    // Sin esto, `(1 + 'a') * 2` reportaria dos errores por una sola equivocacion.
    @Test
    fun `ErrorType absorbe en silencio y no genera errores nuevos`() {
        listOf<Type>(IntType, TextType, DateType, BooleanType).forEach {
            assertTrue(TypeRules.isAssignable(ErrorType, it))
            assertTrue(TypeRules.isAssignable(it, ErrorType))
            assertEquals(ErrorType, TypeRules.unify(ErrorType, it))
            assertEquals(ErrorType, TypeRules.unify(it, ErrorType))
        }
    }
}

/**
 * Los operadores: donde se pueden aplicar y que tipo devuelven.
 */
class OperatorRulesTest {

    private fun binary(operator: BinaryOperator, left: Type, right: Type) =
        TypeRules.binaryResultType(operator, left, right)

    // ── Aritmetica ─────────────────────────────────────────────────────────

    @Test
    fun `la aritmetica solo acepta numericos`() {
        assertEquals(IntType, binary(BinaryOperator.ADD, IntType, IntType))
        assertNull(binary(BinaryOperator.ADD, DateType, IntType))
        assertNull(binary(BinaryOperator.ADD, TextType, TextType))
        assertNull(binary(BinaryOperator.MULTIPLY, BooleanType, BooleanType))
    }

    // Mezclar exacto con inexacto da inexacto: no se puede prometer exactitud
    // sobre un operando que ya la perdio.
    @Test
    fun `FLOAT contagia al resto de la operacion`() {
        assertEquals(FloatType, binary(BinaryOperator.ADD, IntType, FloatType))
        assertEquals(FloatType, binary(BinaryOperator.ADD, FloatType, IntType))
        assertEquals(FloatType, binary(BinaryOperator.MULTIPLY, DecimalType(10, 2), FloatType))
    }

    // El caso que decidio seguir el estandar: dos decimales por dos decimales
    // necesitan cuatro. Con la escala mayor, 0.05 * 0.05 daria 0.00.
    @Test
    fun `multiplicar DECIMAL suma precisiones y escalas`() {
        assertEquals(
            DecimalType(21, 4),
            binary(BinaryOperator.MULTIPLY, DecimalType(10, 2), DecimalType(10, 2))
        )
        assertEquals(
            DecimalType(13, 6),
            binary(BinaryOperator.MULTIPLY, DecimalType(6, 2), DecimalType(6, 4))
        )
    }

    @Test
    fun `sumar DECIMAL toma la escala mayor y acarrea un digito`() {
        // 8 enteros + 2 decimales contra 4 enteros + 4 decimales.
        assertEquals(
            DecimalType(13, 4),
            binary(BinaryOperator.ADD, DecimalType(10, 2), DecimalType(8, 4))
        )
        assertEquals(
            DecimalType(11, 2),
            binary(BinaryOperator.SUBTRACT, DecimalType(10, 2), DecimalType(10, 2))
        )
    }

    // El estandar deja la division sin definir porque 1/3 no termina.
    @Test
    fun `dividir DECIMAL agrega seis decimales`() {
        assertEquals(
            DecimalType(18, 8),
            binary(BinaryOperator.DIVIDE, DecimalType(10, 2), DecimalType(8, 2))
        )
    }

    // Lo que hacen PostgreSQL y SQL Server. Sorprende, pero desviarse del
    // estandar sorprenderia mas.
    @Test
    fun `dividir dos INT trunca`() {
        assertEquals(IntType, binary(BinaryOperator.DIVIDE, IntType, IntType))
    }

    @Test
    fun `un INT entra a la aritmetica decimal como 19 digitos`() {
        assertEquals(
            DecimalType(22, 2),
            binary(BinaryOperator.ADD, IntType, DecimalType(10, 2))
        )
    }

    // ── Concatenacion ──────────────────────────────────────────────────────

    @Test
    fun `la concatenacion solo acepta caracter y devuelve TEXT`() {
        assertEquals(TextType, binary(BinaryOperator.CONCAT, CharType(3), TextType))
        assertEquals(TextType, binary(BinaryOperator.CONCAT, VarcharType(10), VarcharType(20)))
        assertNull(binary(BinaryOperator.CONCAT, IntType, TextType))
    }

    // ── Comparacion ────────────────────────────────────────────────────────

    @Test
    fun `el orden acepta numericos, caracter y temporales`() {
        assertEquals(BooleanType, binary(BinaryOperator.LESS, IntType, DecimalType(10, 2)))
        assertEquals(BooleanType, binary(BinaryOperator.GREATER, TextType, VarcharType(5)))
        assertEquals(BooleanType, binary(BinaryOperator.LESS_EQUAL, DateType, DateType))
    }

    // Preguntar si true > false no significa nada.
    @Test
    fun `el orden no acepta BOOLEAN`() {
        assertNull(binary(BinaryOperator.GREATER, BooleanType, BooleanType))
        assertEquals(BooleanType, binary(BinaryOperator.EQUAL, BooleanType, BooleanType))
    }

    @Test
    fun `no se comparan tipos de familias distintas`() {
        assertNull(binary(BinaryOperator.EQUAL, IntType, TextType))
        assertNull(binary(BinaryOperator.LESS, DateType, TimeType))
        assertNull(binary(BinaryOperator.GREATER, TextType, DateType))
    }

    // ── Logicos ────────────────────────────────────────────────────────────

    @Test
    fun `AND y OR solo aceptan BOOLEAN`() {
        assertEquals(BooleanType, binary(BinaryOperator.AND, BooleanType, BooleanType))
        assertNull(binary(BinaryOperator.OR, BooleanType, IntType))
        assertNull(binary(BinaryOperator.AND, IntType, IntType))
    }

    // ── Unarios y IS NULL ──────────────────────────────────────────────────

    @Test
    fun `el negativo conserva el tipo y NOT exige BOOLEAN`() {
        assertEquals(IntType, TypeRules.unaryResultType(UnaryOperator.NEGATE, IntType))
        assertEquals(
            DecimalType(10, 2),
            TypeRules.unaryResultType(UnaryOperator.NEGATE, DecimalType(10, 2))
        )
        assertNull(TypeRules.unaryResultType(UnaryOperator.NEGATE, TextType))

        assertEquals(BooleanType, TypeRules.unaryResultType(UnaryOperator.NOT, BooleanType))
        assertNull(TypeRules.unaryResultType(UnaryOperator.NOT, IntType))
    }

    @Test
    fun `IS NULL acepta cualquier tipo`() {
        assertEquals(BooleanType, TypeRules.isNullResultType())
    }

    // ── ErrorType ──────────────────────────────────────────────────────────

    @Test
    fun `ninguna operacion con ErrorType genera un error nuevo`() {
        BinaryOperator.entries.forEach {
            assertEquals(ErrorType, binary(it, ErrorType, IntType), "fallo con ${it.symbol}")
            assertEquals(ErrorType, binary(it, TextType, ErrorType), "fallo con ${it.symbol}")
        }
    }

    // ── Simbolos ───────────────────────────────────────────────────────────

    @Test
    fun `los simbolos se leen del texto de la gramatica`() {
        assertEquals(BinaryOperator.ADD, BinaryOperator.fromSymbol("+"))
        assertEquals(BinaryOperator.CONCAT, BinaryOperator.fromSymbol("||"))
        assertEquals(BinaryOperator.AND, BinaryOperator.fromSymbol("AND"))
        assertNull(BinaryOperator.fromSymbol("%"))
    }

    // SQL acepta las dos formas para lo mismo, y el AST guarda una sola.
    @Test
    fun `los dos simbolos de desigualdad dan el mismo operador`() {
        assertEquals(BinaryOperator.NOT_EQUAL, BinaryOperator.fromSymbol("<>"))
        assertEquals(BinaryOperator.NOT_EQUAL, BinaryOperator.fromSymbol("!="))
    }
}
