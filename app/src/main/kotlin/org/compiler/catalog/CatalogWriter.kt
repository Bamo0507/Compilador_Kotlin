package org.compiler.catalog

import org.compiler.storage.CsvWriter
import org.compiler.storage.DataDirectory
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

object CatalogWriter {
    fun flush(original: Catalog, final: Catalog, directory: DataDirectory) {
        final.tables.forEach { (name, table) ->
            val previous = original.table(name)
            if (previous != null && sameSchema(previous, table)) return@forEach

            val target = directory.json(name).toPath().toAbsolutePath()
            val temporary = Files.createTempFile(target.parent, ".$name.", ".json.tmp")
            try {
                Files.writeString(temporary, SchemaJson.encode(table), Charsets.UTF_8)
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }

            if (previous == null) {
                CsvWriter.write(directory.csv(name), table.columns.map { it.name }, emptyList())
            }
        }

        original.tables.keys.filter { it !in final.tables }.forEach { name ->
            Files.deleteIfExists(directory.json(name).toPath())
            Files.deleteIfExists(directory.csv(name).toPath())
        }
    }

    // Los modelos tienen identidad, pero el volcado compara el contenido.
    private fun sameSchema(left: Table, right: Table): Boolean =
        left.name == right.name && left.columns.size == right.columns.size &&
            left.columns.zip(right.columns).all { (a, b) ->
                a.name == b.name && a.type == b.type && a.constraints == b.constraints
            }
}
