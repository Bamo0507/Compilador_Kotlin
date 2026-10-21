package org.compiler.engine

import java.util.TreeMap
import org.compiler.catalog.Catalog
import org.compiler.catalog.Index
import org.compiler.frontend.ast.models.Binary
import org.compiler.frontend.ast.models.ColumnReference
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.InList
import org.compiler.frontend.ast.models.Literal
import org.compiler.types.BinaryOperator
import org.compiler.types.NullValue
import org.compiler.types.OperatorCategory
import org.compiler.types.Value

/**
 * Que filas pedirle a un indice para responder un WHERE.
 *
 * Es el plan, no el resultado: dice que indice sirve y que pedazo del arbol hay
 * que mirar. Quien lo ejecuta es el motor, que ya tiene la sesion.
 */
class IndexPlan(val index: Index, private val lookup: Lookup) {

    // Los desplazamientos de las filas que pueden casar, en el orden del archivo
    // para que los saltos vayan hacia adelante.
    fun offsets(entries: TreeMap<Value, MutableList<Long>>): List<Long> =
        lookup.select(entries).flatten().sorted()

    sealed interface Lookup {
        fun select(entries: TreeMap<Value, MutableList<Long>>): Collection<List<Long>>

        // `marca = 'Toyota'`, y tambien el `IN`, que son varias igualdades.
        class Exact(private val values: List<Value>) : Lookup {
            override fun select(entries: TreeMap<Value, MutableList<Long>>) =
                values.mapNotNull { entries[it] }
        }

        /**
         * `anio > 2020`, `BETWEEN`, y en general cualquier comparacion de orden.
         *
         * Es lo que justifica el arbol sobre una tabla hash: un HashMap solo puede
         * contestar la igualdad, y el enunciado habla de condiciones de WHERE.
         */
        class Range(
            private val from: Value?,
            private val fromInclusive: Boolean,
            private val to: Value?,
            private val toInclusive: Boolean
        ) : Lookup {
            override fun select(entries: TreeMap<Value, MutableList<Long>>): Collection<List<Long>> {
                var view: java.util.SortedMap<Value, MutableList<Long>> = entries
                if (from != null) view = (view as TreeMap<Value, MutableList<Long>>).tailMap(from, fromInclusive)
                if (to != null) view = (view as TreeMap<Value, MutableList<Long>>).headMap(to, toInclusive)
                return view.values
            }
        }
    }
}

/**
 * Elige el indice de una consulta.
 *
 * **Regla:** si hay indice utilizable, se usa. Un DBMS real guarda estadisticas de
 * cardinalidad y a veces decide ignorarlo, porque si `marca = 'Toyota'` casa con
 * 3,000 de 10,000 filas, hacer 3,000 saltos sale mas lento que leer de corrido.
 * Esa es la limitacion conocida de esta fase: sin estadisticas no hay con que
 * decidir, y un planificador por costos es otra fase entera.
 */
object IndexPlanner {

    /**
     * El plan para leer `table` con esta condicion, o null si no hay ninguno.
     *
     * Solo mira las conjunciones de PRIMER NIVEL. `marca = 'Toyota' AND anio > 2020`
     * sirve, porque toda fila del resultado cumple las dos; `marca = 'Toyota' OR
     * anio > 2020` no, porque el OR puede traer filas que el indice no señala.
     *
     * Si dos columnas estan indexadas se toma LA PRIMERA. Elegir la mejor necesita
     * saber cuantas filas casa cada una, que es justo la estadistica que no hay.
     */
    fun plan(table: String, where: Expression?, catalog: Catalog): IndexPlan? {
        val schema = catalog.table(table) ?: return null
        if (where == null || schema.indexes.isEmpty()) return null

        return conjunctions(where).firstNotNullOfOrNull { condition ->
            planOf(condition, table, schema::indexOn)
        }
    }

