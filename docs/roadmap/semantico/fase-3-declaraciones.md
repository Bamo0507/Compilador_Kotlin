# Fase 3 — Pasada 1: declaraciones

**Objetivo de la fase:** recorrer el AST una primera vez y **registrar todo lo que
se declara** —clases, funciones, campos, métodos— en el árbol de ámbitos, sin
entrar a los cuerpos.

**Al terminar:** el árbol de `Scope` completo, y los errores de declaración
reportados (nombre repetido, tipo inexistente, herencia circular).

**Estimación:** una o dos sesiones.

---

## Por qué dos pasadas y no una

Este programa es **válido** en Compiscript:

```cps
function a(): integer { return b(); }     // usa b antes de que exista
function b(): integer { return 1; }
```

Si el compilador hiciera una sola pasada, al llegar a `b()` dentro del cuerpo de `a`
el nombre `b` todavía no estaría en la tabla, y reportaría *"función no declarada"*
— un error falso.

La solución es partir el trabajo:

| | Pasada 1 (esta fase) | Pasada 2 (Fase 4) |
|---|---|---|
| Qué hace | registra **firmas**: nombres, tipos, parámetros | verifica **cuerpos**: expresiones, tipos, llamadas |
| Entra a los cuerpos de funciones | **No** | Sí |
| Qué produce | el árbol de ámbitos | el AST decorado con tipos |

Al no entrar a los cuerpos en la pasada 1, cuando la pasada 2 llega a `b()` el
nombre ya está registrado. Eso es lo que habilita las **referencias adelantadas**, y
aplica igual a clases (`let p: Perro` antes de `class Perro`) y a métodos que se
llaman entre sí.

---

## Decisión: funciones recursivas sobre el AST, no Listeners de ANTLR

La idea inicial era usar un **Listener de ANTLR** para la pasada 1 y un **Visitor**
para la pasada 2. Hay una razón técnica decisiva para no hacerlo:

> **Un Listener de ANTLR recorre TODO el árbol automáticamente y no se le puede
> impedir entrar a los cuerpos de las funciones.**

Y la pasada 1 necesita justamente *no* entrar. Con un Listener habría que agregar
una bandera del tipo *"¿estoy dentro de un cuerpo? entonces ignoro todo"*, que es
exactamente la clase de truco que vuelve el código ilegible.

Con funciones recursivas sobre el AST, simplemente no se recurre:

```kotlin
private fun collectFunctionDeclaration(decl: FunctionDeclaration) {
    // registro la firma...
    // y NO llamo a collect(decl.body). Fin.
}
```

Tres beneficios adicionales:

1. **Un solo lugar del proyecto conoce ANTLR** (el `AstBuilder`, Fase 2). Todo lo
   demás trabaja sobre el AST limpio.
2. **No se duplica** la lectura de anotaciones de tipo: `TypeReference` ya viene
   construido desde el AST.
3. El enunciado pide *"recorrer el árbol mediante Listeners **o** Visitors de
   ANTLR"*, y el `AstBuilder` de la Fase 2 **es** un Visitor de ANTLR. El requisito
   se cumple ahí.

### La forma del recorrido: una función por construcción

Esto es lo que pidió el catedrático —*"todo por medio de funciones: según el ámbito
en el que me encuentre, una función para procesar toda su información"*— y así se
implementa:

```kotlin
// El despachador: mira qué clase de sentencia es y llama a su función.
private fun collect(stmt: Statement) {
    when (stmt) {
        is ClassDeclaration    -> collectClassDeclaration(stmt)
        is FunctionDeclaration -> collectFunctionDeclaration(stmt)

        // Todo lo demás no declara nada en esta pasada. Ver "Qué NO registra la
        // Pasada 1" abajo: las variables y los bloques son trabajo de la Pasada 2.
        else -> Unit
    }
}
```

Cada `collectX` es una función corta que hace **una** cosa. Y en las que abren
ámbito, la función *es* el ámbito: entra al principio, sale al final.

