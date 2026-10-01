# Fase 5: Objetos y listas

**Objetivo de la fase:** traducir la creación de objetos con su constructor, el acceso a
campos, los métodos con `this` y despacho por tabla, la creación de listas, su acceso
por índice y el `foreach`, con los chequeos de índice y de `null`.

**Por qué va después de la Fase 4:** un método es una función con un parámetro
escondido, y un objeto es un bloque cuyos desplazamientos se calculan con las mismas
reglas de tamaño y alineación. Las dos cosas ya existen.

**Al terminar:** todo programa válido de Compiscript genera TAC. Los últimos `TODO` del
generador desaparecen.

**Teoría que la sostiene:** puntos 10 y 11. Dragon Book 6.4 (direcciones de elementos de
arreglos) y 7.4 (el montículo). El despacho por tabla no está en las diapositivas ni en
el Dragon Book: es la técnica estándar de Java y C++ (decisión 43).

---

## Ticket 5.1: La disposición de clases en memoria

- **Estado**: pendiente
- **Depende de**: 4.2

**Archivos:**

- `frontend/intermediate/StorageAllocator.kt` (MODIFICAR)
- `frontend/intermediate/models/StorageLocation.kt` (MODIFICAR: `Field`)
- `frontend/intermediate/models/ClassLayout.kt` (NUEVO)
- `frontend/semantic/symbols/Scope.kt` (MODIFICAR: `classLayout`)
- `gui/screens/SymbolTableScreen.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/StorageAllocatorTest.kt` (AMPLIAR)

### El objeto

```
class Animal { let nombre: string; function hablar() {...} function comer() {...} }
class Perro : Animal { let raza: string; function hablar() {...} }

objeto Animal              objeto Perro
 0  tabla de métodos        0  tabla de métodos
 4  nombre                  4  nombre      <- heredado, en la misma posición
    tamaño: 8               8  raza
                               tamaño: 12
```

- **La casilla 0** guarda la dirección de la tabla de métodos de la clase real del
  objeto (decisión 43). Va en todas las clases, aunque no tengan métodos: una sola
  forma de objeto.
- **Los campos heredados van primero**, en los desplazamientos que tenían en la
  superclase. Los propios siguen, con la alineación natural de la decisión 42.
- Cada campo recibe `StorageLocation.Field(offset)`.

### La tabla de métodos

```
tabla Animal: [ 0: Animal.hablar, 1: Animal.comer ]
tabla Perro:  [ 0: Perro.hablar,  1: Animal.comer ]
```

- Un método heredado ocupa la **misma posición** que en la tabla del padre.
- Un método que sobrescribe **reemplaza** la entrada en esa posición.
- Un método nuevo se agrega al final.
- El `constructor` y `$init` **no** van en la tabla: `new Perro(...)` nombra la clase
  exacta, así que se llaman directo.

```kotlin
data class ClassLayout(
    val className: String,
    val size: Int,
    // La posicion de cada entrada es su indice en la lista.
    val methods: List<FunctionLabel>
)
```

### En el IDE

Al seleccionar el ámbito de una clase, la tabla de símbolos muestra el desplazamiento
de cada campo, incluidos los heredados, el tamaño del objeto y su tabla de métodos.

### Aceptación

| Clase | Resultado esperado |
|---|---|
| `Animal` de arriba | tamaño 8; `nombre` en 4; tabla `[Animal.hablar, Animal.comer]` |
| `Perro` de arriba | tamaño 12; `nombre` en 4, `raza` en 8; tabla `[Perro.hablar, Animal.comer]` |
| `class Punto { let x: integer; let y: float; }` | `x` en 4, `y` en 8, tamaño 16 |
| Una clase sin campos ni métodos | tamaño 4: solo la casilla de la tabla |

---

## Ticket 5.2: Las instrucciones de objetos

- **Estado**: pendiente
- **Depende de**: 1.2, 5.1

**Archivos:**

- `frontend/intermediate/models/Address.kt` (MODIFICAR: `VirtualTableAddress`)
- `frontend/intermediate/models/Quadruple.kt` (MODIFICAR)
- `frontend/intermediate/TacPrinter.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacPrinterTest.kt` (AMPLIAR)

```kotlin
// La direccion de la tabla de metodos de una clase, en datos estaticos.
data class VirtualTableAddress(val className: String) : Address

// t = alloc n: pide n bytes al monticulo y devuelve la direccion del bloque.
data class Allocate(val result: Address, val size: Address) : Quadruple

// call t, n: llama a la funcion cuya direccion esta en t. Solo la usan los metodos.
data class IndirectCall(val result: Address?, val target: Address, val argumentCount: Int) : Quadruple

// vtable Perro: Perro.hablar, Animal.comer
data class VirtualTableDefinition(val className: String, val methods: List<FunctionLabel>) : Quadruple
```

