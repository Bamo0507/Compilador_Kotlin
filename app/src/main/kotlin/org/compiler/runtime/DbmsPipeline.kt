package org.compiler.runtime

import org.compiler.diagnostics.Diagnostics
import org.compiler.runtime.models.CompilationResult
import org.compiler.storage.DataDirectory

/**
 * Encadena las etapas del DBMS. Una llamada, un resultado.
 *
 *   A sintaxis    el script a arbol de ANTLR. La UNICA que corta
 *   B AST         el arbol de ANTLR al AST propio
 *   C catalogo    lee los .json del directorio, y ningun .csv
 *   D semantico   nombres y tipos. Corre aunque C haya reportado, para que el
 *                 usuario vea todos sus problemas de una vez
 *   E ejecucion   solo si no quedo ningun error. Abre los .csv de las tablas
 *                 que el script toca y opera en memoria
 *   F volcado     escribe lo que cambio, solo si E tampoco fallo
 */
object DbmsPipeline {

    fun run(
        source: String,
        directory: DataDirectory = DataDirectory.DEFAULT,
        write: Boolean = true
    ): CompilationResult {
        val diagnostics = Diagnostics()

        return CompilationResult(source = source, errors = diagnostics.all())
    }
}
