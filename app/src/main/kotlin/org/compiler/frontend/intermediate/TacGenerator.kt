package org.compiler.frontend.intermediate

import java.util.IdentityHashMap
import org.compiler.frontend.ast.models.Assignment
import org.compiler.frontend.ast.models.BinaryOperation
import org.compiler.frontend.ast.models.BinaryOperator
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
import org.compiler.frontend.ast.models.OperatorGroup
import org.compiler.frontend.ast.models.Program
import org.compiler.frontend.ast.models.Statement
import org.compiler.frontend.ast.models.Switch
import org.compiler.frontend.ast.models.TernaryOperation
import org.compiler.frontend.ast.models.TryCatch
import org.compiler.frontend.ast.models.UnaryOperation
import org.compiler.frontend.ast.models.UnaryOperator
import org.compiler.frontend.ast.models.VariableDeclaration
import org.compiler.frontend.ast.models.While
import org.compiler.frontend.intermediate.DagNode.Subexpression
import org.compiler.frontend.intermediate.models.ActivationRecordField
import org.compiler.frontend.intermediate.models.ActivationRecordLayout
import org.compiler.frontend.intermediate.models.Address
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.Call
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Concat
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionBegin
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.IfFalseGoto
import org.compiler.frontend.intermediate.models.IfGoto
import org.compiler.frontend.intermediate.models.IfRelationalGoto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.Name
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Param
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Relational
import org.compiler.frontend.intermediate.models.RelationalOperator
import org.compiler.frontend.intermediate.models.Return
import org.compiler.frontend.intermediate.models.TacProgram
import org.compiler.frontend.intermediate.models.TacUnaryOperator
import org.compiler.frontend.intermediate.models.TryBegin
import org.compiler.frontend.intermediate.models.TryEnd
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.Throw
import org.compiler.frontend.intermediate.models.Unary
import org.compiler.frontend.semantic.symbols.BooleanType
import org.compiler.frontend.semantic.symbols.FloatType
import org.compiler.frontend.semantic.symbols.FunctionType
import org.compiler.frontend.semantic.symbols.IntegerType
import org.compiler.frontend.semantic.symbols.StorageLocation
import org.compiler.frontend.semantic.symbols.StringType
import org.compiler.frontend.semantic.symbols.Symbol
import org.compiler.frontend.semantic.symbols.Type
import org.compiler.frontend.ast.models.Print as PrintStatement
import org.compiler.frontend.ast.models.Return as ReturnStatement

/**
 * Etapa G: el AST ya validado a codigo de tres direcciones.
 *
 * Una funcion por construccion, igual que el TypeChecker y el Interpreter. Las
 * sentencias no devuelven nada; las expresiones devuelven la Address donde quedo su
 * valor. Toda expresion pasa por su GDA, y todo temporal por el pool.
 *
 * Lo que el generador todavia no traduce es un TODO que nombra la construccion. El
 * pipeline atrapa el NotImplementedError y deja el TAC en null, asi un programa con
 * un `while` no tumba el IDE.
 */
class TacGenerator(private val storageLayout: StorageLayout) {

    // Lo que se va emitiendo de la funcion actual, y su pool. Cada funcion arranca con
    // los suyos: el t1 de una funcion vive en su registro y no choca con el de otra.
    private var instructions = mutableListOf<Quadruple>()
    private var temporaries = TemporaryAllocator()

    // Las etiquetas son unicas en todo el programa, no por funcion.
    private var nextLabel = 1

    // El TAC completo, funcion tras funcion, y sus registros finales.
    private val output = mutableListOf<Quadruple>()
    private val activationRecords = mutableListOf<ActivationRecordLayout>()
    private var totalTemporaries = 0

    // Las funciones que faltan por generar. Una funcion no se genera donde se declara,
    // sino despues de la que la contiene: su codigo no puede quedar en medio del otro.
    private val pendingFunctions = ArrayDeque<FunctionDeclaration>()

    // La etiqueta de cada funcion, por su Symbol. Se arma antes de generar, porque una
    // llamada puede aparecer antes que la declaracion.
    private val functionLabels = IdentityHashMap<Symbol, FunctionLabel>()

    // Cuantas funciones contienen a cada funcion, contandose a si misma: 1 para una del
    // nivel superior. Es lo que decide los saltos del enlace de acceso en una llamada.
    private val functionDepths = IdentityHashMap<Symbol, Int>()

    // La profundidad de la funcion que se esta generando: 0 en el main.
    private var currentDepth = 0

    // A donde saltan break y continue dentro de un bucle, y cuantos try habia abiertos
    // al entrar: la diferencia con los de ahora es cuantos endtry hay que emitir.
    private data class LoopLabels(
        val breakLabel: Label,
        val continueLabel: Label,
        val openTriesAtEntry: Int
    )