### Qué NO registra la Pasada 1, y por qué

El `when` es corto a propósito: **solo clases y funciones.** Las variables no, y no es
un olvido — hay dos razones y las dos son de fondo.

**1. Se declararían dos veces.** La Fase 4 declara las variables en
`checkVariableDeclaration`, porque tiene que hacerlo de todos modos para las locales
de cada bloque. Si la Pasada 1 también registrara las del nivel superior, un
`let x: integer = 5;` global produciría *"'x' ya fue declarado en este ámbito"* sobre
código correcto.

**2. Las variables NO deben ser referenciables hacia adelante.** Esto tiene que ser
error:

```cps
print(x);              // ERROR: x no esta declarada todavia
let x: integer = 5;
```

Si la Pasada 1 registrara `x`, la Pasada 2 la encontraría y el `print` pasaría. **Las
referencias adelantadas son una característica de las clases y las funciones, no de
las variables** — y esa distinción es justamente lo que las dos pasadas codifican.

Por la misma razón la Pasada 1 **no entra a los bloques**: sus declaraciones locales
las registra la Pasada 2, cuando el cursor de ámbito está en el lugar correcto.

**Los campos de una clase sí se registran aquí** (`collectField`), y eso no contradice
lo anterior: un campo es parte de la **interfaz** de la clase. `perro.nombre` tiene
que encontrarlo, y la clase puede estar declarada más abajo que su uso.

| Qué se declara | Pasada 1 | Pasada 2 |
|---|---|---|
| clases | ✅ | — |
| funciones y métodos (la firma) | ✅ | — |
| campos de clase | ✅ | — |
| parámetros de función | — | ✅ (al entrar al cuerpo) |
| variables y constantes | — | ✅ (en orden de aparición) |
| la variable de un `foreach` | — | ✅ |
| el parámetro de un `catch` | — | ✅ |

---

## Ticket 3.1 — `TypeResolver`

- **Estado**: completado
- **Depende de**: 1.1, 1.3, 1.5

**Archivos:**

- `frontend/semantic/TypeResolver.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/TypeResolverTest.kt` (NUEVO)

**Qué es esto, en simple:** el AST guarda lo que el programador **escribió**
(`TypeReference("Perro", 0)`). Esta clase lo convierte en lo que el compilador
**entiende** (`ClassType("Perro")`), y reporta error si el tipo no existe.

Se separa en su propio archivo porque lo usan las dos pasadas: la 1 para las firmas
y la 2 para las declaraciones locales.

