# Fase 1 · Tipos y valores

**Objetivo:** definir los 11 tipos, los 8 valores, la conversión entre texto y
valor, y las reglas de compatibilidad. Sin lógica de SQL: solo datos y funciones
puras.

**Por qué va primero y sola:** todo lo demás lee estas estructuras. El catálogo
guarda `Type`, la gramática nombra los tipos, el semántico los compara, el motor
opera con `Valor` y el almacenamiento los convierte a texto. Si cambian a mitad de
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

## Ticket 1.2 · `Valor`

- **Estado**: pendiente
- **Depende de**: 1.1

**Archivos:**

- `types/Valor.kt` (MODIFICA el `RuntimeValue.kt` recortado en 0.3)
- `app/src/test/.../ValorTest.kt` (NUEVO)

### Diseño

```kotlin
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// Un valor concreto en una celda.
sealed interface Valor {
    // Como se muestra en la rejilla de resultados.
    fun display(): String
}

data class IntValue(val value: Long) : Valor {
    override fun display() = value.toString()
}

data class FloatValue(val value: Double) : Valor {
    override fun display() = value.toString()
}

data class DecimalValue(val value: BigDecimal) : Valor {
    override fun display() = value.toPlainString()
}

// Sirve a CHAR, VARCHAR y TEXT: lo que los diferencia es la regla al escribir.
data class StringValue(val value: String) : Valor {
    override fun display() = value
}

data class DateValue(val value: LocalDate) : Valor {
    override fun display() = value.toString()
}

data class TimeValue(val value: LocalTime) : Valor {
    override fun display() = value.toString()
}

data class BoolValue(val value: Boolean) : Valor {
    override fun display() = if (value) "true" else "false"
}

data object NullValue : Valor {
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

Usar `Double` para ambos haría que `DECIMAL` mintiera sobre lo que promete. El
costo es que `BigDecimal` se compara con `compareTo` y no con `equals`, porque
`BigDecimal("1.0").equals(BigDecimal("1.00"))` da `false` aunque sean el mismo
número. Eso se maneja en `TypeRules` y hay que cubrirlo con test.

**Aceptación:**

- `DecimalValue(BigDecimal("0.1")).value + BigDecimal("0.2")` da exactamente `0.3`
- `DateValue(LocalDate.of(2026, 9, 23)).display()` da `"2026-09-23"`
- un `when` sobre `Valor` sin `else` compila

---

## Ticket 1.3 · Conversión entre texto y valor

- **Estado**: pendiente
- **Depende de**: 1.2

**Archivos:**

- `types/ValorCodec.kt` (NUEVO)
- `app/src/test/.../ValorCodecTest.kt` (NUEVO)

**Qué es esto, en simple:** el CSV guarda texto. Cuando el motor lee `1250.00` de
un archivo, necesita saber si eso es un `FLOAT`, un `DECIMAL` o una cadena. La
respuesta la da el tipo de la columna, que viene del JSON. Este archivo es la
traducción en los dos sentidos.

### Diseño

```kotlin
object ValorCodec {

    // Texto del CSV -> Valor, guiado por el tipo de la columna.
    // Devuelve null si el texto no es valido para ese tipo.
    fun decodificar(texto: String?, tipo: Type): Valor? = when {
        texto == null -> NullValue
        else -> when (tipo) {
            IntType -> texto.toLongOrNull()?.let { IntValue(it) }
            FloatType -> texto.toDoubleOrNull()?.let { FloatValue(it) }
            is DecimalType -> decimal(texto, tipo)
            is CharType -> StringValue(texto.padEnd(tipo.length))
            is VarcharType -> if (texto.length <= tipo.maxLength) StringValue(texto) else null
            TextType -> StringValue(texto)
            DateType -> runCatching { DateValue(LocalDate.parse(texto)) }.getOrNull()
            TimeType -> runCatching { TimeValue(LocalTime.parse(texto)) }.getOrNull()
            BooleanType -> when (texto) {
                "true" -> BoolValue(true)
                "false" -> BoolValue(false)
                else -> null
            }
            NullType, ErrorType -> null
        }
    }

