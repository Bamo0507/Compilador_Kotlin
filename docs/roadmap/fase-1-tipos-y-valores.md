# Fase 1 · Tipos y valores

**Objetivo:** definir los 11 tipos, los 8 valores, la conversión entre texto y
valor, y las reglas de compatibilidad. Sin lógica de SQL: solo datos y funciones
puras.

**Por qué va primero y sola:** todo lo demás lee estas estructuras. El catálogo
guarda `Type`, la gramática nombra los tipos, el semántico los compara, el motor
opera con `Value` y el almacenamiento los convierte a texto. Si cambian a mitad de
camino, hay que rehacer trabajo en cuatro frentes al mismo tiempo.

**Lo que hace única a esta fase:** cero ANTLR, cero archivos, cero interfaz. Se
prueba con tests puros y es la única fase que se puede terminar sin que exista
nada más.

**Estimación:** una o dos sesiones.

---

## El cambio de fondo respecto a Compiscript

En Compiscript los cuatro tipos eran `data object`: una instancia de cada uno, y
compararlos era comparar referencias. Aquí **tres tipos llevan parámetros**, así
que dejan de ser objetos únicos y pasan a ser `data class`. Eso obliga a que la
comparación sea estructural, que es lo que `data class` da gratis.

---

## Ticket 1.1 · `Type`

- **Estado**: pendiente
- **Depende de**: 0.6

**Archivos:**

- `types/Type.kt` (NUEVO, reemplaza `frontend/semantic/symbols/Type.kt`)
- `app/src/test/.../TypeTest.kt` (NUEVO)

### Diseño

```kotlin
sealed interface Type {
    val name: String
}

// Numericos ---------------------------------------------------------------

data object IntType : Type {
    override val name = "INT"
}

// IEEE-754. Inexacto: 0.1 + 0.2 no da 0.3.
data object FloatType : Type {
    override val name = "FLOAT"
}

// Exacto. DECIMAL y NUMERIC son el mismo tipo con dos nombres, como en SQL.
data class DecimalType(val precision: Int, val scale: Int) : Type {
    override val name = "DECIMAL($precision,$scale)"
}

// Caracter ----------------------------------------------------------------

// Largo fijo: se rellena con espacios al leer del CSV.
data class CharType(val length: Int) : Type {
    override val name = "CHAR($length)"
}

// Largo variable con tope.
data class VarcharType(val maxLength: Int) : Type {
    override val name = "VARCHAR($maxLength)"
}

data object TextType : Type {
    override val name = "TEXT"
}

// Temporales --------------------------------------------------------------

data object DateType : Type {
    override val name = "DATE"
}

data object TimeType : Type {
    override val name = "TIME"
}

// Resto -------------------------------------------------------------------

data object BooleanType : Type {
    override val name = "BOOLEAN"
}

// El tipo del literal NULL. Compatible con todos.
data object NullType : Type {
    override val name = "NULL"
}

// Se devuelve cuando ya se reporto un error. Corta cascadas.
data object ErrorType : Type {
    override val name = "<error>"
}
```

### Decisión · familias como función, no como jerarquía

Se podría hacer `sealed interface Numerico : Type` y colgar los tres numéricos de
ahí. No se hace, porque un tipo pertenece a una familia **para ciertas
operaciones**, y meter eso en la jerarquía obliga a decidir ahora todas las
agrupaciones que vayan a hacer falta. La familia se pregunta con una función en
`TypeRules` (ticket 1.4), que es donde viven las reglas.

Es el principio 8 del README: los modelos son datos, las reglas son funciones
aparte.

### Decisión · `NUMERIC` no tiene tipo propio

El lexer acepta las dos palabras y el AST guarda `DecimalType`. Si tuvieran tipos
distintos, `DECIMAL(10,2)` y `NUMERIC(10,2)` no serían compatibles entre sí, que
es justo lo contrario de lo que dice el estándar.

**Aceptación:**