```kotlin
// Los nombres de los tipos primitivos salen de la gramática (regla `baseType`:
// 'boolean' | 'integer' | 'float' | 'string'). Este mapa se DERIVA del `name` de
// cada Type en vez de repetir los strings a mano, así que `Type.name` queda como
// la ÚNICA definición de cómo se escribe cada tipo primitivo.
//
// Vive aquí y no en Type.kt por el principio 7 del proyecto: Type.kt son datos,
// y el mapeo de texto a tipo es la responsabilidad de este archivo.
private val PRIMITIVE_TYPES: Map<String, Type> =
    listOf(IntegerType, FloatType, StringType, BooleanType)
        .associateBy { it.name }

// Convierte un tipo ESCRITO (TypeReference) en un tipo RESUELTO (Type).
//
// Necesita el ámbito global porque los nombres de clase se validan contra las
// clases declaradas.
class TypeResolver(
    private val globalScope: Scope,
    private val diagnostics: Diagnostics
) {

    // Un TypeReference nulo significa "no se anotó el tipo". Devuelve null para que
    // el LLAMADOR decida qué significa eso: no lo decide esta clase.
    // Ver "Los cuatro significados del null" abajo.
    fun resolve(typeRef: TypeReference?): Type? {
        if (typeRef == null) return null

        val baseType = resolveBaseName(typeRef)

        // Si el nombre base ya fallo, NO envolverlo: ArrayType(ErrorType) no es
        // ErrorType, y la Fase 4 no lo reconoceria como "error ya reportado". Cortar
        // aqui mantiene UN error por equivocacion.
        if (baseType == ErrorType) return ErrorType

        // Cada '[]' envuelve el tipo en un ArrayType.
        //   TypeReference("integer", 2) -> ArrayType(ArrayType(IntegerType))
        var result = baseType
        repeat(typeRef.arrayDimensions) {
            result = ArrayType(result)
        }

        return result
    }

    // Primero primitivos, despues clases. El orden decide que si alguien escribe
    // `class integer { }`, el tipo `integer` sigue siendo el primitivo.
    private fun resolveBaseName(typeRef: TypeReference): Type =
        PRIMITIVE_TYPES[typeRef.baseName] ?: resolveClassName(typeRef)

    private fun resolveClassName(typeRef: TypeReference): Type {
        // lookupLocal y no lookup: las clases viven SOLO en el ambito global, asi
        // que no hay hacia donde subir. Sobre globalScope las dos hacen lo mismo
        // —no tiene parent ni superclass—, pero lookupLocal dice la intencion.
        val symbol = globalScope.lookupLocal(typeRef.baseName)

        // Dos condiciones porque son dos errores distintos:
        //   symbol == null    ->  `let a: Gato;`       el nombre no existe
        //   kind != CLASS     ->  `let b: contador;`   existe, pero es una variable
        //
        // Sin la segunda, `let b: contador;` produciria ClassType("contador"): un
        // tipo que apunta a una clase inexistente, y el error saldria mucho despues
        // con un mensaje incomprensible.
        if (symbol == null || symbol.kind != DeclarationKind.CLASS) {
            diagnostics.report(CompilerError.SemanticError(
                location = typeRef.location,
                message = "El tipo '${typeRef.baseName}' no está declarado"
            ))
            return ErrorType
        }

        return ClassType(typeRef.baseName)
    }
}
```

### Los cuatro significados del `null`

`resolve` devuelve `null` para *"no se anotó"* y **no decide qué hacer con eso**. Cada
llamador lo interpreta según su contexto:

```kotlin
// funcion: null -> void                              (decision 15)
val returnType = typeResolver.resolve(decl.returnType) ?: VoidType

// parametro: null -> error, no hay de donde inferir
typeResolver.resolve(parameter.declaredType) ?: ErrorType

// campo: null -> error, con su propio mensaje
val fieldType = typeResolver.resolve(decl.declaredType)
if (fieldType == null) { report("El campo '${decl.name}' necesita un tipo anotado") }

// variable local: null -> INFERIR del inicializador
declaredType != null -> declaredType
initializer != null  -> initializer.type
```

Si `resolve` hubiera devuelto `ErrorType` en vez de `null`, no podría distinguirlos:
reportaría un error donde no lo hay (una función void) y perdería la inferencia.

**Por qué devuelve `ErrorType` y no lanza excepción:** un tipo inexistente es un
error del usuario, no un bug del compilador. Devolviendo `ErrorType` el análisis
sigue y se pueden reportar los demás errores del archivo en la misma corrida. Si se
lanzara, el usuario vería un error por compilación y tendría que arreglarlos de uno
en uno.

### El orden importa: esto corre después de la ronda A

`resolveClassName` depende de que las clases **ya estén registradas**. Por eso la
Pasada 1 tiene dos rondas y los nombres de clase se anotan primero:

```kotlin
fun collect(program: Program) {
    registerClassNames(program.statements)      // <-- primero los nombres de clase
    program.statements.forEach { collect(it) }  // <-- ahora resolve() las encuentra
}
```

Llamar a `resolve(TypeReference("Perro", 0))` antes de la ronda A reportaría un error
falso.

### Aceptación

- `resolve(TypeReference("integer", 0))` devuelve `IntegerType`. ✅
- `resolve(TypeReference("integer", 2))` devuelve `ArrayType(ArrayType(IntegerType))`. ✅
- `resolve(TypeReference("Perro", 0))` con `Perro` declarada devuelve `ClassType("Perro")`.
- `resolve(TypeReference("Gato", 0))` con `Gato` **no** declarada devuelve `ErrorType` y
  reporta un `SemanticError` con la línea y columna del `TypeReference`.
