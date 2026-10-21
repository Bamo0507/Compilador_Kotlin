package org.compiler.engine

import org.compiler.catalog.Catalog
import org.compiler.catalog.Index
import org.compiler.catalog.Table
import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Script
import org.compiler.models.LexemeLocation
import org.compiler.storage.CsvReader
import org.compiler.storage.DataDirectory
import org.compiler.storage.IndexFile
import org.compiler.storage.RowReader
import org.compiler.types.NullValue
import org.compiler.types.Value
import org.compiler.types.ValueCodec
import java.io.IOException

/**
 * Todo lo que pasa durante una corrida y todavia no llego al disco.
 *
 * Es a la vez el cache de lectura Y el almacen de escritura, y eso es a
 * proposito: si fueran dos objetos, un INSERT escribiria en uno y el SELECT de la
 * linea siguiente leeria del otro, y no veria la fila nueva.
 *
 * Una instancia por corrida, nunca un singleton: dos corridas seguidas con el
 * mismo estado se pisarian, y habria que acordarse de limpiarlo entre una y otra.
 */
class Session(
    private val directory: DataDirectory,
    initialCatalog: Catalog,
    val diagnostics: Diagnostics
) {

    private val originalCatalog = initialCatalog

    // Muta con CREATE, ALTER y DROP. El inicial se guarda para saber al final que
    // esquemas cambiaron.
    var catalog = initialCatalog
        private set

    private val loaded = mutableMapOf<String, MutableList<Row>>()
    private val modified = mutableSetOf<String>()

    // Los .idx que hay que borrar del disco al volcar. Un DROP INDEX no toca el
    // archivo en el momento, igual que ninguna otra sentencia toca el disco.
    private val droppedIndexes = mutableListOf<Index>()

    // Las tres cuentas de la corrida. Sirven para la defensa, y son lo que deja
    // probar que el cache de subconsultas y el corte del EXISTS funcionan de
    // verdad y no solo en el papel.
    var reads = 0
        private set

    // Consultas ejecutadas, subconsultas incluidas.
    var executions = 0
        private set

    // Filas que el motor miro para decidir si pasaban un filtro.
    var examined = 0
        private set

    // Filas traidas por desplazamiento, sin recorrer el archivo. Es la cuenta que
    // deja PROBAR que el indice se uso: un WHERE sobre mil filas que lee una.
    var indexedReads = 0
        private set

    internal fun countExecution() {
        executions++
    }

    internal fun countExamined() {
        examined++
    }

    /**
     * Las filas de una tabla. Las lee del disco la primera vez; despues devuelve
     * las que ya estan, incluidos los cambios de este mismo script.
     *
     * Devuelve la lista viva, no una copia: el escritor de la fase 7 la modifica
     * en el lugar.
     */
    fun rowsOf(table: String): MutableList<Row> = loaded.getOrPut(table) { load(table) }

    fun markModified(table: String) {
        modified.add(table)
    }

    fun dropIndexFile(index: Index) {
        droppedIndexes.add(index)
    }

    // Si la tabla ya esta en memoria. Si lo esta, filtrar ahi es mas barato que
    // volver a abrir el archivo para saltar a unas filas.
    fun isLoaded(table: String): Boolean = table in loaded

    /**
     * Las filas que estan en los desplazamientos dados, sin cargar la tabla.
     *
     * No puebla el cache a proposito: son una PARTE de la tabla, y marcarla como
     * cargada haria que la consulta siguiente viera solo esas. Distinguir una
     * carga parcial de una completa seria estado de mas para lo que da.
     */
    fun rowsAt(table: String, offsets: List<Long>): List<Row> {
        val schema = catalog.table(table) ?: return emptyList()
        val file = directory.csv(table)
        if (!file.isFile) return emptyList()

        return offsets.mapNotNull { offset ->
            indexedReads++
            try {
                decode(schema, RowReader.readAt(file, offset), 0)
            } catch (error: IOException) {
                report("${file.name} no se pudo leer en el byte $offset: ${error.message}")
                null
            }
        }
    }

    /**
     * Las entradas de un indice, leidas de su .idx la primera vez que se piden.
     *
     * Carga perezosa: el catalogo no abre ningun .idx, y uno se abre solo cuando
     * una consulta lo va a usar. Es el mismo *esquema ansioso, datos perezosos*
     * que ya rige para los .csv.
     */
    fun entriesOf(index: Index): java.util.TreeMap<Value, MutableList<Long>>? {
        indexCache[index.name]?.let { return it }

        val column = catalog.table(index.table)?.column(index.column) ?: return null
        val file = directory.index(index.name)
        if (!file.isFile) return null

        val entries = try {
            IndexFile.read(file, column.type)
        } catch (error: IOException) {
            report("${file.name} no se pudo leer: ${error.message}")
            return null
        }
        indexCache[index.name] = entries
        return entries
    }

    private val indexCache = mutableMapOf<String, java.util.TreeMap<Value, MutableList<Long>>>()

    fun applyCatalog(updated: Catalog) {
        catalog = updated
    }

    // Una tabla que nace en este script arranca vacia y en memoria.
    fun createTable(table: Table) {
        loaded[table.name] = mutableListOf()
        markModified(table.name)
    }

    fun forgetTable(name: String) {
        loaded.remove(name)
        modified.remove(name)
    }

    fun changes(): Changes {
        val dropped = originalCatalog.tables.keys.filterNot { it in catalog.tables }
        val schemas = catalog.tables.filterNot { (name, table) -> originalCatalog.table(name) === table }.keys

        return Changes(
            rows = modified.associateWith { rowsOf(it).toList() },
            catalog = catalog,
            schemas = schemas.toList(),
            dropped = dropped,
            droppedIndexes = droppedIndexes.toList()
        )
    }

    /**
     * El bucle del script. Vive aqui y no en el pipeline porque necesita la
     * sesion.
     *
     * Al primer error se corta, y como nada toco el disco todavia, el directorio
     * queda intacto.
     */
    fun run(script: Script): ExecutionResult {
        val results = mutableListOf<ResultSet>()

        for (statement in script.statements) {
            when (statement) {
                is Query -> results += Engine(this).execute(statement)
                else -> Writer(this).execute(statement)
            }
            if (diagnostics.hasErrors) break
        }
        return ExecutionResult(results, this)
    }

    private fun load(table: String): MutableList<Row> {
        val schema = catalog.table(table) ?: return mutableListOf()
        val file = directory.csv(table)
        if (!file.isFile) return mutableListOf()

        reads++
        val content = try {
            CsvReader.read(file)
        } catch (error: IOException) {
            report("${file.name} no se pudo leer: ${error.message}")
            return mutableListOf()
        }

        return content.rows.mapIndexed { number, fields -> decode(schema, fields, number) }.toMutableList()
    }

    // El esquema manda: el orden de las columnas del CSV ya lo verifico la guarda
    // de la fase 4.
    private fun decode(schema: Table, fields: List<String?>, number: Int): Row {
        val values = schema.columns.mapIndexed { position, column ->
            val text = fields.getOrNull(position)
            ValueCodec.decode(text, column.type) ?: fallback(schema, column.name, text, number)
        }
        return Row(values)
    }

    private fun fallback(schema: Table, column: String, text: String?, number: Int): Value {
        report("${schema.name}.csv fila ${number + 1}: '$text' no es un valor de ${schema.column(column)?.type?.name}")
        return NullValue
    }

    private fun report(message: String) {
        // Los errores de datos vienen de un archivo, no de una posicion del script.
        diagnostics.report(CompilerError.ExecutionError(LexemeLocation(1, 1), message))
    }
}

/**
 * Lo que el volcado de la fase 7 tiene que llevar al disco.
 *
 * Solo lo que cambio: una tabla que el script solo leyo no se reescribe.
 */
class Changes(
    val rows: Map<String, List<Row>>,
    val catalog: Catalog,
    val schemas: List<String>,
    val dropped: List<String>,
    val droppedIndexes: List<Index> = emptyList()
)

class ExecutionResult(
    val results: List<ResultSet>,
    val session: Session
)