- `DecimalType(10, 2) == DecimalType(10, 2)` da `true`
- `CharType(5) == VarcharType(5)` da `false`
- un `when` sobre `Type` sin `else` compila y cubre los 11
- `DecimalType(10, 2).name` da `"DECIMAL(10,2)"`

---

## Ticket 1.2 · `Value`

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `types/Value.kt` (MODIFICA el `RuntimeValue.kt` recortado en 0.3)
- `app/src/test/.../ValorTest.kt` (NUEVO)

### Diseño

```kotlin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// Un valor concreto en una celda.
sealed interface Value {
    // Como se muestra en la rejilla de resultados.
    fun display(): String
}

data class IntValue(val value: Long) : Value {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : Value {
    override fun display() = value.toString()
}

// NO es data class: ver la decision de abajo.
class DecimalValue(val value: BigDecimal) : Value {
    override fun display() = value.toPlainString()
    override fun equals(other: Any?) =
        other is DecimalValue && value.compareTo(other.value) == 0
    override fun hashCode() = value.stripTrailingZeros().hashCode()
}

// Sirve a CHAR, VARCHAR y TEXT: lo que los diferencia es la regla al escribir.
data class StringValue(val value: String) : Value {
    override fun display() = value
}

data class DateValue(val value: LocalDate) : Value {
    override fun display() = value.toString()
}

data class TimeValue(val value: LocalTime) : Value {
    override fun display() = value.toString()
}

data class BoolValue(val value: Boolean) : Value {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : Value {
    override fun display() = "NULL"
}
```

**Por qué ocho valores para once tipos:** los tres tipos de carácter comparten
`StringValue`. El tipo dice qué reglas se aplican al escribir; el valor solo
guarda el texto.

### Decisión · `BigDecimal` y no `Double` para `DECIMAL`

| | `FLOAT` | `DECIMAL(10,2)` |
|---|---|---|
| Kotlin | `Double` | `BigDecimal` |
| `0.1 + 0.2` | `0.30000000000000004` | `0.30` |
| Para qué | medidas, promedios | dinero |

Usar `Double` para ambos haría que `DECIMAL` mintiera sobre lo que promete.

### Decisión · `DecimalValue` no es `data class`

`BigDecimal("1.0").equals(BigDecimal("1.00"))` da `false`, porque mira la escala.
En SQL son el mismo número. Si `DecimalValue` fuera `data class`, heredaría esa
igualdad y con ella tres errores silenciosos:

- `DISTINCT` devolvería `1.0` y `1.00` como dos filas
- `GROUP BY` sobre un `DECIMAL` abriría dos grupos para el mismo valor
- una **clave primaria `DECIMAL` aceptaría un duplicado**, porque la revisión de
  unicidad usa un conjunto

Así que define `equals` con `compareTo` y `hashCode` sobre
`value.stripTrailingZeros()`, que es la forma canónica de los que `compareTo`
considera iguales. Sin lo segundo, lo primero no sirve: un `HashSet` se guía por
el hash antes que por la igualdad.

**Aceptación:**

- `BigDecimal("0.1") + BigDecimal("0.2")` da exactamente `0.3`, y `0.1 + 0.2` en
  `Double` no
- `DecimalValue(1.0) == DecimalValue(1.00)`, **y sus hash coinciden**
- `setOf(DecimalValue(1.0), DecimalValue(1.00))` tiene un solo elemento
- `DecimalValue(BigDecimal("1000.00")).display()` da `"1000.00"` y no `1E+3`
- `DateValue(LocalDate.of(2026, 9, 23)).display()` da `"2026-09-23"`
- ordenar fechas ISO-8601 como texto da el orden cronológico
- un `when` sobre `Value` sin `else` compila

---

## Ticket 1.3 · Conversión entre texto y valor

- **Estado**: pendiente
- **Depende de**: 1.2

**Archivos:**

- `types/ValueCodec.kt` (NUEVO)
- `app/src/test/.../ValorCodecTest.kt` (NUEVO)

**Qué es esto, en simple:** el CSV guarda texto. Cuando el motor lee `1250.00` de
un archivo, necesita saber si eso es un `FLOAT`, un `DECIMAL` o una cadena. La
respuesta la da el tipo de la columna, que viene del JSON. Este archivo es la
traducción en los dos sentidos.