    // Los bucles abiertos, el mas interno al final: break y continue van al de arriba.
    private val loops = ArrayDeque<LoopLabels>()

    // Cuantos try hay abiertos en este momento. Un break o continue que abandona un try
    // tiene que quitar su manejador antes de saltar.
    private var openTries = 0

    // El tipo que devuelve la funcion actual: un return entero en una funcion float se
    // convierte. Null en el main, que no devuelve nada.
    private var currentReturnType: Type? = null

    fun generate(program: Program): TacProgram {
        collectFunctionLabels(program.statements)

        // El codigo del nivel superior es el main implicito. Las funciones que declara
        // quedan pendientes, y cada una agrega las suyas al generarse.
        generateFunction(storageLayout.main, program.statements, returnType = null, depth = 0)
        while (pendingFunctions.isNotEmpty()) {
            val declaration = pendingFunctions.removeFirst()
            val scope = requireNotNull(declaration.scope) {
                "'${declaration.name}' sin ambito: ¿corrio el TypeChecker?"
            }
            val returnType = (declaration.symbol?.type as? FunctionType)?.returns
            generateFunction(
                storageLayout.functions.getValue(scope),
                declaration.body.statements,
                returnType,
                scope.functionDepth()
            )
        }

        return TacProgram(output.toList(), totalTemporaries, activationRecords.toList())
    }

    //  begin_func f, 24
    //      <cuerpo>
    //  end_func f
    //
    // El tamano de begin_func es el registro que armo el StorageAllocator mas el
    // espacio de los temporales, que recien aqui se sabe cuantos son.
    private fun generateFunction(
        record: ActivationRecordLayout,
        body: List<Statement>,
        returnType: Type?,
        depth: Int
    ) {
        instructions = mutableListOf()
        temporaries = TemporaryAllocator()
        loops.clear()
        openTries = 0
        currentReturnType = returnType
        currentDepth = depth

        body.forEach { statement ->
            generateStatement(statement)

            // El invariante del pool: entre sentencias del cuerpo no queda ningun
            // temporal vivo. Si queda uno, el generador perdio una lectura y el TAC
            // estaria mal: es un bug del compilador, y es mejor que salte aqui que en el
            // assembler. Solo en este nivel, porque dentro de un `switch` el sujeto
            // sigue vivo a proposito mientras corren los cuerpos de los case.
            check(!temporaries.hasLiveTemporaries) {
                "Quedaron temporales vivos al terminar la sentencia de la linea " +
                    "${statement.location.line}"
            }
        }

        val finalRecord = withTemporaries(record, temporaries.temporaryCount)
        output += FunctionBegin(record.function, finalRecord.size)
        output += instructions
        output += FunctionEnd(record.function)

        activationRecords += finalRecord
        totalTemporaries += temporaries.temporaryCount
    }

    // Los temporales van al final del registro, 8 bytes cada uno. El registro ya mide un
    // multiplo de 8, asi que no hace falta relleno.
    private fun withTemporaries(record: ActivationRecordLayout, count: Int): ActivationRecordLayout {
        val size = StorageAllocator.TEMPORARY_SIZE
        val temporaryFields = (1..count).map { index ->
            ActivationRecordField("t$index", record.size + (index - 1) * size, size)
        }
        return record.copy(fields = record.fields + temporaryFields, size = record.size + count * size)
    }

