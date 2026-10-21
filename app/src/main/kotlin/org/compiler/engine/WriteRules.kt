package org.compiler.engine

import org.compiler.catalog.AutoIncrement
import org.compiler.catalog.Column
import org.compiler.catalog.Default
import org.compiler.catalog.ForeignKey
import org.compiler.catalog.PrimaryKey
import org.compiler.catalog.Table
import org.compiler.catalog.Unique
import org.compiler.diagnostics.CompilerError
import org.compiler.models.LexemeLocation
import org.compiler.types.CharType
import org.compiler.types.DecimalType
import org.compiler.types.DecimalValue
import org.compiler.types.IntValue
import org.compiler.types.NullValue
import org.compiler.types.StringValue
import org.compiler.types.Value
import org.compiler.types.ValueCodec
import org.compiler.types.VarcharType
import java.math.RoundingMode

/**
 * Lo que se revisa antes de aceptar una fila.
 *
 * Es lo que de verdad protege la base, porque el DBMS confia en lo que el mismo
 * escribio: valida lo que entra, no lo que ya esta guardado.
 *
 * El orden importa. El valor por omision y el AUTOINCREMENT van ANTES de la clave
 * primaria, porque rellenan valores que despues hay que verificar: si el numero lo
 * genera el AUTOINCREMENT, revisar la clave antes no tendria que revisar.
 */
class WriteRules(private val session: Session) {

    /**
     * La fila completa de un INSERT, con lo omitido ya rellenado.
     *
     * Devuelve null si algo no cuadra, y en ese caso ya reporto por que.
     */
    fun buildRow(table: Table, named: List<Column>, values: List<Value>, location: LexemeLocation): Row? {
        val given = named.indices.associate { named[it].name to values[it] }

        val cells = table.columns.map { column ->
            when {
                column.name in given -> given.getValue(column.name)
                column.hasConstraint<AutoIncrement>() -> IntValue(nextSerial(table, column))
                else -> fillerFor(column)
            }
        }

        val row = prepare(table, Row(cells), location) ?: return null
        return if (validate(table, row, location)) row else null
    }

    // Ajusta cada celda a su columna: rellena un CHAR, redondea un DECIMAL, y
    // rechaza lo que no cabe.
    fun prepare(table: Table, row: Row, location: LexemeLocation): Row? {
        val cells = table.columns.mapIndexed { position, column ->
            fit(row.values[position], column, location) ?: return null
        }
        return Row(cells)
    }

    /**
     * Revisa la fila ENTERA, no solo lo que se toco.
     *
     * Un UPDATE que cambia la edad no puede romper la clave primaria, pero uno que
     * cambia el id si, y la unica forma robusta de saberlo es mirar la fila como
     * quedo.
     */
    fun validate(table: Table, row: Row, location: LexemeLocation, excluding: Int? = null): Boolean {
        var valid = true

        table.columns.forEachIndexed { position, column ->
            val value = row.values[position]

            if (value == NullValue) {
                if (!column.nullable) {
                    report(location, "la columna '${column.name}' no acepta nulos")
                    valid = false
                }
                return@forEachIndexed
            }

            if (unique(column) && duplicated(table, position, value, excluding)) {
                report(location, "ya existe una fila con ${column.name} = ${value.display()} en '${table.name}'")
                valid = false
            }

            val reference = column.reference
            if (reference != null && !present(reference, value)) {
                report(
                    location,
                    "${table.name}.${column.name} = ${value.display()} no existe en " +
                        "${reference.table}.${reference.column}"
                )
                valid = false
            }
        }
        return valid
    }

    /**
     * Las filas de otras tablas que apuntan a esta.
     *
     * Las llaves foraneas se revisan al reves al borrar: quitar un usuario es
     * error si alguna fila de posts lo referencia.
     */
    fun referencesTo(table: Table, row: Row): Pair<String, Int>? = session.catalog.tables.values
        .filterNot { it.name == table.name }
        .firstNotNullOfOrNull { other -> pointing(other, table, row) }

    private fun pointing(other: Table, table: Table, row: Row): Pair<String, Int>? {
        other.columns.forEachIndexed { position, column ->
            val reference = column.reference ?: return@forEachIndexed
            if (reference.table != table.name) return@forEachIndexed

            val target = table.indexOf(reference.column)
            if (target < 0) return@forEachIndexed

            val value = row.values[target]
            if (value == NullValue) return@forEachIndexed

            val count = session.rowsOf(other.name).count { ValueOrder.compare(it.values[position], value) == 0 }
            if (count > 0) return other.name to count
        }
        return null
    }