- `resolve(TypeReference("Gato", 1))` devuelve **`ErrorType`**, no
  `ArrayType(ErrorType)`, y reporta **un solo** error. *Este es el test que evita la
  cascada: `ArrayType(ErrorType)` no es reconocible como "error ya reportado" y la
  Fase 4 emitiría un segundo error.*
- `resolve(TypeReference("contador", 0))` con `contador` declarada como **variable**
  devuelve `ErrorType` y reporta el error: existe el nombre, pero no es una clase.
- `resolve(null)` devuelve `null` sin reportar nada. ✅
- `resolve(TypeReference("integer", 1))` dos veces devuelve tipos iguales (`==`).
- `PRIMITIVE_TYPES` tiene exactamente cuatro entradas, y sus claves son iguales a
  los `name` de `IntegerType`, `FloatType`, `StringType` y `BooleanType`. Ningún
  string de tipo primitivo está escrito a mano en este archivo.

### Respaldo

Dragon Book §6.3.1 (expresiones de tipo). Enunciado, *"error por uso de variables no
declaradas"* extendido a tipos.

---

## Ticket 3.2 — `DeclarationCollector`

- **Estado**: completado
- **Depende de**: 3.1

**Archivos:**

- `frontend/semantic/DeclarationCollector.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/DeclarationCollectorTest.kt` (NUEVO)

**Qué es esto, en simple:** la primera pasada. Recorre el programa anotando en el
árbol de ámbitos *qué existe*: qué clases, qué funciones, qué campos. No mira
adentro de los cuerpos.

### Estructura

```kotlin
// Pasada 1: registra las declaraciones en el árbol de ámbitos.
//
// NO entra a los cuerpos de las funciones. Eso es lo que permite las referencias
// adelantadas (usar una función declarada más abajo).
class DeclarationCollector(
    private val diagnostics: Diagnostics
) {
    val globalScope = Scope(ScopeKind.GLOBAL, "global", parent = null)
    private var currentScope = globalScope
    private val typeResolver = TypeResolver(globalScope, diagnostics)

    fun collect(program: Program) {
        // Dos rondas sobre el nivel superior:
        //   Ronda A: registrar los NOMBRES de las clases (sin sus miembros).
        //   Ronda B: registrar todo lo demás, ya con los nombres de clase visibles.
        //
        // Es lo que permite  class A { let b: B; }  seguido de  class B { }.
        registerClassNames(program.statements)
        program.statements.forEach { collect(it) }
    }
    // ...
}
```

**Por qué dos rondas dentro de la pasada 1:** una clase puede tener un campo cuyo
tipo es otra clase declarada más abajo. Si se resolvieran los tipos de los campos
en la misma pasada que registra los nombres de clase, el orden de declaración
importaría. Registrando primero **solo los nombres** (sin miembros, sin tipos), la
segunda ronda ya los ve todos.

Es la misma idea de las dos pasadas, aplicada un nivel más adentro. Y es baratísima:
la ronda A solo recorre el nivel superior del programa.

### El despachador

```kotlin
private fun collect(stmt: Statement) {
    when (stmt) {
        is ClassDeclaration    -> collectClassDeclaration(stmt)
        is FunctionDeclaration -> collectFunctionDeclaration(stmt)

        // Todo lo demás no declara nada en esta pasada, incluidas las variables:
        // ver "Qué NO registra la Pasada 1" arriba.
        else -> Unit
    }
}
```

### Las funciones de declaración