### Diseño

```kotlin
object ValueCodec {

    // Texto del CSV -> Value, guiado por el tipo de la columna.
    // Devuelve null si el texto no es valido para ese tipo.
    fun decode(text: String?, type: Type): Value? = when {
        text == null -> NullValue
        else -> when (type) {
            IntType -> text.toLongOrNull()?.let { IntValue(it) }
            FloatType -> text.toDoubleOrNull()?.let { FloatValue(it) }
            is DecimalType -> decimal(text, type)
            is CharType -> StringValue(text.padEnd(type.length))
            is VarcharType -> if (text.length <= type.maxLength) StringValue(text) else null
            TextType -> StringValue(text)
            DateType -> runCatching { DateValue(LocalDate.parse(text)) }.getOrNull()
            TimeType -> runCatching { TimeValue(LocalTime.parse(text)) }.getOrNull()
            BooleanType -> when (text) {
                "true" -> BoolValue(true)
                "false" -> BoolValue(false)
                else -> null
            }
            NullType, ErrorType -> null
        }
    }

    // Value -> texto del CSV. NULL sale como null, que el escritor deja vacio.
    fun encode(value: Value, type: Type): String? = when (value) {
        NullValue -> null
        // CHAR se guarda SIN relleno: el largo lo dice el JSON. Decision 9.
        is StringValue -> if (type is CharType) value.value.trimEnd() else value.value
        else -> value.display()
    }
}
```

**El relleno de `CHAR` va en los dos sentidos:** `decode` rellena hasta
`length`, `encode` recorta al final. En semántica `CHAR`, un valor y ese mismo
valor con espacios al final son el mismo valor, así que no se pierde información y
el CSV queda sin espacios invisibles.

**ISO-8601 para `DATE` y `TIME`** porque se lee a ojo y porque **ordena
alfabéticamente igual que cronológicamente**, así que un `ORDER BY fecha` no
necesita nada especial. `LocalDate.parse` y `LocalTime.parse` ya usan ese formato
por defecto.

**Aceptación:**

- ida y vuelta para los ocho valores: `decodificar(codificar(v, t), t) == v`
- `decodificar("abcdefgh", VarcharType(5))` devuelve `null`
- `decodificar("ab", CharType(5))` devuelve `StringValue("ab   ")`
- `codificar(StringValue("ab   "), CharType(5))` devuelve `"ab"`
- `decodificar("2026-13-45", DateType)` devuelve `null` y no lanza

---

## Ticket 1.4 · `TypeRules`

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `types/TypeRules.kt` (NUEVO)
- `app/src/test/.../TypeRulesTest.kt` (NUEVO)

**Qué es esto, en simple:** las reglas que dicen qué se puede comparar con qué,
qué se puede sumar con qué, y qué tipo sale del resultado. Es el archivo que se
consulta en la defensa cuando preguntan por qué `DATE > TIME` no compila.

### Las familias

| Family | Miembros | Entre sí | Con otra familia |
|---|---|---|---|
| numérica | `INT`, `DECIMAL`, `FLOAT` | comparan y operan, ensanchando | no |
| carácter | `CHAR`, `VARCHAR`, `TEXT` | comparan y concatenan | no |
| temporal | `DATE`, `TIME` | **no entre sí** | no |
| lógica | `BOOLEAN` | | no |
| `NULL` | | compatible con todas | |

Que `DATE` y `TIME` no se comparen entre ellas es a propósito: son cosas
distintas, y dejarlo pasar solo esconde errores.

### La torre numérica

```
INT  ->  DECIMAL  ->  FLOAT
```

El ensanchamiento va en un solo sentido. Un `INT` se usa donde se espera un
`DECIMAL`; al revés no, porque perdería la parte fraccionaria en silencio.

### Diseño

