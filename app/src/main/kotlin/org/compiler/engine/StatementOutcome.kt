package org.compiler.engine

import org.compiler.frontend.ast.models.AlterTableAdd
import org.compiler.frontend.ast.models.AlterTableDrop
import org.compiler.frontend.ast.models.CreateIndex
import org.compiler.frontend.ast.models.CreateTable
import org.compiler.frontend.ast.models.Delete
import org.compiler.frontend.ast.models.DropIndex
import org.compiler.frontend.ast.models.DropTable
import org.compiler.frontend.ast.models.Insert
import org.compiler.frontend.ast.models.Query
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Update

/**
 * Lo que dejo cada sentencia del script.
 *
 * Existe porque la interfaz necesita saber de QUE sentencia salio cada cosa: tres
 * SELECT dan tres rejillas numeradas, y un INSERT no da rejilla sino una linea que
 * dice cuantas filas agrego. Con una lista suelta de ResultSet eso no se puede
 * reconstruir.
 */
sealed interface StatementOutcome {

    // La posicion en el script, contando desde 1, como la lee quien lo escribio.
    val number: Int

    // Como se nombra la sentencia en la interfaz: `INSERT INTO users`.
    val label: String

    // Un SELECT: su rejilla.
    class Rows(
        override val number: Int,
        override val label: String,
        val result: ResultSet
    ) : StatementOutcome

    // Lo demas: una linea que dice que cambio.
    class Written(
        override val number: Int,
        override val label: String,
        val detail: String
    ) : StatementOutcome
}

/**
 * El nombre corto de una sentencia, con la tabla que toca.
 *
 * Es lo que se lee a la izquierda de cada linea de resumen, y lo que titula cada
 * rejilla. Se arma del AST y no del texto del script: el texto puede venir en
 * minusculas, partido en varias lineas o con comentarios en medio.
 */
fun labelOf(statement: Statement): String = when (statement) {
    is Query -> "SELECT"
    is Insert -> "INSERT INTO ${statement.table}"
    is Update -> "UPDATE ${statement.table}"
    is Delete -> "DELETE FROM ${statement.table}"
    is CreateTable -> "CREATE TABLE ${statement.name}"
    is AlterTableAdd -> "ALTER TABLE ${statement.table} ADD"
    is AlterTableDrop -> "ALTER TABLE ${statement.table} DROP COLUMN"
    is DropTable -> "DROP TABLE ${statement.table}"
    is CreateIndex -> "CREATE INDEX ${statement.name}"
    is DropIndex -> "DROP INDEX ${statement.name}"
}