| Familia | Sintaxis | Ejemplo |
|---|---|---|
| `Allocate` | `x = alloc n` | `t1 = alloc 12` |
| `IndirectCall` | `call t, n` / `x = call t, n` | `t3 = call t2, 1` |
| `VirtualTableDefinition` | `vtable C: m0, m1, ...` | `vtable Perro: Perro.hablar, Animal.comer` |
| `VirtualTableAddress` | `vtable.C` | `t1[0] = vtable.Perro` |

**Las tablas se emiten al inicio del TAC** (decisión 46), antes de `$main`: son datos
estáticos, no código, y la fase de assembler las va a traducir a su sección de datos.

### Aceptación

- Un test por familia nueva que fija su línea de texto.
- `toRow()` cubre las tres familias nuevas.

---

## Ticket 5.3: Objetos, `this` y métodos

- **Estado**: pendiente
- **Depende de**: 5.2, 4.4

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `frontend/intermediate/StorageAllocator.kt` (MODIFICAR: el parámetro `this`)
- `app/src/test/kotlin/org/compiler/TacGeneratorObjectTest.kt` (NUEVO)

### `new`

```
let p: Perro = new Perro("Toby");

    t1 = alloc 12
    t1[0] = vtable.Perro
    param t1
    call Perro.$init, 1
    param t1
    param "Toby"
    call Perro.constructor, 2
    p = t1
```

El constructor es el propio o el heredado, el mismo que eligió el `TypeChecker` con
`lookupMember`. Si la clase hereda el de `Animal`, la llamada es `call
Animal.constructor, 2`. Si no hay ninguno, no se emite.

### `$init` (decisión 44)

Una rutina generada por clase. Llama al `$init` de la superclase y después le da a cada
campo propio su inicializador o el valor por defecto de su tipo, el mismo que usa el
intérprete:

```
begin_func Perro.$init, ...
    param this
    call Animal.$init, 1
    this[8] = ""             // raza: el string vacío, que es el valor por defecto
end_func Perro.$init
```

**Por qué separado del constructor:** si `Perro` hereda el constructor de `Animal`,
ese constructor no sabe que existe `raza`. Con `$init` aparte, los campos propios de
`Perro` se inicializan siempre, tenga o no constructor propio.

### `this`

Cada método, constructor y `$init` recibe `this` como su primer parámetro, en el
desplazamiento 0 de su registro. El `StorageAllocator` lo agrega como un símbolo
sintético de categoría parámetro. Una función anidada dentro de un método lo lee con
saltos, como cualquier otra variable: `this^1`.

### Campos

| Código | TAC |
|---|---|
| `print(p.nombre);` | el chequeo de `null` sobre `p`, `t1 = p[4]`, `print_s t1` |
| `p.raza = "lab";` | el chequeo de `null`, `p[8] = "lab"` |
| `this.nombre` | `t1 = this[4]`, sin chequeo: `this` nunca es `null` |

### Métodos

```
a.hablar();          con a: Animal

    <chequeo de null sobre a>
    t1 = a[0]                // la tabla de la clase real del objeto
    t2 = t1[0]               // hablar está en la posición 0: desplazamiento 0 × 4
    param a                  // this
    call t2, 1
```

La posición sale de la tabla de la clase **declarada**, `Animal`, y vale para cualquier
subclase porque un método conserva su posición al heredarse. `this.hablar()` dentro de
un método también despacha por la tabla: si una subclase lo sobrescribió, se llama al
de la subclase.

Cada método se emite como función, con la etiqueta `Clase.metodo` y `this` en sus
parámetros.

**En el GDA**, una lectura de campo, una llamada a método y un `new` entran como nodos
`Opaque` (decisión 36): no se comparten.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `new Perro("Toby")` con constructor propio | los cuatro pasos de arriba |
| `new Perro("Toby")` heredando el constructor de `Animal` | `call Animal.constructor`, y `Perro.$init` inicializa `raza` |
| `class A { let x: integer = 1; let y: integer = this.x + 1; }` | `$init` asigna `x` y después lee `this[4]` para `y` |
| `let a: Animal = new Perro(...); a.hablar();` | la llamada indirecta por la tabla |
| `this.raza` dentro de un método | `this[8]`, sin chequeo de `null` |
| Una función anidada en un método que usa `this` | `this^1` |
| El `demo_completa.cps` | genera TAC completo |

---

## Ticket 5.4: Listas y `foreach`

- **Estado**: pendiente
- **Depende de**: 5.2

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorListTest.kt` (NUEVO)

### La lista en memoria

```
desplazamiento 0              el largo, 4 bytes
inicio de elementos           align(4, tamaño del elemento): 4 para integer, boolean y
                              referencias; 8 para float
elemento i                    inicio + i × tamaño
```

### Creación

```
let lista: integer[] = [10, 20, 30];

    t1 = alloc 16
    t1[0] = 3
    t1[4] = 10
    t1[8] = 20
    t1[12] = 30
    lista = t1
```

Una lista vacía pide solo la casilla del largo y le escribe 0.

### Acceso y asignación por índice

```
print(lista[i]);                     lista[i] = 5;

    <chequeos de null y de rango>        <chequeos de null y de rango>
    t1 = i * 4                           t1 = i * 4
    t1 = t1 + 4                          t1 = t1 + 4
    t2 = lista[t1]                       lista[t1] = 5
    print_i t2
