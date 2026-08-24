package org.compiler.frontend.semantic

import org.compiler.diagnostics.CompilerError
import org.compiler.diagnostics.Diagnostics
import org.compiler.frontend.ast.models.ClassDeclaration
import org.compiler.frontend.ast.models.FunctionDeclaration
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.semantic.symbols.CONSTRUCTOR_NAME
import org.compiler.frontend.semantic.symbols.ClassType
import org.compiler.frontend.semantic.symbols.DeclarationKind
import org.compiler.frontend.semantic.symbols.ErrorType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.Scope
import org.compiler.frontend.semantic.symbols.ScopeKind
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.VoidType
import org.compiler.models.LexemeLocation

/**
 * Pasada 1: registra las declaraciones en el arbol de ambitos.
 *
 * NO entra a los cuerpos de las funciones ni a los bloques. Esa omision es lo que
 * habilita las referencias adelantadas: cuando la Pasada 2 llega a `b()`, `b` ya esta
 * en la tabla aunque se declare mas abajo.
 */
class DeclarationCollector(
    private val diagnostics: Diagnostics
) {
    // La SALIDA de esta fase: el pipeline lo lee y se lo pasa al TypeChecker.
    val globalScope = Scope(ScopeKind.GLOBAL, "global", parent = null)

    // El cursor del recorrido. Sube y baja por el arbol, no lo destruye.
    private var currentScope = globalScope

    private val typeResolver = TypeResolver(globalScope, diagnostics)

    /**
     * Dos rondas sobre el nivel superior:
     *   A. registrar los NOMBRES de las clases, sin sus miembros
     *   B. registrar todo lo demas, ya con esos nombres visibles
     *
     * Es lo que permite  class A { let b: B; }  seguido de  class B { }.
     */
    fun collect(program: Program) {
        registerClassNames(program.statements)
        program.statements.forEach { collect(it) }
    }

    private fun collect(stmt: Statement) {
        when (stmt) {
            is ClassDeclaration -> collectClassDeclaration(stmt)
            is FunctionDeclaration -> collectFunctionDeclaration(stmt)

            // Las variables y los bloques son trabajo de la Pasada 2: registrarlas aqui
            // las declararia dos veces, y las volveria referenciables hacia adelante.
            else -> Unit
        }
    }

    // Ronda A. Recorre solo el nivel superior: una clase dentro de un bloque no se
    // registra, y eso queda como limitacion documentada.
    private fun registerClassNames(statements: List<Statement>) {
        statements.filterIsInstance<ClassDeclaration>().forEach { decl ->
            // Se verifica ANTES de abrir el ambito: si se abriera primero y la
            // declaracion fallara, quedaria un Scope huerfano en globalScope.children.
            val previous = globalScope.lookupLocal(decl.name)
            if (previous != null) {
                diagnostics.reportAlreadyDeclared(decl.name, decl.location, previous)
                return@forEach
            }

            val classScope = globalScope.openChild(ScopeKind.CLASS, decl.name)

            globalScope.declareOrReport(
                Symbol(
                    name = decl.name,
                    kind = DeclarationKind.CLASS,
                    type = ClassType(decl.name),
                    location = decl.location,
                    scopeName = globalScope.name,
                    offset = 0,
                    memberScope = classScope
                ),
                diagnostics
            )
        }
    }

    // Ronda B para una clase. No crea el ambito: lo recupera del memberScope.
    private fun collectClassDeclaration(decl: ClassDeclaration) {
        val classSymbol = globalScope.lookupLocal(decl.name) ?: return

        // Si el simbolo registrado no es de ESTA declaracion, la clase esta repetida y
        // el error ya se reporto en la ronda A. Seguir declararia sus miembros en el
        // ambito de la OTRA clase.
        if (classSymbol.location != decl.location) return

        // memberScope es nullable porque la mayoria de los Symbol no tienen ambito, y
        // el tipo no puede expresar "no-nulo cuando kind == CLASS".
        val classScope = classSymbol.memberScope ?: return

        resolveSuperclass(decl, classScope)

        val previousScope = currentScope
        currentScope = classScope

        decl.members.forEach { member ->
            when (member) {
                is VariableDeclaration -> collectField(member)
                is FunctionDeclaration -> collectMethod(member)
                else -> Unit
            }
        }

        currentScope = previousScope
    }

    // Registra la FIRMA. Aqui NO se llama a collect(decl.body), y esa omision es lo que
    // habilita las referencias adelantadas.
    private fun collectFunctionDeclaration(decl: FunctionDeclaration) {
        val parameterTypes = decl.parameters.map { parameter ->
            typeResolver.resolve(parameter.declaredType) ?: ErrorType
        }
        val returnType = typeResolver.resolve(decl.returnType) ?: VoidType

        // Siempre FUNCTION, suelta o dentro de una clase. Que sea un metodo lo marca
        // isMember, y eso lo pone Scope.declare.
        currentScope.declareOrReport(
            Symbol(
                name = decl.name,
                kind = DeclarationKind.FUNCTION,
                type = FunctionType(parameterTypes, returnType),
                location = decl.location,
                scopeName = currentScope.name,
                offset = 0
            ),
            diagnostics
        )
    }

    private fun collectMethod(decl: FunctionDeclaration) {
        // La gramatica no puede imponer esto: el constructor es una `function` que se
        // llama asi, no tiene sintaxis propia.
        if (decl.name == CONSTRUCTOR_NAME && decl.returnType != null) {
            report(
                decl.location,
                "El constructor de '${currentScope.name}' no puede declarar tipo de retorno"
            )
        }

        collectFunctionDeclaration(decl)
    }

    // La unica variable que la Pasada 1 registra: un campo es parte de la interfaz de la
    // clase, y `perro.nombre` tiene que encontrarlo.
    private fun collectField(decl: VariableDeclaration) {
        // Un campo exige tipo anotado. No se puede inferir del inicializador porque esta
        // pasada no evalua expresiones: el inicializador podria llamar a un metodo de
        // una clase que todavia no se registro.
        val fieldType = typeResolver.resolve(decl.declaredType)

        if (fieldType == null) {
            report(decl.location, "El campo '${decl.name}' necesita un tipo anotado")
        }

        currentScope.declareOrReport(
            Symbol(
                name = decl.name,
                kind = if (decl.isConstant) DeclarationKind.CONSTANT else DeclarationKind.VARIABLE,
                type = fieldType ?: ErrorType,
                location = decl.location,
                scopeName = currentScope.name,
                offset = 0,
                initialized = decl.initializer != null
            ),
            diagnostics
        )
    }

    private fun resolveSuperclass(decl: ClassDeclaration, classScope: Scope) {
        val superName = decl.superclassName ?: return

        val superSymbol = globalScope.lookupLocal(superName)

        if (superSymbol == null || superSymbol.kind != DeclarationKind.CLASS) {
            report(
                decl.location,
                "La clase '${decl.name}' hereda de '$superName', que no es una clase declarada"
            )
            return
        }

        if (createsInheritanceCycle(decl.name, superName)) {
            report(
                decl.location,
                "Herencia circular: '${decl.name}' hereda de '$superName', " +
                    "que a su vez hereda de '${decl.name}'"
            )
            return
        }

        val superScope = superSymbol.memberScope ?: return

        // No se COPIA nada: se enlaza. Los miembros siguen viviendo en el ambito de la
        // superclase, y lookupMember camina la cadena para encontrarlos.
        classScope.attachSuperclass(superScope)
    }

    /**
     * Sube la cadena de superclases desde `superName` buscando volver a `className`.
     *
     * Es obligatorio detectarlo: sin esto, lookupMember entraria en recursion infinita y
     * el compilador colgaria en vez de dar un error.
     */
    private fun createsInheritanceCycle(className: String, superName: String): Boolean {
        var current: String? = superName

        while (current != null) {
            if (current == className) return true
            current = globalScope.lookupLocal(current)?.memberScope?.superclass?.name
        }

        return false
    }

    private fun report(location: LexemeLocation, message: String) {
        diagnostics.report(CompilerError.SemanticError(location, message))
    }
}