    // ── Las piezas ─────────────────────────────────────────────────────────

    /**
     * El siguiente numero es el maximo actual mas uno.
     *
     * Es correcto sin guardar estado en ningun lado, y el costo es que si se borra
     * la ultima fila el numero se reusa. Es una simplificacion consciente.
     */
    private fun nextSerial(table: Table, column: Column): Long {
        val position = table.indexOf(column.name)
        val highest = session.rowsOf(table.name)
            .mapNotNull { ValueOrder.toLong(it.values.getOrNull(position) ?: NullValue) }
            .maxOrNull() ?: 0L

        return highest + 1
    }

    // Con que nace una celda que nadie lleno: su valor por omision, o nulo.
    fun fillerFor(column: Column): Value {
        val default = column.constraints.filterIsInstance<Default>().firstOrNull() ?: return NullValue
        return ValueCodec.decode(default.value, column.type) ?: NullValue
    }

    /**
     * El largo y la escala, que el semantico no pudo verificar.
     *
     * La fase 5 revisa el largo solo sobre literales, porque `name || apellido` no
     * tiene largo conocido hasta evaluarlo. Aqui el valor ya esta calculado.
     */
    private fun fit(value: Value, column: Column, location: LexemeLocation): Value? = when {
        value == NullValue -> value

        value is StringValue && column.type is CharType -> {
            val limit = (column.type as CharType).length
            if (value.value.trimEnd().length > limit) tooLong(value, column, location)
            else StringValue(value.value.trimEnd().padEnd(limit))
        }

        value is StringValue && column.type is VarcharType ->
            if (value.value.length > (column.type as VarcharType).maxLength) tooLong(value, column, location) else value

        value is DecimalValue && column.type is DecimalType -> rescale(value, column, location)

        else -> value
    }

    private fun tooLong(value: StringValue, column: Column, location: LexemeLocation): Value? {
        report(location, "'${value.value}' no cabe en ${column.type.name}")
        return null
    }

    // Los decimales de mas se redondean, que es lo que hace un DBMS al insertar.
    // La parte entera no: ahi el numero no cabe y no hay nada que recortar.
    private fun rescale(value: DecimalValue, column: Column, location: LexemeLocation): Value? {
        val type = column.type as DecimalType
        val scaled = value.value.setScale(type.scale, RoundingMode.HALF_UP)

        if (scaled.precision() - scaled.scale() > type.precision - type.scale) {
            report(location, "${value.display()} no cabe en ${type.name}")
            return null
        }
        return DecimalValue(scaled)
    }

    private fun unique(column: Column) = column.hasConstraint<PrimaryKey>() || column.hasConstraint<Unique>()

    // La fila que se esta cambiando se excluye por POSICION: si no, poner id = 5
    // en la fila que ya tiene id = 5 se reportaria como duplicado consigo misma.
    private fun duplicated(table: Table, position: Int, value: Value, excluding: Int?): Boolean =
        session.rowsOf(table.name).withIndex().any { (index, row) ->
            index != excluding && ValueOrder.compare(row.values.getOrNull(position) ?: NullValue, value) == 0
        }

    /**
     * Si el valor existe en la tabla referida.
     *
     * Una columna referida es unica por la regla de la fase 5, asi que SIEMPRE
     * tiene indice: es uno de los automaticos. Con el, la validacion es una
     * busqueda en el arbol en vez de cargar la tabla entera, que era lo que la
     * fase 7 tenia que hacer.
     *
     * Si la tabla ya esta en memoria se busca ahi, por la misma razon de siempre:
     * reabrir el archivo seria mas lento.
     */
    private fun present(reference: ForeignKey, value: Value): Boolean {
        val target = session.catalog.table(reference.table) ?: return true
        val position = target.indexOf(reference.column)
        if (position < 0) return true

        if (!session.isLoaded(reference.table)) {
            val index = target.indexOn(reference.column)
            val entries = index?.let { session.entriesOf(it) }
            if (entries != null) return entries.containsKey(value)
        }

        return session.rowsOf(reference.table).any {
            ValueOrder.compare(it.values.getOrNull(position) ?: NullValue, value) == 0
        }
    }

    private fun report(location: LexemeLocation, message: String) {
        session.diagnostics.report(CompilerError.ExecutionError(location, message))
    }
}