```kotlin
object TypeRules {

    enum class Family { NUMERIC, CHARACTER, TEMPORAL, LOGICAL, NULL, ERROR }

    fun familyOf(type: Type): Family = when (type) {
        IntType, FloatType, is DecimalType -> Family.NUMERIC
        is CharType, is VarcharType, TextType -> Family.CHARACTER
        DateType, TimeType -> Family.TEMPORAL
        BooleanType -> Family.LOGICAL
        NullType -> Family.NULL
        ErrorType -> Family.ERROR
    }

    // Se puede guardar un valor de `source` en una columna de `target`?
    fun isAssignable(source: Type, target: Type): Boolean = when {
        source == ErrorType || target == ErrorType -> true    // corta cascadas
        source == NullType -> true
        familyOf(source) != familyOf(target) -> false
        familyOf(source) == Family.TEMPORAL -> source == target
        familyOf(source) == Family.NUMERIC -> widthOf(source) <= widthOf(target)
        else -> true    // caracter: el largo se valida aparte, al escribir
    }

    // El tipo del resultado de comparar u operar dos tipos.
    fun unify(izq: Type, der: Type): Type? = when {
        izq == ErrorType || der == ErrorType -> ErrorType
        izq == NullType -> der
        der == NullType -> izq
        familyOf(izq) != familyOf(der) -> null
        familyOf(izq) == Family.NUMERIC -> if (widthOf(izq) >= widthOf(der)) izq else der
        familyOf(izq) == Family.TEMPORAL -> if (izq == der) izq else null
        familyOf(izq) == Family.CHARACTER -> TextType
        else -> izq
    }

    private fun widthOf(type: Type): Int = when (type) {
        IntType -> 0
        is DecimalType -> 1
        FloatType -> 2
        else -> -1
    }
}
```

### Decisión · `isAssignable` es direccional, `unify` es simétrica

Son dos preguntas distintas y confundirlas es el error clásico:

- `isAssignable(IntType, FloatType)` es `true`, al revés es `false`. Es la pregunta
  del `INSERT`: cabe este valor en esta columna.
- `unificar(IntType, FloatType)` es `FloatType` en cualquier orden. Es la pregunta
  del operador: de qué tipo es `a + b`.

### Decisión · la aritmética de `DECIMAL` sigue el estándar

```kotlin
// multiplicacion
DecimalType(p1 + p2 + 1, s1 + s2)

// suma y resta
val scale = max(s1, s2)
DecimalType(max(p1 - s1, p2 - s2) + scale + 1, scale)

// division: el estandar la deja sin definir, ver abajo
DecimalType(p1 + s2 + 6, max(s1, s2) + 6)
```

Lo que decide es que la alternativa simple, quedarse con la escala mayor,
**pierde datos**:

```sql
SELECT 0.05 * 0.05;

escala mayor:  DECIMAL(10,2)  ->  0.00     <- el dato desaparece
estandar:      DECIMAL(21,4)  ->  0.0025
```

Multiplicar dos números de dos decimales necesita cuatro. Redondear en cada paso
intermedio es el error clásico de contabilidad: lo correcto es arrastrar la
precisión y redondear una sola vez al final.

El argumento en contra del estándar es que la precisión crece sola en una cadena
de multiplicaciones. Eso importa en SQL Server o en Oracle, donde el tope son 38
dígitos y el motor tiene que recortar la escala. **Aquí no hay tope:**
`BigDecimal` es de precisión arbitraria, y un tipo intermedio como
`DECIMAL(32,6)` nunca se guarda, solo existe mientras se evalúa la expresión.

El desbordamiento se atrapa al escribir. `precio * cantidad` da `DECIMAL(21,4)` y
puede terminar en una columna `DECIMAL(10,2)`: a nivel de tipos se permite, y
`ValueCodec.decode` rechaza el valor concreto si la parte entera no cabe. Es la
misma regla que `VARCHAR`, un mecanismo menos que explicar.

### Decisión · la división lleva 6 decimales de más

El estándar deja la división sin definir a propósito, porque `1/3` no termina.

```sql
SELECT 10.00 / 3.00;   -- 3.333333
```

