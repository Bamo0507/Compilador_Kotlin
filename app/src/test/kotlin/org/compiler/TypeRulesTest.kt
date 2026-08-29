package org.compiler

import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.semantic.TypeRules
import org.compiler.frontend.semantic.symbols.ArrayType
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.NullType
import org.compiler.frontend.semantic.symbols.StringType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del ticket 4.1: la tabla de verdad del lenguaje.
 *
 * Se prueban con tipos puros, sin construir AST ni ambitos: es lo que compra que
 * TypeRules no conozca ni uno ni otro.
 *
 * Cada seccion lleva el codigo de la regla que cubre —A1, C1, L1...—, y esos codigos
 * son los mismos de docs/reglas-de-tipos.md, para poder ir de la regla al test.
 */
class TypeRulesTest {

    // La jerarquia falsa: Perro y Gato heredan de Animal, nada mas existe.
    //
    // Va como lambda porque ClassHierarchy es una `fun interface`. Sin eso haria falta
    // un `object : ClassHierarchy { override fun ... }` de cuatro lineas.
    private val rules = TypeRules {
        when (it) {
            "Perro", "Gato" -> "Animal"
            else -> null
        }
    }

    private val animal = ClassType("Animal")
    private val perro = ClassType("Perro")
    private val gato = ClassType("Gato")

    // ── A1/A2/A3: aritmetica ───────────────────────────────────────────────

    @Test
    fun `arithmetic entre integers da integer`() {
        assertEquals(IntegerType, rules.arithmetic(BinaryOperator.ADD, IntegerType, IntegerType))
        assertEquals(IntegerType, rules.arithmetic(BinaryOperator.SUBTRACT, IntegerType, IntegerType))
        assertEquals(IntegerType, rules.arithmetic(BinaryOperator.MODULO, IntegerType, IntegerType))
    }

    @Test
    fun `arithmetic con un float ensancha a float`() {
        assertEquals(FloatType, rules.arithmetic(BinaryOperator.ADD, IntegerType, FloatType))
        assertEquals(FloatType, rules.arithmetic(BinaryOperator.MULTIPLY, FloatType, IntegerType))
        assertEquals(FloatType, rules.arithmetic(BinaryOperator.DIVIDE, FloatType, FloatType))
    }

    // A3, decision documentada: el modulo NO aplica a float.
    @Test
    fun `modulo con float es error`() {
        assertNull(rules.arithmetic(BinaryOperator.MODULO, FloatType, IntegerType))
        assertNull(rules.arithmetic(BinaryOperator.MODULO, IntegerType, FloatType))
    }

    // ── C1/C2: concatenacion ───────────────────────────────────────────────

    @Test
    fun `concatenar dos strings da string`() {
        assertEquals(StringType, rules.concatenation(BinaryOperator.ADD, StringType, StringType))
    }

    // C2: sin conversion implicita a string. "texto" + 5 es error, no "texto5".
    @Test
    fun `concatenar string con integer es error`() {
        assertNull(rules.concatenation(BinaryOperator.ADD, StringType, IntegerType))
        assertNull(rules.concatenation(BinaryOperator.ADD, IntegerType, StringType))
        // Y con otro operador tampoco: "a" - "b" no existe.
        assertNull(rules.concatenation(BinaryOperator.SUBTRACT, StringType, StringType))
    }

    // ── L1: logicos ────────────────────────────────────────────────────────

    @Test
    fun `logico entre booleans da boolean`() {
        assertEquals(BooleanType, rules.logical(BooleanType, BooleanType))
    }

    @Test
    fun `logico con integer es error`() {
        assertNull(rules.logical(IntegerType, BooleanType))
        assertNull(rules.logical(BooleanType, IntegerType))
    }

    // ── R1: relacionales ───────────────────────────────────────────────────

    @Test
    fun `relacional entre numericos da boolean`() {
        assertEquals(BooleanType, rules.relational(IntegerType, IntegerType))
        assertEquals(BooleanType, rules.relational(IntegerType, FloatType))
    }

    @Test
    fun `relacional entre strings da boolean`() {
        assertEquals(BooleanType, rules.relational(StringType, StringType))
    }

    @Test
    fun `relacional entre booleans es error`() {
        assertNull(rules.relational(BooleanType, BooleanType))
        // Mezclar familias tampoco: "a" < 1 no tiene sentido.
        assertNull(rules.relational(StringType, IntegerType))
    }

    // ── E1: igualdad ───────────────────────────────────────────────────────

    @Test
    fun `igualdad entre comparables da boolean`() {
        assertEquals(BooleanType, rules.equality(IntegerType, FloatType))
        assertEquals(BooleanType, rules.equality(StringType, StringType))
        // Mas permisiva que la relacional: boolean con boolean SI se puede.
        assertEquals(BooleanType, rules.equality(BooleanType, BooleanType))
    }

    @Test
    fun `igualdad de clase con null da boolean`() {
        assertEquals(BooleanType, rules.equality(perro, NullType))
        assertEquals(BooleanType, rules.equality(NullType, ArrayType(IntegerType)))
    }

    @Test
    fun `igualdad entre string y boolean es error`() {
        assertNull(rules.equality(StringType, BooleanType))
    }

    // ── L2/N1: unarios ─────────────────────────────────────────────────────

    @Test
    fun `not exige boolean`() {
        assertEquals(BooleanType, rules.unary(UnaryOperator.NOT, BooleanType))
        assertNull(rules.unary(UnaryOperator.NOT, IntegerType))
    }

