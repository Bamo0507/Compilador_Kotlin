// Ticket 8.1: CREATE INDEX y DROP INDEX, del texto al AST.
package org.compiler

import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.SqlAstBuilder
import org.compiler.frontend.ast.models.CreateIndex
import org.compiler.frontend.ast.models.DropIndex
import org.compiler.frontend.ast.models.Script
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.syntax.SqlSyntaxAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SqlAstBuilderIndexTest {

    private fun statement(sql: String): Statement {
        val diagnostics = Diagnostics()
        val tree = assertNotNull(SqlSyntaxAnalyzer.parse(sql, diagnostics), "No parsea: $sql")
        val ast = SqlAstBuilder(diagnostics).visit(tree) as Script
        assertEquals(emptyList(), diagnostics.all(), "Reporto errores: $sql")
        return ast.statements.single()
    }

    // 'CREATE' 'INDEX' Identifier 'ON' Identifier: el primero es el indice y el
    // segundo la tabla. Confundirlos es el error facil de esta regla.
    @Test
    fun `CREATE INDEX guarda el nombre, la tabla y la columna`() {
        val index = assertIs<CreateIndex>(statement("CREATE INDEX idx_marca ON carros(marca);"))

        assertEquals("idx_marca", index.name)
        assertEquals("carros", index.table)
        assertEquals(listOf("marca"), index.columns)
    }

    /**
     * La gramatica acepta varias columnas aunque solo se soporte una.
     *
     * Si aceptara un solo Identifier, esto daria "se esperaba ')'", que no explica
     * nada. Parseando la lista, el mensaje lo da el semantico (ticket 8.5) y dice
     * de verdad cual es la regla.
     */
    @Test
    fun `la gramatica acepta varias columnas y el AST las guarda todas`() {
        val index = assertIs<CreateIndex>(statement("CREATE INDEX idx_dos ON carros(marca, anio);"))

        assertEquals(listOf("marca", "anio"), index.columns)
    }

    @Test
    fun `DROP INDEX guarda el nombre`() {
        assertEquals("idx_marca", assertIs<DropIndex>(statement("DROP INDEX idx_marca;")).name)
    }

    // Las palabras clave no distinguen mayusculas: lo resuelve UpperCaseCharStream.
    @Test
    fun `las dos sentencias parsean en minusculas`() {
        assertIs<CreateIndex>(statement("create index idx_a on carros(marca);"))
        assertIs<DropIndex>(statement("drop index idx_a;"))
    }

    // DROP INDEX y DROP TABLE empiezan igual: el parser los separa por la palabra
    // que sigue, no por el nombre.
    @Test
    fun `DROP INDEX no se confunde con DROP TABLE`() {
        assertIs<DropIndex>(statement("DROP INDEX carros;"))
        assertIs<org.compiler.frontend.ast.models.DropTable>(statement("DROP TABLE carros;"))
    }
}