```kotlin
// Ronda A: solo los nombres de las clases, para que se puedan referenciar entre sí.
//
// filterIsInstance recorre SOLO el nivel superior del programa. Ver "Limitacion:
// las clases van en el nivel superior" abajo.
private fun registerClassNames(statements: List<Statement>) {
    statements.filterIsInstance<ClassDeclaration>().forEach { decl ->
        // Se verifica el nombre ANTES de abrir el ambito. Si se abriera primero y la
        // declaracion fallara por nombre repetido, quedaria un Scope huerfano
        // colgado en globalScope.children, y la GUI mostraria dos nodos para la
        // misma clase — uno de ellos vacio.
        val previous = globalScope.lookupLocal(decl.name)
        if (previous != null) {
            diagnostics.reportAlreadyDeclared(decl.name, decl.location, previous)
            return@forEach
        }

        // El ambito se crea aqui, VACIO. Los miembros los mete la ronda B.
        val classScope = globalScope.openChild(ScopeKind.CLASS, decl.name)

        globalScope.declareOrReport(Symbol(
            name = decl.name,
            kind = DeclarationKind.CLASS,
            type = ClassType(decl.name),
            location = decl.location,
            scopeName = globalScope.name,
            offset = 0,
            memberScope = classScope        // <-- el enlace que hace posible perro.nombre
        ))
    }
}

// Ronda B para una clase: resolver la herencia y registrar sus miembros.
//
// NO crea el ambito: lo recupera. La ronda A ya lo creo y lo guardo en memberScope.
private fun collectClassDeclaration(decl: ClassDeclaration) {
    val classSymbol = globalScope.lookupLocal(decl.name) ?: return

    // Si el simbolo registrado NO es de ESTA declaracion, es una clase repetida y el
    // error ya se reporto en la ronda A. Seguir seria peor que salir: lookupLocal
    // devuelve el simbolo de la PRIMERA clase con ese nombre, asi que los miembros
    // de esta se declararian en el ambito de la otra, y la primera clase ganaria en
    // silencio campos que no tiene.
    if (classSymbol.location != decl.location) return

    val classScope = classSymbol.memberScope ?: return

    resolveSuperclass(decl, classScope)

    // La función ES el ámbito: se entra al principio y se sale al final.
    val previousScope = currentScope
    currentScope = classScope

    decl.members.forEach { member ->
        when (member) {
            is VariableDeclaration      -> collectField(member)
            is FunctionDeclaration -> collectMethod(member)
            else            -> Unit
        }
    }

    currentScope = previousScope
}

// Registra la FIRMA de la función. No entra al cuerpo.
private fun collectFunctionDeclaration(decl: FunctionDeclaration) {
    val parameterTypes = decl.parameters.map { parameter ->
        typeResolver.resolve(parameter.declaredType) ?: ErrorType
    }
    val returnType = typeResolver.resolve(decl.returnType) ?: VoidType

    // Siempre FUNCTION, tanto suelta como dentro de una clase. Que sea un método
    // lo marca `isMember`, y eso lo pone `Scope.declare` solo (ver ticket 1.2).
    currentScope.declareOrReport(Symbol(
        name = decl.name,
        kind = DeclarationKind.FUNCTION,
        type = FunctionType(parameterTypes, returnType),
        location = decl.location,
        scopeName = currentScope.name,
        offset = 0
    ))

    // Aquí NO se llama a collect(decl.body).
    // Esa omisión deliberada es lo que habilita las referencias adelantadas.
}

// Un metodo es casi lo mismo que una funcion suelta: `currentScope` ya es el de la
// clase, y `Scope.declare` marca isMember solo. Lo unico propio es la validacion del
// constructor, que solo puede existir dentro de una clase.
private fun collectMethod(decl: FunctionDeclaration) {
    // Un constructor no devuelve nada. Como la gramatica no tiene sintaxis propia
    // para el —es una `function` que se llama "constructor"—, esta regla no la puede
    // imponer el parser y hay que verificarla aqui.
    if (decl.name == CONSTRUCTOR_NAME && decl.returnType != null) {
        diagnostics.report(CompilerError.SemanticError(
            location = decl.location,
            message = "El constructor de '${currentScope.name}' no puede declarar " +
                      "tipo de retorno"
        ))
    }

    collectFunctionDeclaration(decl)
}

// Un campo de clase. Es la UNICA variable que la Pasada 1 registra, porque es parte
// de la interfaz de la clase: `perro.nombre` tiene que encontrarlo, y la clase puede
// estar declarada despues de su uso.
private fun collectField(decl: VariableDeclaration) {
    // Un campo SIN tipo anotado no tiene de donde inferir: la Pasada 1 no evalua
    // inicializadores, y el tipo del campo debe conocerse antes de verificar
    // cualquier cuerpo que lo use.
    val fieldType = typeResolver.resolve(decl.declaredType)

    if (fieldType == null) {
        diagnostics.report(CompilerError.SemanticError(
            location = decl.location,
            message = "El campo '${decl.name}' necesita un tipo anotado"
        ))
    }

    currentScope.declareOrReport(Symbol(
        name = decl.name,
        kind = if (decl.isConstant) DeclarationKind.CONSTANT else DeclarationKind.VARIABLE,
        type = fieldType ?: ErrorType,
        location = decl.location,
        scopeName = currentScope.name,
        offset = 0,
        initialized = decl.initializer != null
    ))
}
```