    // Recorre todo el programa buscando declaraciones de funciones, incluidas las
    // anidadas en cuerpos, bloques y bucles.
    private fun collectFunctionLabels(statements: List<Statement>) {
        statements.forEach { statement ->
            when (statement) {
                is FunctionDeclaration -> {
                    val scope = statement.scope
                    val symbol = statement.symbol
                    if (scope != null && symbol != null) {
                        functionLabels[symbol] = storageLayout.functions.getValue(scope).function
                        functionDepths[symbol] = scope.functionDepth()
                    }
                    collectFunctionLabels(statement.body.statements)
                }
                is Block -> collectFunctionLabels(statement.statements)
                is If -> {
                    collectFunctionLabels(statement.thenBranch.statements)
                    statement.elseBranch?.let { collectFunctionLabels(it.statements) }
                }
                is While -> collectFunctionLabels(statement.body.statements)
                is DoWhile -> collectFunctionLabels(statement.body.statements)
                is For -> collectFunctionLabels(statement.body.statements)
                is ForEach -> collectFunctionLabels(statement.body.statements)
                is TryCatch -> {
                    collectFunctionLabels(statement.tryBlock.statements)
                    collectFunctionLabels(statement.catchBlock.statements)
                }
                is Switch -> {
                    statement.cases.forEach { collectFunctionLabels(it.body) }
                    statement.defaultBody?.let { collectFunctionLabels(it) }
                }
                else -> Unit
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sentencias
    // ══════════════════════════════════════════════════════════════════════

    private fun generateStatement(stmt: Statement) {
        when (stmt) {
            is VariableDeclaration -> generateVariableDeclaration(stmt)
            is Assignment -> generateAssignment(stmt)
            is PrintStatement -> generatePrint(stmt)

            // `f();` o `x + 1;`: el valor se calcula y se descarta.
            // Una llamada como sentencia descarta su valor: `call f, n` sin resultado.
            is ExpressionStatement -> {
                val call = stmt.expr as? FunctionCall
                if (call != null) {
                    generateCall(call, wantsResult = false)
                } else {
                    temporaries.consume(generateExpression(stmt.expr))
                }
            }

            // El bloque no genera nada propio: las variables ya son Name con su
            // Symbol, asi que dos `x` de bloques distintos ya son direcciones distintas.
            is Block -> generateBlock(stmt)
            is If -> generateIf(stmt)
            is While -> generateWhile(stmt)
            is DoWhile -> generateDoWhile(stmt)
            is For -> generateFor(stmt)
            is Break -> jumpOutOfLoop { it.breakLabel }
            is Continue -> jumpOutOfLoop { it.continueLabel }

            is Switch -> generateSwitch(stmt)
            is ForEach -> TODO("foreach")
            is TryCatch -> generateTryCatch(stmt)
            // La declaracion no genera codigo aqui: queda pendiente y se emite aparte.
            is FunctionDeclaration -> pendingFunctions.addLast(stmt)
            is ReturnStatement -> generateReturn(stmt)
            is ClassDeclaration -> TODO("clases y objetos")
        }
    }

    // `let x = e;` -> el codigo de e, y despues `x = <direccion de e>`. La copia final
    // se conserva aunque cueste una instruccion: es la forma de la
    // diapositiva 24, y escribir directo en x es una optimizacion que la teoria no
    // muestra.
    //
    // `let x: integer;` -> `x = 0`: el cero de su tipo, el mismo que usa el interprete.
    private fun generateVariableDeclaration(decl: VariableDeclaration) {
        val symbol = requireNotNull(decl.symbol) {
            "'${decl.name}' sin Symbol: ¿corrio el TypeChecker?"
        }
        val target = nameOf(symbol)

        val initializer = decl.initializer
        if (initializer == null) {
            instructions += Copy(target, Constant(defaultValueOf(symbol.type)))
            return
        }

        copyInto(target, generateExpression(initializer))
    }

    private fun generateAssignment(stmt: Assignment) {
        val target = stmt.target as? Identifier
            ?: TODO("asignacion a campos y elementos de lista")

        copyInto(nameOf(symbolOf(target)), generateExpression(stmt.value))
    }

    private fun generatePrint(stmt: PrintStatement) {
        val value = generateExpression(stmt.expr)
        instructions += Print(value, ExpressionDag.kindOf(stmt.expr.type))
        temporaries.consume(value)
    }

    private fun generateBlock(block: Block) {
        block.statements.forEach { generateStatement(it) }
    }

    //  if (B) S                 if (B) S1 else S2
    //      <B: cae, L1>             <B: cae, L1>
    //      S                        S1
    //  L1:                          goto L2
    //                           L1: S2
    //                           L2:
    //
    // La condicion cae al cuerpo y solo salta cuando es falsa.
    private fun generateIf(stmt: If) {
        val elseBranch = stmt.elseBranch
        val falseLabel = newLabel()

        generateCondition(stmt.condition, null, falseLabel)
        generateBlock(stmt.thenBranch)

        if (elseBranch == null) {
            instructions += LabelDefinition(falseLabel)
            return
        }

        val endLabel = newLabel()
        instructions += Goto(endLabel)
        instructions += LabelDefinition(falseLabel)
        generateBlock(elseBranch)
        instructions += LabelDefinition(endLabel)
    }

    //  while (B) S
    //  L1: <B: cae, L2>
    //      S
    //      goto L1
    //  L2:
    private fun generateWhile(stmt: While) {
        val startLabel = newLabel()
        val endLabel = newLabel()

        instructions += LabelDefinition(startLabel)
        generateCondition(stmt.condition, null, endLabel)
        withinLoop(LoopLabels(breakLabel = endLabel, continueLabel = startLabel, openTries)) {
            generateBlock(stmt.body)
        }
        instructions += Goto(startLabel)
        instructions += LabelDefinition(endLabel)
    }

    //  do S while (B);
    //  L1: S
    //  L2: <B: L1, cae>
    //  L3:
    //
    // La condicion verdadera es la vuelta del bucle: no hace falta un goto propio. Y
    // continue va a L2, a reevaluar la condicion, no a repetir el cuerpo.
    private fun generateDoWhile(stmt: DoWhile) {
        val bodyLabel = newLabel()
        val conditionLabel = newLabel()
        val endLabel = newLabel()

        instructions += LabelDefinition(bodyLabel)
        withinLoop(LoopLabels(breakLabel = endLabel, continueLabel = conditionLabel, openTries)) {
            generateBlock(stmt.body)
        }
        instructions += LabelDefinition(conditionLabel)
        generateCondition(stmt.condition, bodyLabel, null)
        instructions += LabelDefinition(endLabel)
    }

    //  for (init; B; update) S
    //      init
    //  L1: <B: cae, L3>
    //      S
    //  L2: update
    //      goto L1
    //  L3:
    //
    // continue va a L2: la actualizacion corre tambien despues de un continue. Sin
    // condicion no se compara nada, y el bucle solo sale con break.
    private fun generateFor(stmt: For) {
        stmt.initializer?.let { generateStatement(it) }

        val startLabel = newLabel()
        val updateLabel = newLabel()
        val endLabel = newLabel()

        instructions += LabelDefinition(startLabel)
        stmt.condition?.let { generateCondition(it, null, endLabel) }
        withinLoop(LoopLabels(breakLabel = endLabel, continueLabel = updateLabel, openTries)) {
            generateBlock(stmt.body)
        }
        instructions += LabelDefinition(updateLabel)
        stmt.update?.let { temporaries.consume(generateExpression(it)) }
        instructions += Goto(startLabel)
        instructions += LabelDefinition(endLabel)
    }

    //      t1 = <sujeto>
    //      if t1 != 1 goto L2
    //      <cuerpo del case 1>
    //      goto L1
    //  L2: if t1 != 2 goto L3
    //      <cuerpo del case 2>
    //      goto L1
    //  L3: <default>
    //  L1:
    //
    // Una cadena de comparaciones y no una tabla de saltos: un case puede ser un string
    // o una variable. Sin fall-through, cada case termina saltando al final.
    private fun generateSwitch(stmt: Switch) {
        val endLabel = newLabel()
        val subject = generateSubject(stmt)

        stmt.cases.forEach { case ->
            val nextLabel = newLabel()
            val kind = ExpressionDag.comparisonKind(stmt.subject, case.value)
            val left = convertAddress(subject, stmt.subject.type, kind)
            val right = convertedTo(kind, case.value)
            emitComparisonJump(left, RelationalOperator.EQUAL, right, kind, null, nextLabel)

            case.body.forEach { generateStatement(it) }
            instructions += Goto(endLabel)
            instructions += LabelDefinition(nextLabel)
        }

        stmt.defaultBody?.forEach { generateStatement(it) }
        instructions += LabelDefinition(endLabel)
    }

    // El sujeto se evalua UNA vez y se lee una vez por case, asi que su temporal vive
    // todo el switch. Si es una variable y algun case puede modificarla, como una
    // llamada, se copia antes: el switch compara contra el valor de cuando empezo.
    private fun generateSubject(stmt: Switch): Address {
        val readers = stmt.cases.size
        if (readers == 0) {
            temporaries.consume(generateExpression(stmt.subject))
            return Constant(null)
        }

        val subject = generateExpression(stmt.subject, readers)
        val casesArePure = stmt.cases.all { ExpressionDag.isPure(it.value) }
        if (subject !is Name || casesArePure) return subject

        val snapshot = temporaries.newTemp(readers)
        instructions += Copy(snapshot, subject)
        return snapshot
    }

    // El switch no apila nada: no es un bucle, y un break dentro de el sale del bucle
    // que lo contiene.
    private inline fun withinLoop(labels: LoopLabels, body: () -> Unit) {
        loops.addLast(labels)
        body()
        loops.removeLast()
    }

    // break y continue: si abandonan un try, primero quitan su manejador, uno por cada
    // try abierto desde que se entro al bucle. Sin eso, un error posterior, ya fuera del
    // bucle, saltaria a un catch que no le corresponde.
    private fun jumpOutOfLoop(target: (LoopLabels) -> Label) {
        // El FlowAnalyzer ya garantiza que todo break y continue esta dentro de un bucle.
        val loop = checkNotNull(loops.lastOrNull()) { "break o continue fuera de un bucle" }

        repeat(openTries - loop.openTriesAtEntry) { instructions += TryEnd }
        instructions += Goto(target(loop))
    }

    //      try L1, e
    //      <cuerpo del try>
    //      endtry
    //      goto L2
    //  L1: <cuerpo del catch>
    //  L2:
    //
    // try registra el manejador: si algo falla, el mensaje va a e y se salta a L1. El
    // catch no cuenta como try abierto: el throw que llevo ahi ya quito el manejador.
    private fun generateTryCatch(stmt: TryCatch) {
        val catchSymbol = requireNotNull(stmt.catchSymbol) {
            "'${stmt.catchParameterName}' sin Symbol: ¿corrio el TypeChecker?"
        }
        val handlerLabel = newLabel()
        val endLabel = newLabel()

        instructions += TryBegin(handlerLabel, nameOf(catchSymbol))
        openTries += 1
        generateBlock(stmt.tryBlock)
        openTries -= 1
        instructions += TryEnd
        instructions += Goto(endLabel)

        instructions += LabelDefinition(handlerLabel)
        generateBlock(stmt.catchBlock)
        instructions += LabelDefinition(endLabel)
    }

    private fun copyInto(target: Address, value: Address) {
        instructions += Copy(target, value)
        temporaries.consume(value)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Expresiones: a traves del GDA
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Construye el GDA de la expresion y lo emite en postorden desde la raiz.
     *
     * Cada nodo Operation se emite UNA vez y se recuerda su direccion: la segunda vez
     * que un padre lo pide, recibe el mismo temporal sin recalcular. Cada lectura de un
     * hijo consume el temporal, y el de cada nodo se pide con tantos usos como padres
     * tiene. La raiz tiene un lector mas: la sentencia que la contiene.
     */
    // `readers` es cuantas veces se va a leer el resultado: casi siempre una, salvo el
    // sujeto de un switch, que se compara una vez por case.
    private fun generateExpression(expr: Expression, readers: Int = 1): Address {
        val dag = ExpressionDag.build(expr, ::nameOf)
        val emitted = HashMap<Int, Address>()

        fun usesOf(node: Int): Int = dag.parentCount[node] + if (node == dag.root) readers else 0

        fun emit(node: Int): Address {
            emitted[node]?.let { return it }

            val address = when (val dagNode = dag.nodes[node]) {
                is DagNode.Leaf -> dagNode.address

                is DagNode.Operation -> {
                    val operands = readOperands(dagNode.operands, dag, ::emit)

                    val operation = dagNode.operation
                    if (needsZeroCheck(operation, operands)) {
                        emitZeroCheck(operands[1], dag.lineOf(node))
                    }

                    // Primero se consumen los operandos y DESPUES se pide el resultado:
                    // asi `t1 = t1 + d` puede reutilizar el nombre que acaba de liberar.
                    operands.forEach { temporaries.consume(it) }
                    val result = temporaries.newTemp(usesOf(node))
                    instructions += quadrupleOf(operation, result, operands)
                    result
                }

                // `x = (y = 5)`: emite `y = 5`, y el valor de la asignacion es lo que
                // quedo en y. El padre lee Name(y), no un temporal.
                is DagNode.Assign -> {
                    copyInto(dagNode.target, emit(dagNode.value))
                    dagNode.target
                }

                is Subexpression -> generateSubexpression(dagNode)
            }

            emitted[node] = address
            return address
        }

        return emit(dag.root)
    }

    /**
     * Los operandos de una operacion, de izquierda a derecha.
     *
     * Con una asignacion anidada hay un caso fino: en `x + (x = 5)` la x de la
     * izquierda vale lo que valia ANTES de la asignacion, porque se evalua primero.
     * Pero una hoja no genera instruccion: se lee recien cuando se emite la suma, y
     * para entonces la asignacion ya corrio. Por eso, si el operando derecho puede
     * modificar la variable de la izquierda (una asignacion o una subexpresion, como
     * una llamada), la izquierda se copia antes a un temporal.
     */
    private fun readOperands(
        operands: List<Int>,
        dag: ExpressionDag,
        emit: (Int) -> Address
    ): List<Address> {
        val addresses = mutableListOf<Address>()
        operands.forEachIndexed { position, operand ->
            val address = emit(operand)

            val later = operands.drop(position + 1)
            if (address is Name && later.any { mayModify(dag, it, address.symbol) }) {
                val snapshot = temporaries.newTemp(uses = 1)
                instructions += Copy(snapshot, address)
                addresses += snapshot
            } else {
                addresses += address
            }
        }
        return addresses
    }

    // Si este nodo puede cambiar la variable. Una subexpresion se considera que si: una
    // llamada puede modificar una global, y un ternario puede tener una asignacion en
    // una rama. Revisar su contenido seria analisis de efectos.
    private fun mayModify(dag: ExpressionDag, node: Int, symbol: Symbol): Boolean =
        when (val dagNode = dag.nodes[node]) {
            is DagNode.Assign ->
                dagNode.target.symbol === symbol || mayModify(dag, dagNode.value, symbol)
            is DagNode.Operation -> dagNode.operands.any { mayModify(dag, it, symbol) }
            is Subexpression -> true
            is DagNode.Leaf -> false
        }

    private fun quadrupleOf(
        operation: DagOperation,
        result: Temporary,
        operands: List<Address>
    ): Quadruple =
        when (operation) {
            is DagOperation.Arithmetic ->
                Arithmetic(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Relational ->
                Relational(result, operands[0], operation.operator, operands[1], operation.kind)
            is DagOperation.Unary -> Unary(result, operation.operator, operands[0], operation.kind)
            DagOperation.Concat -> Concat(result, operands[0], operands[1])
        }

    // ══════════════════════════════════════════════════════════════════════
    //  Subexpresiones: lo que el GDA no descompone
    // ══════════════════════════════════════════════════════════════════════

    // Cada una se traduce con su propia funcion, y devuelve la direccion donde quedo su
    // valor. Como nunca se comparte, su resultado tiene un solo lector.
    private fun generateSubexpression(subexpression: Subexpression): Address =
        when (subexpression) {
            is Subexpression.Ternary -> generateTernary(subexpression.expression)
            is Subexpression.LogicalValue -> generateLogicalValue(subexpression.expression)
            is Subexpression.Call -> checkNotNull(generateCall(subexpression.expression, true))
            is Subexpression.NewObject -> TODO("creacion de objetos")
            is Subexpression.NewList -> TODO("creacion de listas")
            is Subexpression.FieldAccess -> TODO("acceso a campos")
            is Subexpression.ElementAccess -> TODO("acceso a elementos de lista")
        }

    //      param a1
    //      ...
    //      param an
    //      t = call f, n
    //
    // Primero se calculan TODOS los argumentos y despues van los param: asi, en f(g(x)),
    // los param de g no quedan intercalados con los de f. Cada argumento se convierte al
    // tipo de su parametro, y una variable se copia antes si un argumento posterior
    // podria modificarla.
    private fun generateCall(call: FunctionCall, wantsResult: Boolean): Address? {
        val callee = call.callee as? Identifier ?: TODO("llamadas a metodos")
        val function = symbolOf(callee)
        val label = checkNotNull(functionLabels[function]) { "'${callee.name}' sin etiqueta" }
        val parameterTypes = (function.type as FunctionType).parameters

        val arguments = call.arguments.mapIndexed { index, argument ->
            val kind = ExpressionDag.kindOf(parameterTypes[index])
            val address = convertedTo(kind, argument)
            val later = call.arguments.drop(index + 1)
            if (later.all { ExpressionDag.isPure(it) }) address else snapshotIfName(address)
        }

        arguments.forEach { argument ->
            instructions += Param(argument)
            temporaries.consume(argument)
        }

        val accessHops = accessHopsTo(function)
        if (!wantsResult) {
            instructions += Call(null, label, arguments.size, accessHops)
            return null
        }
        val result = temporaries.newTemp(uses = 1)
        instructions += Call(result, label, arguments.size, accessHops)
        return result
    }

    // Una variable vista desde la funcion actual. Si vive en el registro de otra
    // funcion que la contiene, lleva cuantos enlaces de acceso hay que subir. Las
    // globales viven en datos estaticos y no suben nada.
    private fun nameOf(symbol: Symbol): Name {
        if (symbol.storage !is StorageLocation.Frame) return Name(symbol)
        return Name(symbol, hops = currentDepth - symbol.declarationFunctionDepth)
    }

    // Quien llama le pasa a la funcion el registro de la que la contiene. Si la llamada
    // es desde esa misma funcion, son 0 saltos; si es desde una hermana o desde si
    // misma, 1. Una funcion del nivel superior no usa el enlace.
    private fun accessHopsTo(function: Symbol): Int? {
        val calleeDepth = checkNotNull(functionDepths[function])
        if (calleeDepth == 1) return null
        return currentDepth - calleeDepth + 1
    }

    // return x: si sale de un try, primero quita su manejador, uno por cada try abierto
    // en esta funcion.
    private fun generateReturn(stmt: ReturnStatement) {
        val returnType = currentReturnType
        val value = stmt.value?.let { expression ->
            convertedTo(ExpressionDag.kindOf(returnType), expression)
        }

        repeat(openTries) { instructions += TryEnd }
        instructions += Return(value)
        value?.let { temporaries.consume(it) }
    }

    // c ? x : y. El temporal del resultado se pide ANTES de los saltos, para que los
    // temporales de la condicion y de las ramas queden por encima, y cada rama copia su
    // valor en el. Si el ternario es float, una rama entera se convierte.
    private fun generateTernary(expression: TernaryOperation): Address {
        val result = temporaries.newTemp(uses = 1)
        val elseLabel = newLabel()
        val endLabel = newLabel()
        val kind = ExpressionDag.kindOf(expression.type)

        generateCondition(expression.condition, null, elseLabel)
        copyInto(result, convertedTo(kind, expression.ifTrue))
        instructions += Goto(endLabel)

        instructions += LabelDefinition(elseLabel)
        copyInto(result, convertedTo(kind, expression.ifFalse))
        instructions += LabelDefinition(endLabel)

        return result
    }

    // `let b = x < y && z`: && y || solo existen como saltos, asi que se generan los
    // saltos y despues se escribe el valor.
    private fun generateLogicalValue(expression: BinaryOperation): Address {
        val result = temporaries.newTemp(uses = 1)
        val falseLabel = newLabel()
        val endLabel = newLabel()

        generateCondition(expression, null, falseLabel)
        instructions += Copy(result, Constant(true))
        instructions += Goto(endLabel)

        instructions += LabelDefinition(falseLabel)
        instructions += Copy(result, Constant(false))
        instructions += LabelDefinition(endLabel)

        return result
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Condiciones: codigo de saltos
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Genera saltos a trueLabel si la condicion es verdadera y a falseLabel si es
     * falsa. Una etiqueta null significa caer: seguir con la instruccion siguiente.
     *
     * Con caida, cada condicion solo salta hacia el lado que no sigue, invirtiendo la
     * relacion si hace falta: `if a >= b goto Lfin` en vez de `if a < b goto L1` mas un
     * `goto Lfin`.
     */
    private fun generateCondition(condition: Expression, trueLabel: Label?, falseLabel: Label?) {
        // Plegada por el TypeChecker: `while (true)` no compara nada.
        val constant = condition.constantValue
        if (constant is Boolean) {
            jumpTo(if (constant) trueLabel else falseLabel)
            return
        }

        when {
            // !B no genera nada propio: es B con las etiquetas intercambiadas.
            condition is UnaryOperation && condition.operator == UnaryOperator.NOT ->
                generateCondition(condition.operand, falseLabel, trueLabel)

            condition is BinaryOperation && condition.operator == BinaryOperator.AND ->
                generateAnd(condition, trueLabel, falseLabel)

            condition is BinaryOperation && condition.operator == BinaryOperator.OR ->
                generateOr(condition, trueLabel, falseLabel)

            condition is BinaryOperation && isComparison(condition.operator) ->
                generateComparison(condition, trueLabel, falseLabel)

            else -> generateBooleanTest(condition, trueLabel, falseLabel)
        }
    }

    // Si B1 es falsa, B2 ni se evalua: salta directo al lado falso. Si el lado falso
    // cae, hace falta una etiqueta propia para saltarse B2.
    private fun generateAnd(condition: BinaryOperation, trueLabel: Label?, falseLabel: Label?) {
        val falseTarget = falseLabel ?: newLabel()
        generateCondition(condition.left, null, falseTarget)
        generateCondition(condition.right, trueLabel, falseLabel)
        if (falseLabel == null) instructions += LabelDefinition(falseTarget)
    }

    // Si B1 es verdadera, B2 ni se evalua: salta directo al lado verdadero.
    private fun generateOr(condition: BinaryOperation, trueLabel: Label?, falseLabel: Label?) {
        val trueTarget = trueLabel ?: newLabel()
        generateCondition(condition.left, trueTarget, null)
        generateCondition(condition.right, trueLabel, falseLabel)
        if (trueLabel == null) instructions += LabelDefinition(trueTarget)
    }

    private fun generateComparison(
        condition: BinaryOperation,
        trueLabel: Label?,
        falseLabel: Label?
    ) {
        val kind = ExpressionDag.comparisonKind(condition.left, condition.right)
        val left = readBefore(convertedTo(kind, condition.left), condition.right)
        val right = convertedTo(kind, condition.right)
        val operator = ExpressionDag.relationalOf(condition.operator)

        emitComparisonJump(left, operator, right, kind, trueLabel, falseLabel)
    }

    // El salto de una comparacion con sus dos operandos ya calculados. Con caida, si el
    // lado verdadero sigue de largo se salta al falso con la relacion invertida.
    private fun emitComparisonJump(
        left: Address,
        operator: RelationalOperator,
        right: Address,
        kind: OperandKind,
        trueLabel: Label?,
        falseLabel: Label?
    ) {
        when {
            trueLabel != null -> {
                instructions += IfRelationalGoto(left, operator, right, kind, trueLabel)
                jumpTo(falseLabel)
            }
            falseLabel != null ->
                instructions += IfRelationalGoto(left, operator.inverted, right, kind, falseLabel)
        }

        temporaries.consume(left)
        temporaries.consume(right)
    }

    // Una variable, una llamada o cualquier otro valor booleano: `if (bandera)`.
    private fun generateBooleanTest(condition: Expression, trueLabel: Label?, falseLabel: Label?) {
        val value = generateExpression(condition)

        when {
            trueLabel != null -> {
                instructions += IfGoto(value, trueLabel)
                jumpTo(falseLabel)
            }
            falseLabel != null -> instructions += IfFalseGoto(value, falseLabel)
        }

        temporaries.consume(value)
    }

    private fun jumpTo(label: Label?) {
        if (label != null) instructions += Goto(label)
    }

    private fun isComparison(operator: BinaryOperator): Boolean =
        operator.group == OperatorGroup.RELATIONAL || operator.group == OperatorGroup.EQUALITY

    // Un operando de una comparacion, convertido a flotante si el otro lo es. Una
    // constante entera se convierte aqui mismo, sin instruccion.
    private fun convertedTo(kind: OperandKind, operand: Expression): Address =
        convertAddress(generateExpression(operand), operand.type, kind)

    // Lo mismo, para una direccion que ya se calculo, como el sujeto de un switch.
    private fun convertAddress(address: Address, type: Type?, kind: OperandKind): Address {
        val needsConversion =
            kind == OperandKind.FLOAT && ExpressionDag.kindOf(type) == OperandKind.INTEGER
        if (!needsConversion) return address

        val value = (address as? Constant)?.value
        if (value is Long) return Constant(value.toDouble())

        temporaries.consume(address)
        val converted = temporaries.newTemp(uses = 1)
        instructions += Unary(converted, TacUnaryOperator.INT_TO_FLOAT, address, OperandKind.FLOAT)
        return converted
    }

    // En `x < f()`, la x se lee ANTES de la llamada, porque se evalua primero. Una
    // variable no genera instruccion y se leeria despues, asi que se copia a un
    // temporal si lo que sigue puede modificarla.
    private fun readBefore(address: Address, later: Expression): Address =
        if (ExpressionDag.isPure(later)) address else snapshotIfName(address)

    private fun snapshotIfName(address: Address): Address {
        if (address !is Name) return address

        val snapshot = temporaries.newTemp(uses = 1)
        instructions += Copy(snapshot, address)
        return snapshot
    }

    // ══════════════════════════════════════════════════════════════════════
    //  El chequeo de division entre cero
    // ══════════════════════════════════════════════════════════════════════

    // Solo la division ENTERA: en flotantes IEEE 754, dividir entre 0.0 da Infinity,
    // que es un valor legitimo. Es la misma regla del interprete.
    //
    // Un divisor constante no lo necesita: el cero constante ya lo rechazo el
    // TypeChecker, asi que cualquier otra constante es segura.
    private fun needsZeroCheck(operation: DagOperation, operands: List<Address>): Boolean {
        if (operation !is DagOperation.Arithmetic) return false
        if (operation.kind != OperandKind.INTEGER) return false

        val isDivision = operation.operator == ArithmeticOperator.DIVIDE ||
            operation.operator == ArithmeticOperator.MODULO
        if (!isDivision) return false

        return operands[1] !is Constant
    }

    //     if b != 0 goto L1
    //     throw "División entre cero (línea 7)"
    // L1:
    //
    // En linea, junto a la division, para que el mensaje lleve la linea del fuente.
    private fun emitZeroCheck(divisor: Address, line: Int) {
        val safe = newLabel()
        instructions += IfRelationalGoto(
            divisor, RelationalOperator.NOT_EQUAL, Constant(0L), OperandKind.INTEGER, safe
        )
        instructions += Throw(Constant("División entre cero (línea $line)"))
        instructions += LabelDefinition(safe)
    }

    private fun newLabel(): Label = Label(nextLabel++)

    // ══════════════════════════════════════════════════════════════════════
    //  Ayudantes
    // ══════════════════════════════════════════════════════════════════════

    private fun symbolOf(identifier: Identifier): Symbol =
        requireNotNull(identifier.resolvedSymbol) { "'${identifier.name}' sin resolver" }

    // El cero de cada tipo, el mismo del interprete: una variable sin inicializar no
    // puede tener en el TAC un valor distinto del que tiene al ejecutarse.
    private fun defaultValueOf(type: Type): Any? = when (type) {
        IntegerType -> 0L
        FloatType -> 0.0
        StringType -> ""
        BooleanType -> false

        // Clases y listas arrancan en null: son referencias.
        else -> null
    }

}