    // Valor -> texto del CSV. NULL sale como null, que el escritor deja vacio.
    fun codificar(valor: Valor, tipo: Type): String? = when (valor) {
        NullValue -> null
        // CHAR se guarda SIN relleno: el largo lo dice el JSON. Decision 9.
        is StringValue -> if (tipo is CharType) valor.value.trimEnd() else valor.value
        else -> valor.display()
    }
}
```

**El relleno de `CHAR` va en los dos sentidos:** `decodificar` rellena hasta
`length`, `codificar` recorta al final. En semántica `CHAR`, un valor y ese mismo
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

| Familia | Miembros | Entre sí | Con otra familia |
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

    enum class Familia { NUMERICA, CARACTER, TEMPORAL, LOGICA, NULA, ERROR }

    fun familiaDe(tipo: Type): Familia = when (tipo) {
        IntType, FloatType, is DecimalType -> Familia.NUMERICA
        is CharType, is VarcharType, TextType -> Familia.CARACTER
        DateType, TimeType -> Familia.TEMPORAL
        BooleanType -> Familia.LOGICA
        NullType -> Familia.NULA
        ErrorType -> Familia.ERROR
    }

    // Se puede guardar un valor de `origen` en una columna de `destino`?
    fun esAsignable(origen: Type, destino: Type): Boolean = when {
        origen == ErrorType || destino == ErrorType -> true    // corta cascadas
        origen == NullType -> true
        familiaDe(origen) != familiaDe(destino) -> false
        familiaDe(origen) == Familia.TEMPORAL -> origen == destino
        familiaDe(origen) == Familia.NUMERICA -> anchoDe(origen) <= anchoDe(destino)
        else -> true    // caracter: el largo se valida aparte, al escribir
    }

    // El tipo del resultado de comparar u operar dos tipos.
    fun unificar(izq: Type, der: Type): Type? = when {
        izq == ErrorType || der == ErrorType -> ErrorType
        izq == NullType -> der
        der == NullType -> izq
        familiaDe(izq) != familiaDe(der) -> null
        familiaDe(izq) == Familia.NUMERICA -> if (anchoDe(izq) >= anchoDe(der)) izq else der
        familiaDe(izq) == Familia.TEMPORAL -> if (izq == der) izq else null
        familiaDe(izq) == Familia.CARACTER -> TextType
        else -> izq
    }

    private fun anchoDe(tipo: Type): Int = when (tipo) {
        IntType -> 0
        is DecimalType -> 1
        FloatType -> 2
        else -> -1
    }
}
```

### Decisión · `esAsignable` es direccional, `unificar` es simétrica

Son dos preguntas distintas y confundirlas es el error clásico:

- `esAsignable(IntType, FloatType)` es `true`, al revés es `false`. Es la pregunta
  del `INSERT`: cabe este valor en esta columna.
- `unificar(IntType, FloatType)` es `FloatType` en cualquier orden. Es la pregunta
  del operador: de qué tipo es `a + b`.

### Decisión pendiente · la escala de `DECIMAL` al multiplicar

El estándar dice que `DECIMAL(10,2) * DECIMAL(10,2)` da `DECIMAL(20,4)`: las
precisiones se suman y las escalas también. La alternativa simple es quedarse con
la escala mayor de las dos.

**Recomendación: la escala mayor.** La regla del estándar hace que la precisión
crezca sola en una cadena de multiplicaciones y obliga a decidir qué pasa al pasar
del tope, que es una regla más para una ganancia que en este proyecto nadie va a
notar. Anotarlo en el README como simplificación consciente.

**Aceptación:** un test por celda de la tabla de familias, más

- `esAsignable(IntType, DecimalType(10,2))` es `true`
- `esAsignable(FloatType, IntType)` es `false`
- `esAsignable(DateType, TimeType)` es `false`
- `unificar(CharType(5), TextType)` es `TextType`
- `unificar(DateType, IntType)` es `null`
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
| `+` `-` `*` `/` | numéricos | `unificar` de los dos |
| `\|\|` | carácter | `TEXT` |
| `<` `>` `<=` `>=` | numéricos, carácter, temporales | `BOOLEAN` |
| `=` `<>` | misma familia | `BOOLEAN` |
| `AND` `OR` `NOT` | `BOOLEAN` | `BOOLEAN` |
| `IS NULL` `IS NOT NULL` | cualquiera | `BOOLEAN` |

### Diseño

```kotlin
fun tipoDeBinaria(operador: OperadorBinario, izq: Type, der: Type): Type? =
    when (operador.categoria) {
        ARITMETICA -> unificar(izq, der)?.takeIf { familiaDe(it) == Familia.NUMERICA }
        CONCATENACION -> TextType.takeIf {
            familiaDe(izq) == Familia.CARACTER && familiaDe(der) == Familia.CARACTER
        }
        ORDEN -> BooleanType.takeIf {
            unificar(izq, der) != null && familiaDe(izq) != Familia.LOGICA
        }
        IGUALDAD -> BooleanType.takeIf { unificar(izq, der) != null }
        LOGICA -> BooleanType.takeIf { izq == BooleanType && der == BooleanType }
    }
```

**Por qué `ORDEN` excluye `BOOLEAN`:** preguntar si `true > false` no significa
nada en SQL, y permitirlo solo deja pasar comparaciones escritas por error.

**Por qué `IS NULL` acepta cualquier tipo:** es la única forma de preguntar por un
nulo, porque `= NULL` en SQL da `NULL`, no `true`. Esa es la lógica de tres
valores, y aquí se simplifica: `IS NULL` es el operador correcto y `= NULL` se
reporta como advertencia en la fase 5.

**Aceptación:**

- `tipoDeBinaria(SUMA, IntType, DecimalType(10,2))` da `DecimalType(10,2)`
- `tipoDeBinaria(SUMA, DateType, IntType)` da `null`
- `tipoDeBinaria(MAYOR, BooleanType, BooleanType)` da `null`
- `tipoDeBinaria(CONCAT, CharType(3), TextType)` da `TextType`