**Por qué un campo exige tipo anotado y una variable local no.** Una local se puede
inferir porque la Pasada 2 ya evaluó su inicializador cuando llega a declararla. Un
campo se registra en la Pasada 1, que **no evalúa expresiones** — y no puede
evaluarlas, porque el inicializador podría llamar a un método de una clase que
todavía no se registró. Así que `class A { let x = 5; }` es error y hay que escribir
`class A { let x: integer = 5; }`.

### El ayudante que reporta la redeclaración

Declarar un símbolo y reportar si el nombre ya existía es **la misma operación en las
dos pasadas**, así que vive una sola vez, en su propio archivo:

```kotlin
// frontend/semantic/ScopeDeclaration.kt
//
// Son funciones de extension y no metodos de Scope porque Scope es un modelo de
// datos y no debe conocer Diagnostics (principio 7 del proyecto). Y viven aqui y no
// duplicadas en cada pasada porque el MENSAJE tiene que ser identico en las dos.

// Declara el simbolo, y si el nombre ya existia en ESTE ambito, reporta el error
// diciendo donde estaba la declaracion anterior.
fun Scope.declareOrReport(symbol: Symbol, diagnostics: Diagnostics) {
    when (val result = declare(symbol)) {
        is DeclareResult.Ok -> Unit
        is DeclareResult.AlreadyDeclared ->
            diagnostics.reportAlreadyDeclared(
                symbol.name, symbol.location, result.previous
            )
    }
}

// El mensaje, aparte, porque registerClassNames lo necesita SIN declarar: ahi el
// nombre repetido se detecta antes de intentar, para no dejar un ambito huerfano.
fun Diagnostics.reportAlreadyDeclared(
    name: String,
    location: LexemeLocation,
    previous: Symbol
) {
    report(CompilerError.SemanticError(
        location = location,
        message = "'$name' ya fue declarado en este ámbito " +
                  "(línea ${previous.location.line})"
    ))
}
```

**TODAS las funciones `collectX` de esta fase y `checkX` de la Fase 4 declaran a
través de `declareOrReport`.** En el `DeclarationCollector` se lee así:

```kotlin
currentScope.declareOrReport(symbol, diagnostics)
```

**Cuatro reglas del enunciado se cumplen con esta sola función**, porque
`Scope.declare` no distingue qué es lo que se declara:

- prohibición de redeclarar identificadores en el mismo ámbito
- detección de funciones repetidas con el mismo nombre
- clases repetidas
- campos repetidos en la misma clase

Y es donde se ve para qué sirve que `DeclareResult` sea un `sealed interface` en vez
de un `Boolean`: `result.previous.location.line` es lo que permite decir *"(línea 3)"*
en vez de solo *"ya fue declarado"*.

Eso no es cosmética: es la propiedad **transparente** del sistema de tipos.
*"'x' ya fue declarado"* obliga al usuario a buscar dónde;
*"'x' ya fue declarado en este ámbito (línea 3)"* le dice exactamente qué comparar.

