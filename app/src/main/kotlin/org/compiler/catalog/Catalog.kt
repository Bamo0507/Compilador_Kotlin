package org.compiler.catalog

import org.compiler.types.Type

class Catalog(tables: Map<String, Table>) {
    val tables: Map<String, Table> = tables.toMap()

    fun table(name: String): Table? = tables[name]

    // Los indices no viven en un mapa propio: cada tabla lleva los suyos, asi que
    // el catalogo solo los junta. Un DROP TABLE se los lleva sin dejar huerfanos.
    fun indexes(): List<Index> = tables.values.flatMap { it.indexes }

    fun index(name: String): Index? = indexes().firstOrNull { it.name == name }
}

class Table(
    val name: String,
    columns: List<Column>,
    indexes: List<Index> = emptyList()
) {
    // El orden coincide con el header del CSV y con INSERT sin columnas nombradas.
    val columns: List<Column> = columns.toList()

    /**
     * Los indices de la tabla: primero los que sus restricciones implican y
     * despues los que alguien creo a mano.
     *
     * Los automaticos NO se guardan, se DERIVAN. Asi una tabla con PRIMARY KEY
     * siempre tiene su indice, venga de un CREATE TABLE, de un .json escrito a
     * mano o de un Table armado en un test. La alternativa, guardarlos, deja
     * posible una tabla con clave primaria y sin indice, que es justo el estado
     * que no deberia existir.
     */
    val indexes: List<Index> = run {
        val automatic = automaticIndexesOf(name, this.columns)
        val names = automatic.map { it.name }.toSet()
        automatic + indexes.filterNot { it.name in names }
    }

    // El que puede responder un WHERE sobre esa columna. Si hay dos, el primero.
    fun indexOn(column: String): Index? = this.indexes.firstOrNull { it.column == column }

    fun column(name: String): Column? = columns.firstOrNull { it.name == name }

    fun indexOf(name: String): Int = columns.indexOfFirst { it.name == name }

    val primaryKey: Column? get() = columns.firstOrNull { it.hasConstraint<PrimaryKey>() }
}

class Column(
    val name: String,
    val type: Type,
    constraints: List<Constraint>
) {
    val constraints: List<Constraint> = constraints.toList()

    inline fun <reified R : Constraint> hasConstraint(): Boolean = constraints.any { it is R }

    // PRIMARY KEY implica NOT NULL aunque no se declare por separado.
    val nullable: Boolean get() = !hasConstraint<NotNull>() && !hasConstraint<PrimaryKey>()

    val reference: ForeignKey? get() = constraints.filterIsInstance<ForeignKey>().firstOrNull()
}
