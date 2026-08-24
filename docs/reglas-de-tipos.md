# Reglas de tipos de Compiscript

La regla de inferencia y su función son el mismo texto en dos formatos, siguiendo la
notación de Cardelli (*Type Systems*):

```
Γ ⊢ M : integer      Γ ⊢ N : integer
────────────────────────────────────
        Γ ⊢ (M − N) : integer
```

- Lo de **arriba de la línea** son las **premisas**: en el código, las llamadas
  recursivas a los hijos.
- Lo de **abajo** es la **conclusión**: en el código, el `return`.
- **Γ** (el contexto) es `currentScope`.

Cada función pública de `frontend/semantic/TypeRules.kt` lleva su regla en el
comentario de encima, y cada fila de esta tabla apunta al test de
`app/src/test/kotlin/org/compiler/TypeRulesTest.kt` que la fija. Así la trazabilidad
teoría → código → test es una sola línea de lectura.

## Operadores

| Regla (notación de Cardelli) | Función | Test |
|---|---|---|
| A1 — aritmética entre enteros da `integer` | `TypeRules.arithmetic` | `arithmetic entre integers da integer` |
| A2 — aritmética con ensanchamiento: si un lado es `float`, el resultado es `float` | `TypeRules.arithmetic` | `arithmetic con un float ensancha a float` |
| A3 — módulo solo entre enteros (decisión documentada: `%` no aplica a `float`) | `TypeRules.arithmetic` | `modulo con float es error` |
| C1 — concatenación: `+` con `string` en AMBOS lados da `string` | `TypeRules.concatenation` | `concatenar dos strings da string` |
| C2 — sin conversión implícita: `string + integer` es error, no concatenación | `TypeRules.concatenation` | `concatenar string con integer es error` |
| L1 — lógicos: `&&` y `\|\|` exigen `boolean` en ambos lados y dan `boolean` | `TypeRules.logical` | `logico entre booleans da boolean` |
| L1 (negativa) — un operando no booleano es error | `TypeRules.logical` | `logico con integer es error` |
| R1 — relacionales: numéricos entre sí, o `string` con `string`, dan `boolean` | `TypeRules.relational` | `relacional entre numericos da boolean` |
| R1 (strings) — `string < string` es válido | `TypeRules.relational` | `relacional entre strings da boolean` |
| R1 (negativa) — `boolean < boolean` es error | `TypeRules.relational` | `relacional entre booleans es error` |
| E1 — igualdad: comparables si `unify` encuentra tipo común, da `boolean` | `TypeRules.equality` | `igualdad entre comparables da boolean` |
| E1 (null) — una clase se puede comparar con `null` | `TypeRules.equality` | `igualdad de clase con null da boolean` |
| E1 (negativa) — `string == boolean` es error | `TypeRules.equality` | `igualdad entre string y boolean es error` |
| L2 — negación lógica: `!M` exige `boolean` | `TypeRules.unary` | `not exige boolean` |
| N1 — negación aritmética: `−M` preserva el tipo numérico | `TypeRules.unary` | `negate preserva el tipo numerico` |

## Asignabilidad

`isAssignable(target, source)`: ¿se puede guardar un valor de tipo `source` en algo
declarado `target`? La usan asignaciones, argumentos, retornos e inicializadores.

| Regla | Función | Test |
|---|---|---|
| S1 — ensanchamiento: `integer` cabe en `float`; al revés NO (habría pérdida de precisión y el lenguaje no tiene casts) | `TypeRules.isAssignable` | `integer cabe en float pero no al reves` |
| S2 — subtipado nominal: `Perro` cabe donde se pide `Animal`; al revés NO | `TypeRules.isAssignable` | `una subclase cabe en su superclase pero no al reves` |
| S3 — `null` solo cabe en clases y arreglos, no en los tipos simples | `TypeRules.isAssignable` | `null cabe en clases y arreglos pero no en primitivos` |
| S4 — el arreglo vacío `[]` llega como `ArrayType(NullType)` y toma su tipo del contexto | `TypeRules.isAssignable` | `el arreglo vacio encaja con cualquier arreglo` |
| S5 — los arreglos NO son covariantes: `integer[]` no cabe en `float[]` (evita el agujero de Java, `ArrayStoreException`) | `TypeRules.isAssignable` | `los arreglos no son covariantes` |
| S6 — `ErrorType` se acepta en cualquier dirección: un error ya reportado no genera cascada | `TypeRules.isAssignable` | `ErrorType corta cascadas` |

## Unificación

`unify(left, right)`: el tipo común de dos ramas, o `null` si no existe — eso es un
error. La usan el ternario, el literal de arreglo y la igualdad.

| Regla | Función | Test |
|---|---|---|
| U1 — dos numéricos unifican al más ancho: `integer ∪ float = float` | `TypeRules.unify` | `unify de numericos ensancha` |
| U2 — recursiva en arreglos: `integer[] ∪ float[] = float[]` (el caso `[[1, 2], [3.5, 4.0]]`) | `TypeRules.unify` | `unify recursa en arreglos` |
| U3 — dos clases unifican en su ancestro común más cercano | `TypeRules.unify` | `unify de clases hermanas da el ancestro comun` |
| U4 — sin tipo común no hay unificación: `string ∪ boolean = null` | `TypeRules.unify` | `unify sin tipo comun devuelve null` |
| U5 — `null` unifica con clases y arreglos al tipo no nulo | `TypeRules.unify` | `unify de null con clase da la clase` |

## Ayudantes

| Regla | Función | Test |
|---|---|---|
| W1 — el más ancho de dos numéricos: `float` si alguno lo es, si no `integer` | `TypeRules.widen` | `widen elige el mas ancho` |
| W2 — precondición verificada: `widen` fuera de numéricos LANZA, no devuelve `IntegerType` en silencio | `TypeRules.widen` | `widen fuera de numericos lanza IllegalArgumentException` |
| — numérico es `integer` o `float`, nada más | `TypeRules.isNumeric` | `isNumeric acepta integer y float y nada mas` |

## Decisiones documentadas

**A3 — `%` solo entre enteros.** El módulo de flotantes tiene semántica ambigua
(¿resto IEEE o matemático?) y ningún uso en los programas del curso. Se rechaza.

**C2 — sin concatenación implícita.** `"texto" + 5` es error y no `"texto5"`: el
lenguaje no tiene conversión automática a string, y aceptarla escondería errores de
tipo.

**S5 — los arreglos no son covariantes.** Si `integer[]` cupiera en `float[]`, esto
sería legal:

```cps
let enteros: integer[] = [1, 2];
let vista: float[] = enteros;      // si los arreglos fueran covariantes
vista[0] = 1.5;                    // y ahora enteros[0] contiene un float
```

Es el agujero de los arreglos covariantes de Java, que ahí explota en ejecución con
`ArrayStoreException`. Compiscript lo rechaza en compilación: el arreglo se escribe
con el tipo que se quiere, `[1.0, 2.0]`.

**S6/`ErrorType` — cortar cascadas.** `(1 + "a") * 2` reporta UN error, no dos: la
suma inválida produce `ErrorType`, y toda operación que reciba `ErrorType` se acepta
en silencio devolviendo `ErrorType`. El usuario ve el error real, no sus ecos.
