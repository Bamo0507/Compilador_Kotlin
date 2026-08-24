package org.compiler.frontend.semantic

import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.semantic.symbols.*

// Lo mínimo que TypeRules necesita saber del mundo exterior: la jerarquía de clases,
// que hace falta para el subtipado. Se inyecta para que TypeRules no dependa de
// Scope y se pueda probar sin construir ámbitos.
fun interface ClassHierarchy {
    fun superclassOf(className: String): String?
}

// Las reglas de tipo del lenguaje. No conoce el AST ni los ámbitos:
// recibe tipos y devuelve tipos. Aislado para poder leerlo y probarlo solo.
//
// Es una CLASE y no un `object` porque el subtipado necesita la jerarquía inyectada,
// y un `object` no tiene constructor. Las alternativas eran pasar la jerarquía como
// parámetro en los ~15 sitios que llaman a isAssignable, o guardarla en un `var` del
// object — que violaría el principio 6 (nada de `object` con estado mutable).
class TypeRules(private val hierarchy: ClassHierarchy) {

    fun isNumeric(type: Type): Boolean =
        type == IntegerType || type == FloatType

    // El tipo más ancho de dos numéricos. integer + float = float.
    //
    // PRECONDICION: los dos son numéricos. Sin el require, widen(string, boolean)
    // devolvería IntegerType en silencio.
    fun widen(left: Type, right: Type): Type {
        require(isNumeric(left) && isNumeric(right)) {
            "widen solo aplica a tipos numéricos, recibió '${left.name}' y '${right.name}'"
        }
        return if (left == FloatType || right == FloatType) FloatType else IntegerType
    }

    // ¿Se puede guardar un valor de tipo `source` en algo declarado `target`?
    //
    // Es la regla más usada del compilador: asignaciones, argumentos de llamada,
    // valor de retorno, e inicializadores usan todos esta función.
    fun isAssignable(target: Type, source: Type): Boolean = when {
        // Un error ya reportado se acepta en silencio: corta cascadas.
        target == ErrorType || source == ErrorType -> true

        target == source -> true

        // Ensanchamiento: un integer cabe en un float. Al revés NO,
        // porque habría pérdida de precisión y el lenguaje no tiene casts.
        target == FloatType && source == IntegerType -> true

        // null solo cabe en clases y arreglos, no en los tipos simples.
        source == NullType && (target is ClassType || target is ArrayType) -> true

        // Un arreglo VACIO llega como ArrayType(NullType) y encaja con cualquier
        // arreglo: es el contexto el que dice de qué es.
        //   let notas: integer[] = [];
        source is ArrayType && source.element == NullType && target is ArrayType -> true

        // Subtipado: un Perro cabe donde se pide un Animal.
        target is ClassType && source is ClassType -> isSubclassOf(source, target)

        // Los arreglos NO son covariantes: ver la decisión abajo.
        else -> false
    }

    // El tipo común de dos ramas. Lo usan el ternario, el literal de arreglo y la
    // igualdad. Devuelve null si no hay tipo común: eso es un error.
    fun unify(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == right -> left
        isNumeric(left) && isNumeric(right) -> widen(left, right)
        left == NullType && (right is ClassType || right is ArrayType) -> right
        right == NullType && (left is ClassType || left is ArrayType) -> left
        left is ClassType && right is ClassType -> commonAncestor(left, right)

        // Recursivo en arreglos: el tipo común de integer[] y float[] es float[].
        // Sin esta rama, [[1, 2], [3.5, 4.0]] daría error sin razón.
        left is ArrayType && right is ArrayType ->
            unify(left.element, right.element)?.let { ArrayType(it) }

        else -> null
    }

    // ── Regla A1/A2/A3: aritmética ──────────────────────────────────────────
    //
    //   Γ ⊢ M : integer      Γ ⊢ N : integer      op ∈ {+ − * / %}
    //   ──────────────────────────────────────────────────────────
    //                  Γ ⊢ M op N : integer
    //
    //   Γ ⊢ M : τ₁   Γ ⊢ N : τ₂   τ₁,τ₂ ∈ {integer,float}   τ₁=float ∨ τ₂=float
    //   op ∈ {+ − * /}
    //   ────────────────────────────────────────────────────────────────────
    //                        Γ ⊢ M op N : float
    //
    //   A3 (decisión documentada): % NO aplica a float. Solo integer.
    fun arithmetic(op: BinaryOperator, left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        op == BinaryOperator.MODULO ->
            if (left == IntegerType && right == IntegerType) IntegerType else null
        isNumeric(left) && isNumeric(right) -> widen(left, right)
        else -> null
    }

