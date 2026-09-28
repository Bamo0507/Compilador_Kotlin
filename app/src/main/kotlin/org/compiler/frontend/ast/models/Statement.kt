package org.compiler.frontend.ast.models

import org.compiler.catalog.Constraint
import org.compiler.models.LexemeLocation
import org.compiler.types.Type

sealed interface Node {
    val location: LexemeLocation
}

// El nodo RAIZ: lo que devuelve el SqlAstBuilder y lo que recorre todo lo demas.
class Script(
    val statements: List<Statement>,
    override val location: LexemeLocation
) : Node

// Una sentencia. Sellada: un `when` sobre ella sin `else` no compila si falta una.
sealed interface Statement : Node

// DDL -------------------------------------------------------------------------

class CreateTable(
    val name: String,
    val columns: List<ColumnDefinition>,
    override val location: LexemeLocation
) : Statement

class AlterTableAdd(
    val table: String,
    val column: ColumnDefinition,
    override val location: LexemeLocation
) : Statement

class AlterTableDrop(
    val table: String,
    val column: String,
    override val location: LexemeLocation
) : Statement

class DropTable(
    val table: String,
    override val location: LexemeLocation
) : Statement

// DML -------------------------------------------------------------------------

class Insert(
    val table: String,

    // null si no se nombraron: los valores van en el orden del esquema. Es
    // distinto de una lista vacia, que la gramatica ni siquiera permite.
    val columns: List<String>?,

    // Una lista por fila: INSERT acepta varias en una sola sentencia.
    val rows: List<List<Expression>>,

    override val location: LexemeLocation
) : Statement

class Update(
    val table: String,
    val assignments: List<Assignment>,
    val where: Expression?,
    override val location: LexemeLocation
) : Statement

class Delete(
    val table: String,
    val where: Expression?,
    override val location: LexemeLocation
) : Statement

// Piezas de las sentencias ------------------------------------------------------

/**
 * Una columna del CREATE TABLE o del ALTER TABLE ADD.
 *
 * Guarda un Type ya RESUELTO y no texto: el tipo se escribe completo en la
 * sentencia y no hay nada que resolver despues. Es la diferencia con el
 * TypeReference de Compiscript, donde un nombre de clase podia apuntar a algo
 * declarado mas abajo.
 *
 * Las restricciones van tal como se escribieron, en orden y con repetidos: que no
 * se contradigan (NOT NULL con NULL) es una regla de la fase 5.
 */
class ColumnDefinition(
    val name: String,
    val type: Type,
    val constraints: List<Constraint>,
    override val location: LexemeLocation
) : Node

// `columna = valor` dentro de un UPDATE ... SET.
class Assignment(
    val column: String,
    val value: Expression,
    override val location: LexemeLocation
) : Node