```

Con un índice constante, el desplazamiento se calcula al compilar: `lista[2]` es
`lista[12]`.

### `foreach`

```
foreach (n in lista) { S }

    $lista = <lista>          // se evalúa una vez
    $i = 0
Linicio:
    t1 = $lista[0]            // el largo
    if $i >= t1 goto Lfin
    t2 = $i * 4
    t2 = t2 + 4
    n = $lista[t2]
    S
Lcont:
    $i = $i + 1
    goto Linicio
Lfin:
```

`$lista` y `$i` son locales ocultas que el `StorageAllocator` agrega al registro de la
función, con el `$` para que no choquen con nombres del usuario. No pueden ser
temporales: viven todo el bucle y los temporales se reciclan en cada sentencia
(punto 5). `continue` salta a `Lcont` y `break` a `Lfin`, con la pila de bucles de la
Fase 3.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `[10, 20, 30]` | el `alloc 16` y las cuatro escrituras |
| `[1.5, 2.5]` | los elementos empiezan en 8 |
| `lista[i]` y `lista[i] = 5` | la fórmula del desplazamiento |
| `lista[2]` | el desplazamiento constante, 12 |
| `matriz[i][j]` con `integer[][]` | dos accesos encadenados: el primero devuelve una referencia |
| `foreach` con `continue` | salta a `Lcont` |
| `tipos_listas.cps` | genera TAC completo |

---

## Ticket 5.5: Los chequeos de índice y de `null`

- **Estado**: pendiente
- **Depende de**: 5.3, 5.4

**Archivos:**

- `frontend/intermediate/TacGenerator.kt` (MODIFICAR)
- `app/src/test/kotlin/org/compiler/TacGeneratorListTest.kt` (AMPLIAR)

Completan la decisión 29, con la misma forma en línea del chequeo de división:

```
    if lista != null goto L5
    throw "Acceso a null (línea 7)"
L5:
    t3 = lista[0]
    if i < 0 goto L6
    if i < t3 goto L7
L6:
    throw "Índice fuera de rango (línea 7)"
L7:
    ...el acceso...
```

**Dónde se emite el de `null` (decisión 45):** antes de cada lectura o escritura de un
campo, de cada acceso por índice y de cada llamada a método. No se emite sobre `this`.

**El de rango** se emite en cada acceso por índice. Con un índice constante se omite la
comparación con 0, porque el `TypeChecker` ya rechazó los negativos constantes, pero se
conserva la del largo.

### Aceptación

| Programa | Lo que se verifica |
|---|---|
| `p.nombre` | el chequeo de `null` antes de leer |
| `this.nombre` | sin chequeo |
| `lista[i]` | los chequeos de `null` y de rango |
| `lista[2]` | solo la comparación con el largo |
| `try { print(lista[10]); } catch (e) { print(e); }` | el `throw` queda dentro del bloque protegido |

---

## Ticket 5.6: El documento, secciones de objetos, listas y montículo

- **Estado**: pendiente
- **Depende de**: 5.5, 4.6

**Archivos:**

- `docs/lenguaje-intermedio.md` (AMPLIAR)

| Sección | Contenido |
|---|---|
| 21. Objetos en memoria | La casilla de la tabla, los campos heredados primero y por qué eso hace funcionar el subtipado |
| 22. `new`, `$init` y el constructor | Los cuatro pasos y por qué `$init` va separado |
| 23. Métodos y despacho | `this` como parámetro escondido, la tabla de métodos y la llamada indirecta, con el ejemplo `Animal`/`Perro` |
| 24. Listas | La disposición con el largo, la fórmula del desplazamiento, el `foreach` y sus locales ocultas |
| 25. Chequeos en ejecución | División, `null` y rango, con su forma en línea |
| 26. Montículo y recolección de basura | Basura contra fuga, conteo de referencias contra rastreo, por qué esta etapa no libera memoria (decisión 48) y por qué el diseño deja posible un recolector por rastreo |
| Supuestos | Se agregan: las listas tienen tamaño fijo; los strings son referencias de 4 bytes, los literales viven en datos estáticos y un `concat` pide su bloque en el montículo (decisión 47) |

### Aceptación

- Los ejemplos son salida real del generador.
- La sección 23 muestra el mismo `a.hablar()` con `a` conteniendo un `Animal` y un
  `Perro`, y cómo la tabla elige.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 5.1 | El tamaño de cada clase, el desplazamiento de cada campo y su tabla de métodos |
| 5.2 | `alloc`, la llamada indirecta y las tablas de métodos en el TAC |
| 5.3 | `new`, `$init`, constructores, campos, `this` y métodos con despacho |
| 5.4 | Listas, su acceso por índice y el `foreach` |
| 5.5 | Los chequeos de `null` y de rango |
| 5.6 | Las secciones 21 a 26 del documento |
