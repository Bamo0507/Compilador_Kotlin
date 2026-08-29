package org.compiler.frontend.ast

import org.compiler.frontend.ast.models.ArrayLiteral
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.AssignmentExpression
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.Block
import org.compiler.frontend.ast.models.Break
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.Continue
import org.compiler.frontend.ast.models.DoWhile
import org.compiler.frontend.ast.models.Expression
import org.compiler.frontend.ast.models.ExpressionStatement
import org.compiler.frontend.ast.models.For
import org.compiler.frontend.ast.models.ForEach
import org.compiler.frontend.ast.models.FunctionCall
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.Identifier
import org.compiler.frontend.ast.models.If
import org.compiler.frontend.ast.models.IndexAccess
import org.compiler.frontend.ast.models.Literal
import org.compiler.frontend.ast.models.ObjectCreation
import org.compiler.frontend.ast.models.Parameter
import org.compiler.frontend.ast.models.Print
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.PropertyAccess
import org.compiler.frontend.ast.models.Return
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.SwitchCase
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.ThisReference
import org.compiler.frontend.ast.models.TreeNodeView
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While

/**
 * Convierte el AST a la vista neutral que dibuja la GUI.
 *
 * En `detail` van el tipo y el valor constante que dejo el TypeChecker: es lo que
 * hace de esta pantalla la evidencia visible de la Fase 4.
 */
fun Program.toTreeView(): TreeNodeView =
    TreeNodeView("Program", detail = null, children = statements.map { it.toView() })

// ── La decoracion ──────────────────────────────────────────────────────────

// El tipo, y el valor si el plegado lo resolvio:  "integer = 8"
private fun Expression.decoration(): String? {
    val typeName = type?.name
    val constant = constantValue?.let { constantText(it) }

    return when {
        typeName != null && constant != null -> "$typeName = $constant"
        typeName != null -> typeName
        else -> constant
    }
}

// Las cadenas van entre comillas para que se distingan de un nombre.
private fun constantText(constant: Any): String =
    if (constant is String) "\"$constant\"" else constant.toString()

private fun node(label: String, vararg children: TreeNodeView?) =
    TreeNodeView(label, detail = null, children = children.filterNotNull())

private fun Expression.node(label: String, children: List<TreeNodeView>) =
    TreeNodeView(label, detail = decoration(), children = children)

// ── Expresiones ────────────────────────────────────────────────────────────

private fun Expression.toView(): TreeNodeView = when (this) {
    is Literal -> node("Literal ${value?.let { constantText(it) } ?: "null"}", emptyList())

    is Identifier -> node("Identifier $name", emptyList())

    is ThisReference -> node("this", emptyList())

    is ArrayLiteral -> node("ArrayLiteral", elements.map { it.toView() })

    is UnaryOperation -> node("UnaryOperation ${operator.symbol}", listOf(operand.toView()))

    is BinaryOperation ->
        node("BinaryOperation ${operator.symbol}", listOf(left.toView(), right.toView()))

    is TernaryOperation -> node(
        "TernaryOperation ?:",
        listOf(condition.toView(), ifTrue.toView(), ifFalse.toView())
    )

    is AssignmentExpression ->
        node("AssignmentExpression =", listOf(target.toView(), value.toView()))

    is FunctionCall ->
        node("FunctionCall", listOf(callee.toView()) + arguments.map { it.toView() })

    is IndexAccess -> node("IndexAccess []", listOf(target.toView(), index.toView()))

    is PropertyAccess -> node("PropertyAccess .$propertyName", listOf(target.toView()))

    is ObjectCreation -> node("ObjectCreation $className", arguments.map { it.toView() })
}

// ── Sentencias ─────────────────────────────────────────────────────────────

private fun Statement.toView(): TreeNodeView = when (this) {
    is VariableDeclaration -> TreeNodeView(
        label = "${if (isConstant) "const" else "let"} $name",
        detail = declaredType?.name,
        children = listOfNotNull(initializer?.toView())
    )

    is FunctionDeclaration -> TreeNodeView(
        label = "function $name",
        detail = returnType?.name ?: "void",
        children = parameters.map { it.toView() } + body.toView()
    )

    is ClassDeclaration -> node(
        "class $name" + (superclassName?.let { " : $it" } ?: ""),
        *members.map { it.toView() }.toTypedArray()
    )

    is Assignment -> node("Assignment =", target.toView(), value.toView())

    is ExpressionStatement -> node("ExpressionStatement", expr.toView())

    is Print -> node("print", expr.toView())

    is Block -> node("Block", *statements.map { it.toView() }.toTypedArray())

    is If -> node("if", condition.toView(), thenBranch.toView(), elseBranch?.toView())

    is While -> node("while", condition.toView(), body.toView())

    is DoWhile -> node("do while", body.toView(), condition.toView())

    is For -> node(
        "for",
        initializer?.toView(),
        condition?.toView(),
        update?.toView(),
        body.toView()
    )

    is ForEach -> node("foreach $variableName in", iterable.toView(), body.toView())

    is Switch -> node(
        "switch",
        subject.toView(),
        *cases.map { it.toView() }.toTypedArray(),
        defaultBody?.let { node("default", *it.map { stmt -> stmt.toView() }.toTypedArray()) }
    )

    is TryCatch -> node(
        "try catch ($catchParameterName)",
        tryBlock.toView(),
        catchBlock.toView()
    )

    is Return -> node("return", value?.toView())

    is Break -> node("break")

    is Continue -> node("continue")
}

private fun Parameter.toView(): TreeNodeView =
    TreeNodeView("param $name", detail = declaredType?.name, children = emptyList())

private fun SwitchCase.toView(): TreeNodeView =
    node("case", value.toView(), *body.map { it.toView() }.toTypedArray())