    // Los AND de primer nivel, aplanados. Un OR queda entero y no se abre: el plan
    // de una de sus ramas no cubre la otra.
    private fun conjunctions(expression: Expression): List<Expression> {
        if (expression is Binary && expression.operator == BinaryOperator.AND) {
            return conjunctions(expression.left) + conjunctions(expression.right)
        }
        return listOf(expression)
    }

    private fun planOf(
        condition: Expression,
        table: String,
        indexOn: (String) -> Index?
    ): IndexPlan? = when (condition) {
        is Binary -> comparisonPlan(condition, table, indexOn)
        is InList -> inPlan(condition, table, indexOn)
        else -> null
    }

    // `columna op constante`, o `constante op columna`, que es lo mismo con la
    // comparacion al reves.
    private fun comparisonPlan(
        condition: Binary,
        table: String,
        indexOn: (String) -> Index?
    ): IndexPlan? {
        if (condition.operator.category != OperatorCategory.EQUALITY &&
            condition.operator.category != OperatorCategory.ORDER
        ) {
            return null
        }

        val left = indexedColumn(condition.left, table, indexOn)
        val right = indexedColumn(condition.right, table, indexOn)

        val (index, value, operator) = when {
            left != null -> Triple(left, constantOf(condition.right), condition.operator)
            right != null -> Triple(right, constantOf(condition.left), flip(condition.operator))
            else -> return null
        }
        if (value == null || value == NullValue) return null

        val lookup = when (operator) {
            BinaryOperator.EQUAL -> IndexPlan.Lookup.Exact(listOf(value))
            BinaryOperator.GREATER -> IndexPlan.Lookup.Range(value, false, null, false)
            BinaryOperator.GREATER_EQUAL -> IndexPlan.Lookup.Range(value, true, null, false)
            BinaryOperator.LESS -> IndexPlan.Lookup.Range(null, false, value, false)
            BinaryOperator.LESS_EQUAL -> IndexPlan.Lookup.Range(null, false, value, true)

            // `<>` casa con casi todo: el indice no ahorraria nada.
            else -> return null
        }
        return IndexPlan(index, lookup)
    }

    // `marca IN ('Mazda', 'Nissan')`: varias igualdades, una busqueda por cada una.
    private fun inPlan(condition: InList, table: String, indexOn: (String) -> Index?): IndexPlan? {
        if (condition.negated) return null
        val index = indexedColumn(condition.operand, table, indexOn) ?: return null

        val values = condition.values.map { constantOf(it) ?: return null }
        if (values.isEmpty() || values.any { it == NullValue }) return null

        return IndexPlan(index, IndexPlan.Lookup.Exact(values))
    }

    /**
     * El indice sobre la columna de esta referencia, si la hay.
     *
     * `sourceTable` es lo que deja saber que `u.id` con `FROM users u` es una
     * columna de users: la fase 4 ya resolvio el alias.
     */
    private fun indexedColumn(
        expression: Expression,
        table: String,
        indexOn: (String) -> Index?
    ): Index? {
        val reference = expression as? ColumnReference ?: return null
        val symbol = reference.symbol ?: return null

        // Nivel distinto de 0 es una columna de la consulta de afuera: su valor
        // cambia por fila y no se puede buscar una sola vez.
        if (reference.level != 0 || symbol.sourceTable != table) return null
        return indexOn(symbol.name)
    }

    private fun constantOf(expression: Expression): Value? = (expression as? Literal)?.value

    // `5 < anio` es `anio > 5`.
    private fun flip(operator: BinaryOperator): BinaryOperator = when (operator) {
        BinaryOperator.LESS -> BinaryOperator.GREATER
        BinaryOperator.LESS_EQUAL -> BinaryOperator.GREATER_EQUAL
        BinaryOperator.GREATER -> BinaryOperator.LESS
        BinaryOperator.GREATER_EQUAL -> BinaryOperator.LESS_EQUAL
        else -> operator
    }
}
