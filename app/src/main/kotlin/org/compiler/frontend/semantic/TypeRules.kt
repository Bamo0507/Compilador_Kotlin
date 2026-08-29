package org.compiler.frontend.semantic

import org.compiler.frontend.ast.models.BinaryOperator
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.semantic.symbols.*

fun interface ClassHierarchy {
    fun superclassOf(className: String): String?
}

class TypeRules(private val hierarchy: ClassHierarchy) {

    // ¿Participa en aritmetica? ErrorType queda fuera a proposito: todos los
    // llamadores lo manejan ANTES de preguntar.
    fun isNumeric(type: Type): Boolean =
        type == IntegerType || type == FloatType

    // El mas ancho de dos numericos:  1 + 2.5  ->  float
    //
    // Es SIMETRICA, y por eso unify puede usarla. El require protege contra un bug
    // nuestro: sin el, widen(string, boolean) devolveria IntegerType en silencio.
    fun widen(left: Type, right: Type): Type {
        require(isNumeric(left) && isNumeric(right)) {
            "widen solo aplica a tipos numéricos, recibió '${left.name}' y '${right.name}'"
        }
        return if (left == FloatType || right == FloatType) FloatType else IntegerType
    }

    // ¿Cabe un valor de tipo `source` en algo declarado `target`?
    //
    // Es DIRECCIONAL: isAssignable(float, integer) es true y al reves no. La usan las
    // asignaciones, los argumentos de llamada, el retorno y los inicializadores.
    fun isAssignable(target: Type, source: Type): Boolean = when {
        // Primero, para que un error ya reportado no produzca un segundo mensaje.
        target == ErrorType || source == ErrorType -> true

        // let x: integer = 5;   el caso normal, los dos son el mismo tipo.
        target == source -> true

        // let x: float = 4;   el 4 se ensancha. Al reves seria perdida de precision.
        target == FloatType && source == IntegerType -> true

        // let p: Perro = null;   pero `let n: integer = null;` es error.
        source == NullType && (target is ClassType || target is ArrayType) -> true

        // let notas: integer[] = [];   el arreglo vacio llega como ArrayType(NullType)
        // y toma su tipo del contexto.
        source is ArrayType && source.element == NullType && target is ArrayType -> true

        // let a: Animal = new Perro();
        target is ClassType && source is ClassType -> isSubclassOf(source, target)

        // Aqui cae la covarianza de arreglos: Perro[] NO cabe en Animal[]. Si se
        // permitiera, `animales[0] = new Gato()` corromperia el arreglo de perros.
        else -> false
    }

    // El tipo comun de dos tipos, o null si no lo tienen.
    //
    // Es SIMETRICA, al reves que isAssignable. La usan el ternario, el literal de
    // arreglo y la igualdad, que son los tres casos donde ningun lado manda.
    fun unify(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType

        left == right -> left

        // cond ? 1 : 2.5   ->  float
        isNumeric(left) && isNumeric(right) -> widen(left, right)

        // cond ? null : perro   ->  Perro
        left == NullType && (right is ClassType || right is ArrayType) -> right
        right == NullType && (left is ClassType || left is ArrayType) -> left

        // cond ? perro : gato   ->  Animal, si las dos heredan de el
        left is ClassType && right is ClassType -> commonAncestor(left, right)

        // [[1, 2], [3.5, 4.0]]   ->  float[][]
        // Sin esta rama daria error, porque ArrayType(integer) != ArrayType(float).
        left is ArrayType && right is ArrayType ->
            unify(left.element, right.element)?.let { ArrayType(it) }

        // cond ? 1 : "a"   ->  sin tipo comun, y eso es un error
        else -> null
    }

    // Solo se llama cuando el operador es del grupo ARITHMETIC, asi que no verifica
    // eso: lo garantiza el `when (expr.operator.group)` del TypeChecker.
    fun arithmetic(op: BinaryOperator, left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType

        // A3: el modulo NO aplica a float. 5 % 2 va; 5.0 % 2 es error.
        op == BinaryOperator.MODULO ->
            if (left == IntegerType && right == IntegerType) IntegerType else null

        // A1: 1 + 2 -> integer.   A2: 1 + 2.5 -> float.
        isNumeric(left) && isNumeric(right) -> widen(left, right)

        // "a" + 1 cae aqui, y el TypeChecker prueba despues con concatenation.
        else -> null
    }

    // "a" + "b" -> string.  Los DOS lados tienen que ser string: no hay conversion
    // implicita, asi que "total: " + 5 es error y no "total: 5".
    //
    // A diferencia de arithmetic, esta si verifica el operador: es el unico caso del
    // grupo ARITHMETIC que no es aritmetica, y el TypeChecker la prueba en cadena
    // despues de que arithmetic devuelve null.
    fun concatenation(op: BinaryOperator, left: Type, right: Type): Type? =
        if (op == BinaryOperator.ADD && left == StringType && right == StringType) StringType
        else null

    // La regla mas estricta de las cuatro: && y || exigen boolean de los dos lados.
    // `1 && true` es error, no hay conversion de entero a booleano.
    fun logical(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        left == BooleanType && right == BooleanType -> BooleanType
        else -> null
    }

    // < <= > >= sobre cosas ORDENABLES. Devuelven boolean, no el tipo de los operandos.
    fun relational(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType

        // 1 < 2.5   se comparan como numeros, aunque sean de tipo distinto
        isNumeric(left) && isNumeric(right) -> BooleanType

        // "a" < "b"   orden alfabetico
        left == StringType && right == StringType -> BooleanType

        // true < false no tiene sentido, y "a" < 1 mezcla familias
        else -> null
    }

    // Mas permisiva que la relacional: si los dos tipos tienen algo en comun, se pueden
    // comparar. Eso agrega boolean con boolean, y null con clases y arreglos.
    //
    // Se apoya en unify porque "son comparables" y "tienen un tipo comun" son la misma
    // pregunta:  1 == 1.0 va,  "a" == true no.
    fun equality(left: Type, right: Type): Type? = when {
        left == ErrorType || right == ErrorType -> ErrorType
        unify(left, right) != null -> BooleanType
        else -> null
    }

    // El NEGATE devuelve `operand` y no un tipo fijo: -5 es integer y -2.5 es float.
    fun unary(op: UnaryOperator, operand: Type): Type? = when {
        operand == ErrorType -> ErrorType
        op == UnaryOperator.NOT -> if (operand == BooleanType) BooleanType else null
        op == UnaryOperator.NEGATE -> if (isNumeric(operand)) operand else null
        else -> null
    }

    // ¿Perro hereda de Animal, directa o indirectamente? Sube la cadena de herencia.
    //
    // El `visited` corta si la jerarquia trae un ciclo. La Fase 3 los reporta, pero aqui
    // la jerarquia llega por una lambda inyectada y no hay garantia de que sea acorde.
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
    //
    // Junta TODOS los ancestros del izquierdo en un conjunto, y despues sube por el
    // derecho hasta encontrar el primero que este ahi: ese es el mas cercano.
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