### Limitación: las clases van en el nivel superior

`registerClassNames` recorre solo `program.statements`. Pero la gramática permite
declarar una clase en cualquier lugar donde quepa un `statement`:

```cps
{ class A { } }                        // bloque suelto
function f() { class A { } }           // cuerpo de funcion
if (x) { class A { } }                 // rama de un if
switch (x) { case 1: class A { } }     // dentro de un case
```

Los cuatro **parsean**, y ninguno se registra. Un `let a: A;` después daría
*"el tipo 'A' no está declarado"*.

**Se deja así a propósito.** Soportarlo obligaría a registrar las clases por ámbito en
vez de globalmente, y entonces `ClassType` tendría que llevar la ruta del ámbito
además del nombre para que dos clases `A` en bloques distintos fueran tipos distintos.
Es complejidad real por un constructo que ningún programa del enunciado usa.

**Lo que sí es imposible por gramática** es una clase dentro de otra:

```antlr
classMember: functionDeclaration | variableDeclaration | constantDeclaration;
//           no existe classDeclaration aqui
```

Así que `class A { class B { } }` no parsea, y eso no hay que documentarlo como
limitación: es una regla del lenguaje.

### Herencia: resolver la superclase y detectar ciclos

```kotlin
private fun resolveSuperclass(decl: ClassDeclaration, classScope: Scope) {
    val superName = decl.superclassName ?: return

    val superSymbol = globalScope.lookupLocal(superName)

    if (superSymbol == null || superSymbol.kind != DeclarationKind.CLASS) {
        diagnostics.report(CompilerError.SemanticError(
            location = decl.location,
            message = "La clase '${decl.name}' hereda de '$superName', " +
                      "que no es una clase declarada"
        ))
        return
    }

    if (createsInheritanceCycle(decl.name, superName)) {
        diagnostics.report(CompilerError.SemanticError(
            location = decl.location,
            message = "Herencia circular: '${decl.name}' hereda de '$superName', " +
                      "que a su vez hereda de '${decl.name}'"
        ))
        return
    }

    classScope.attachSuperclass(superSymbol.memberScope!!)
}
```

**Nota:** `Scope` es inmutable en sus enlaces (`superclass` es un `val` del
constructor), pero la superclase se conoce **después** de crear el ámbito de la
clase, en la ronda B. Dos opciones:

- **Recomendada:** en la ronda A, crear el ámbito de clase **sin** superclase, y en
  la ronda B **reemplazarlo** por uno nuevo con la superclase puesta, actualizando
  el `memberScope` del símbolo. Requiere que `Symbol` se pueda reemplazar en el
  ámbito.
- **Alternativa más simple:** cambiar `superclass` de `val` a
  `var superclass: Scope? = null; private set`, con un método
  `attachSuperclass(scope: Scope)` que solo se puede llamar una vez.

La alternativa es más simple y menos propensa a error, así que **se elige esa**, y
se agrega a `Scope` en el ticket 1.3 al implementarlo:

```kotlin
// En Scope:
var superclass: Scope? = null
    private set

// Se llama una sola vez, durante la Pasada 1, cuando ya se sabe de qué hereda.
fun attachSuperclass(scope: Scope) {
    require(superclass == null) { "La superclase de '$name' ya fue asignada" }
    superclass = scope
}
```

**La detección de ciclos** es un recorrido simple hacia arriba: se sigue la cadena
de superclases y si se vuelve al punto de partida, hay ciclo. Es obligatoria porque
sin ella `lookup` a través de `superclass` entraría en recursión infinita y el
compilador colgaría en vez de dar un error.

### El constructor

La gramática no tiene sintaxis de constructor: es una `functionDeclaration` que
**se llama** `constructor`. Se reconoce por nombre, y la constante vive en
`Symbol.kt` porque la usan **dos** fases: esta para validarlo, y la Fase 4 para
buscarlo desde `new`.