    // ── Regla C1: concatenación ─────────────────────────────────────────────
    //
    //   Γ ⊢ M : string      Γ ⊢ N : string
    //   ──────────────────────────────────
    //         Γ ⊢ M + N : string
    //
    // Solo el operador +, y solo si AMBOS lados son string. Se decide que
    // "texto" + 5 sea ERROR y no concatenación implícita: el lenguaje no tiene
    // conversión automática a string, y aceptarla escondería errores de tipo.
    fun concatenation(op: BinaryOperator, left: Type, right: Type): Type? =
        if (op == BinaryOperator.ADD && left == StringType && right == StringType) StringType
        else null

    // ── Regla L1: lógicos ───────────────────────────────────────────────────
    //
    //   Γ ⊢ M : boolean     Γ ⊢ N : boolean     op ∈ {&& ||}
    //   ────────────────────────────────────────────────────
    //               Γ ⊢ M op N : boolean
    fun logical(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == BooleanType && right == BooleanType -> BooleanType
        else -> null
    }

    // ── Regla R1: relacionales ──────────────────────────────────────────────
    //
    //   Γ ⊢ M : τ₁   Γ ⊢ N : τ₂   (τ₁,τ₂ numéricos) ∨ (τ₁=τ₂=string)
    //   op ∈ {< <= > >=}
    //   ────────────────────────────────────────────────────────────
    //                     Γ ⊢ M op N : boolean
    fun relational(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        isNumeric(left) && isNumeric(right) -> BooleanType
        left == StringType && right == StringType -> BooleanType
        else -> null
    }

    // ── Regla E1: igualdad ──────────────────────────────────────────────────
    //
    //   Γ ⊢ M : τ₁    Γ ⊢ N : τ₂    comparables(τ₁, τ₂)
    //   ───────────────────────────────────────────────
    //              Γ ⊢ M == N : boolean
    //
    // Es más permisiva que la relacional: además de numéricos y strings, admite
    // boolean con boolean, y null con cualquier clase o arreglo.
    fun equality(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        unify(left, right) != null -> BooleanType
        else -> null
    }

    // ── Regla L2/N1: unarios ────────────────────────────────────────────────
    //
    //   Γ ⊢ M : boolean          Γ ⊢ M : τ    τ ∈ {integer, float}
    //   ────────────────         ─────────────────────────────────
    //   Γ ⊢ !M : boolean                  Γ ⊢ −M : τ
    fun unary(op: UnaryOperator, operand: Type): Type? = when {
        operand == ErrorType -> ErrorType
        op == UnaryOperator.NOT    -> if (operand == BooleanType) BooleanType else null
        op == UnaryOperator.NEGATE -> if (isNumeric(operand)) operand else null
        else -> null
    }

    // ¿`sub` es `sup` o desciende de ella? Camina la cadena de superclases.
    // El `visited` corta si la jerarquía tuviera un ciclo (A : B, B : A): eso lo
    // reporta la Fase 3, pero aqui no debemos colgarnos si llega igual.
    private fun isSubclassOf(sub: ClassType, sup: ClassType): Boolean {
        val visited = mutableSetOf<String>()
        var current: String? = sub.name
        while (current != null && visited.add(current)) {
            if (current == sup.name) return true
            current = hierarchy.superclassOf(current)
        }
        return false
    }

    // El ancestro comun mas cercano de dos clases, o null si no comparten ninguno.
    private fun commonAncestor(left: ClassType, right: ClassType): Type? {
        val ancestrosDeLeft = mutableSetOf<String>()
        var current: String? = left.name
        while (current != null && ancestrosDeLeft.add(current)) {
            current = hierarchy.superclassOf(current)
        }

        current = right.name
        val visited = mutableSetOf<String>()
        while (current != null && visited.add(current)) {
            if (current in ancestrosDeLeft) return ClassType(current)
            current = hierarchy.superclassOf(current)
        }
        return null
    }
}