    @Test
    fun `negate preserva el tipo numerico`() {
        assertEquals(IntegerType, rules.unary(UnaryOperator.NEGATE, IntegerType))
        assertEquals(FloatType, rules.unary(UnaryOperator.NEGATE, FloatType))
        assertNull(rules.unary(UnaryOperator.NEGATE, BooleanType))
    }

    // ── S1-S6: asignabilidad ───────────────────────────────────────────────

    @Test
    fun `integer cabe en float pero no al reves`() {
        assertTrue(rules.isAssignable(target = FloatType, source = IntegerType))
        assertFalse(rules.isAssignable(target = IntegerType, source = FloatType))
    }

    @Test
    fun `una subclase cabe en su superclase pero no al reves`() {
        assertTrue(rules.isAssignable(target = animal, source = perro))
        assertFalse(rules.isAssignable(target = perro, source = animal))
        // Hermanas tampoco: un Gato no es un Perro.
        assertFalse(rules.isAssignable(target = perro, source = gato))
    }

    @Test
    fun `null cabe en clases y arreglos pero no en primitivos`() {
        assertTrue(rules.isAssignable(target = perro, source = NullType))
        assertTrue(rules.isAssignable(target = ArrayType(IntegerType), source = NullType))
        assertFalse(rules.isAssignable(target = IntegerType, source = NullType))
        assertFalse(rules.isAssignable(target = StringType, source = NullType))
    }

    // El caso de `let notas: integer[] = [];` — el vacio toma su tipo del contexto.
    @Test
    fun `el arreglo vacio encaja con cualquier arreglo`() {
        assertTrue(rules.isAssignable(target = ArrayType(IntegerType), source = ArrayType(NullType)))
        assertTrue(rules.isAssignable(target = ArrayType(perro), source = ArrayType(NullType)))
    }

    // S5, decision documentada: el agujero de Java, cerrado en compilacion.
    @Test
    fun `los arreglos no son covariantes`() {
        assertFalse(rules.isAssignable(target = ArrayType(FloatType), source = ArrayType(IntegerType)))
        // Ni siquiera con subtipado adentro: Perro[] no cabe en Animal[].
        assertFalse(rules.isAssignable(target = ArrayType(animal), source = ArrayType(perro)))
    }

    @Test
    fun `ErrorType corta cascadas`() {
        // En cualquier direccion y contra cualquier tipo.
        assertTrue(rules.isAssignable(target = IntegerType, source = ErrorType))
        assertTrue(rules.isAssignable(target = ErrorType, source = StringType))

        // Y en todas las reglas de operadores: el error se propaga, no se duplica.
        assertEquals(ErrorType, rules.arithmetic(BinaryOperator.ADD, ErrorType, IntegerType))
        assertEquals(ErrorType, rules.logical(ErrorType, BooleanType))
        assertEquals(ErrorType, rules.relational(IntegerType, ErrorType))
        assertEquals(ErrorType, rules.equality(ErrorType, StringType))
        assertEquals(ErrorType, rules.unary(UnaryOperator.NOT, ErrorType))
        assertEquals(ErrorType, rules.unify(ErrorType, IntegerType))
    }

    // ── U1-U5: unificacion ─────────────────────────────────────────────────

    @Test
    fun `unify de numericos ensancha`() {
        assertEquals(FloatType, rules.unify(IntegerType, FloatType))
        assertEquals(IntegerType, rules.unify(IntegerType, IntegerType))
    }

    // El caso de [[1, 2], [3.5, 4.0]]: sin recursion, error sin razon.
    @Test
    fun `unify recursa en arreglos`() {
        assertEquals(
            ArrayType(FloatType),
            rules.unify(ArrayType(IntegerType), ArrayType(FloatType))
        )
    }

    @Test
    fun `unify de clases hermanas da el ancestro comun`() {
        assertEquals(animal, rules.unify(perro, gato))
        // Y de una clase con su superclase, la superclase.
        assertEquals(animal, rules.unify(perro, animal))
    }

    @Test
    fun `unify sin tipo comun devuelve null`() {
        assertNull(rules.unify(StringType, BooleanType))
        assertNull(rules.unify(IntegerType, perro))
        // Dos clases sin ancestro comun tampoco unifican.
        assertNull(rules.unify(perro, ClassType("Piedra")))
    }

    @Test
    fun `unify de null con clase da la clase`() {
        assertEquals(perro, rules.unify(NullType, perro))
        assertEquals(ArrayType(IntegerType), rules.unify(ArrayType(IntegerType), NullType))
    }

    // ── W1/W2 e isNumeric ──────────────────────────────────────────────────

    @Test
    fun `widen elige el mas ancho`() {
        assertEquals(IntegerType, rules.widen(IntegerType, IntegerType))
        assertEquals(FloatType, rules.widen(IntegerType, FloatType))
        assertEquals(FloatType, rules.widen(FloatType, IntegerType))
    }

    @Test
    fun `widen fuera de numericos lanza IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            rules.widen(StringType, BooleanType)
        }
    }

    @Test
    fun `isNumeric acepta integer y float y nada mas`() {
        assertTrue(rules.isNumeric(IntegerType))
        assertTrue(rules.isNumeric(FloatType))
        assertFalse(rules.isNumeric(StringType))
        assertFalse(rules.isNumeric(BooleanType))
        assertFalse(rules.isNumeric(ArrayType(IntegerType)))
    }

    @Test
    fun `dos jerarquias distintas dan respuestas distintas`() {
        val conHerencia = TypeRules { if (it == "Perro") "Animal" else null }
        val sinHerencia = TypeRules { null }

        assertTrue(conHerencia.isAssignable(target = animal, source = perro))
        assertFalse(sinHerencia.isAssignable(target = animal, source = perro))
    }
}