La escala del resultado es `max(s1, s2) + 6`, con redondeo `HALF_UP`, que es lo
que hace SQL Server con su escala mínima. Va al README como el único lugar donde
el estándar no dicta la respuesta.

`AVG` no entra en esto: el ticket 5.3 ya decidió que siempre devuelve `FLOAT`.

**Aceptación:** un test por celda de la tabla de familias, más

- `isAssignable(IntType, DecimalType(10,2))` es `true`
- `isAssignable(FloatType, IntType)` es `false`
- `isAssignable(DateType, TimeType)` es `false`
- `isAssignable(DecimalType(21,4), DecimalType(10,2))` es `true`, porque la escala
  se revisa al escribir y no al asignar
- `unify(CharType(5), TextType)` es `TextType`
- `unify(DecimalType(10,2), DecimalType(8,4))` es `DecimalType(12,4)`
- `unify(DateType, IntType)` es `null`
- `unify` da lo mismo en cualquier orden, e `isAssignable` no
- cualquier cosa con `ErrorType` no genera error nuevo

---

## Ticket 1.5 · Reglas de los operadores

- **Estado**: pendiente
- **Depende de**: 1.4

**Archivos:**

- `types/TypeRules.kt` (MODIFICA)
- `app/src/test/.../TypeRulesTest.kt` (MODIFICA)

### La tabla que hay que implementar

| Operador | Permitido en | Tipo del resultado |
|---|---|---|
| `+` `-` `*` `/` | numéricos | `unify` de los dos |
| `\|\|` | carácter | `TEXT` |
| `<` `>` `<=` `>=` | numéricos, carácter, temporales | `BOOLEAN` |
| `=` `<>` | misma familia | `BOOLEAN` |
| `AND` `OR` `NOT` | `BOOLEAN` | `BOOLEAN` |
| `IS NULL` `IS NOT NULL` | cualquiera | `BOOLEAN` |

### Diseño

```kotlin
fun binaryResultType(operator: BinaryOperator, izq: Type, der: Type): Type? =
    when (operator.categoria) {
        ARITMETICA -> unify(izq, der)?.takeIf { familyOf(it) == Family.NUMERIC }
        CONCATENACION -> TextType.takeIf {
            familyOf(izq) == Family.CHARACTER && familyOf(der) == Family.CHARACTER
        }
        ORDEN -> BooleanType.takeIf {
            unify(izq, der) != null && familyOf(izq) != Family.LOGICAL
        }
        IGUALDAD -> BooleanType.takeIf { unify(izq, der) != null }
        LOGICAL -> BooleanType.takeIf { izq == BooleanType && der == BooleanType }
    }
```

**Por qué `ORDER` excluye `BOOLEAN`:** preguntar si `true > false` no significa
nada en SQL, y permitirlo solo deja pasar comparaciones escritas por error. La
igualdad sí los acepta.

**`FLOAT` contagia.** Cualquier operación donde aparezca da `FLOAT`, porque no se
puede prometer exactitud sobre un operando que ya la perdió.

**`INT / INT` trunca.** `10 / 3` da `3`, como en PostgreSQL y SQL Server.
Sorprende, pero desviarse del estándar sorprendería más. Va al README junto a las
demás simplificaciones.

**Un `INT` entra a la aritmética decimal como `DECIMAL(19,0)`**, que son los
dígitos que caben en un `Long`. Así `INT + DECIMAL(10,2)` da `DECIMAL(22,2)`.

**Por qué `IS NULL` acepta cualquier tipo:** es la única forma de preguntar por un
nulo, porque `= NULL` en SQL da `NULL`, no `true`. Esa es la lógica de tres
valores, y aquí se simplifica: `IS NULL` es el operador correcto y `= NULL` se
reporta como advertencia en la fase 5.

**Aceptación:**

- `binaryResultType(SUMA, IntType, DecimalType(10,2))` da `DecimalType(10,2)`
- `binaryResultType(SUMA, DateType, IntType)` da `null`
- `binaryResultType(MAYOR, BooleanType, BooleanType)` da `null`
- `binaryResultType(CONCAT, CharType(3), TextType)` da `TextType`