```kotlin
// En frontend/semantic/symbols/Symbol.kt, al nivel del archivo:
//
// La gramatica no tiene sintaxis de constructor. Una clase lo declara como una
// funcion que se LLAMA asi, y este es el unico lugar donde ese nombre esta escrito.
const val CONSTRUCTOR_NAME = "constructor"
```

Tres decisiones que la gramática deja abiertas y que hay que cerrar y documentar:

| Situación | Decisión | Por qué |
|---|---|---|
| Clase sin `constructor` declarado | Constructor implícito de 0 parámetros | `new Animal()` es legal; `new Animal("x")` da error "esperaba 0 argumentos, recibió 1" |
| ¿Se hereda el constructor? | **Sí**, si la clase no declara uno propio | Compiscript **no tiene `super`**, así que una subclase sin constructor propio no tendría ninguna forma de inicializar los campos heredados: la herencia quedaría inutilizable. En Java no heredarlo funciona porque existe `super(...)`. Y el ejemplo de `Especificaciones.md` lo asume: `class Perro : Animal` sin constructor, con `new Perro("Toby")` |
| `constructor` con tipo de retorno | Error | Un constructor no devuelve nada. Lo valida `collectMethod`, porque la gramática no puede |

### Aceptación

**Casos válidos:**

- Referencia adelantada de función: `function a() { return b(); } function b() { }`
  no produce errores.
- Referencia adelantada de clase: `let p: Perro; class Perro { }` no produce errores.
- Un `let x: integer = 5;` del nivel superior queda declarado **una sola vez**: la
  Pasada 1 no lo registra. *Test que atrapa la doble declaración.*
- Un método que usa un campo declarado más abajo en la misma clase no produce error
  (los campos se registran en la ronda B, antes de verificar cualquier cuerpo).
- Clases mutuamente referenciadas: `class A { let b: B; } class B { let a: A; }` no
  produce errores. **Este es el test que justifica las dos rondas.**
- `class Perro : Animal { }` con `Animal` declarada: el `Scope` de `Perro` tiene
  `superclass` apuntando al de `Animal`.
- Un campo heredado se encuentra: desde el ámbito de `Perro`,
  `lookupMember("nombre")` encuentra el campo declarado en `Animal`.

**Casos con error, cada uno con línea y columna verificadas:**

- Dos variables con el mismo nombre en el mismo ámbito.
- Dos funciones con el mismo nombre (el enunciado lo exige: no hay sobrecarga).
- Dos clases con el mismo nombre.
- Dos campos con el mismo nombre en la misma clase.
- `class Perro : NoExiste { }`.
- `class A : B { }` con `class B : A { }` → herencia circular, y el compilador **no
  cuelga**.
- Un campo de tipo inexistente: `class A { let x: NoExiste; }`.
- `let x: NoExiste;`.
- `class A { let x = 5; }` → error: un campo necesita tipo anotado.
- `print(x); let x: integer = 5;` → error *"la variable 'x' no está declarada"*. **Este
  es el test que prueba que la Pasada 1 no registra variables**: si las registrara,
  este programa pasaría.

**Estructura resultante:**

- Después de recolectar, `globalScope.children` contiene un `Scope` por cada clase
  declarada.
- Los offsets de los campos de una clase son 0, 1, 2… en orden de declaración.

### Respaldo

Enunciado: *"prohibición de redeclaración de identificadores en el mismo ámbito"*,
*"detección de redeclaración de funciones con el mismo nombre"*, *"verificación de
la correcta invocación del constructor"*. Dragon Book §2.7.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 3.1 | `TypeReference` escrito → `Type` resuelto, con error si el tipo no existe |
| 3.2 | El árbol de ámbitos con todas las declaraciones y sus firmas |

**Al terminar:** el árbol de `Scope` está completo a nivel de declaraciones y los
errores de declaración están reportados. **La GUI ya puede mostrar la tabla de
símbolos** — que son 25 de los 100 puntos, disponibles antes de escribir una sola
regla de tipos.
