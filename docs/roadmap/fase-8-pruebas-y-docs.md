# Fase 8 — Batería de pruebas y documentación

> **⚠ Esta fase queda PENDIENTE DE REVISIÓN hasta que el código exista.**
>
> A diferencia de las fases 0 a 7, que se revisaron ticket por ticket contra el
> diseño, esta se escribió antes de que hubiera implementación. Sus listas de
> verificación y su batería de casos hay que validarlas contra el compilador real, no
> contra el plan.
>
> Ya hay dos desalineaciones conocidas en el ticket 8.3, arrastradas de decisiones que
> cambiaron después:
>
> - pide verificar una **"Tabla de tipos"** y una columna de **"número de tipo"**, que
>   la decisión 13 eliminó;
> - dice que una variable capturada aparece *"como no liberable"*, y esa propiedad se
>   quitó del reporte de vivacidad (ticket 5.2).
>
> Se revisa completa al llegar aquí.

**Objetivo de la fase:** cerrar los dos entregables que no son código de compilador
pero **sí se califican**, y verificar que todo funcione junto.

El enunciado lo pide con nombre y apellido:

> - Batería de pruebas para cada regla semántica (casos exitosos y fallidos),
>   **presente y funcional al momento de la evaluación**.
> - Documentación de la arquitectura de la implementación y documentación de cómo
>   ejecutar el compilador.

Y bajo *Requisitos para Calificación*:

> Para poder optar a calificación, el programa debe funcionar correctamente el día
> de la presentación. El proyecto debe estar **correctamente documentado y
> organizado**.

**Estimación:** dos sesiones. Los tests unitarios ya se fueron escribiendo ticket por
ticket; aquí se agregan los de integración y se cierra la documentación.

---

## Ticket 8.1 — Batería de programas `.cps`

- **Estado**: pendiente
- **Depende de**: 7.1

**Archivos:**

- `app/src/test/resources/programas/validos/*.cps` (NUEVOS)
- `app/src/test/resources/programas/invalidos/*.cps` (NUEVOS)
- `app/src/test/kotlin/org/compiler/ProgramasValidosTest.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/ProgramasInvalidosTest.kt` (NUEVO)

**Qué es esto, en simple:** archivos `.cps` de verdad, uno por regla semántica, que
el compilador procesa entero. A diferencia de los tests unitarios de cada ticket
—que prueban una función—, estos prueban **el compilador completo** sobre un programa
real.

### Cómo se organizan

Un archivo por regla, con el nombre de la regla:

```
programas/
├── validos/
│   ├── tipos_aritmetica.cps
│   ├── tipos_logicos.cps
│   ├── tipos_comparaciones.cps
│   ├── tipos_asignacion.cps
│   ├── tipos_listas.cps
│   ├── ambito_anidado.cps
│   ├── ambito_shadowing.cps
│   ├── funciones_argumentos.cps
│   ├── funciones_recursion.cps
│   ├── funciones_closures.cps
│   ├── flujo_condiciones.cps
│   ├── flujo_break_continue.cps
│   ├── clases_constructor.cps
│   ├── clases_herencia.cps
│   ├── clases_this.cps
│   └── demo_completa.cps
└── invalidos/
    ├── tipo_asignacion_incompatible.cps
    ├── tipo_aritmetica_con_string.cps
    ├── tipo_logico_con_entero.cps
    ├── const_sin_inicializar.cps
    ├── const_reasignada.cps
    ├── variable_no_declarada.cps
    ├── variable_redeclarada.cps
    ├── funcion_redeclarada.cps
    ├── argumentos_cantidad.cps
    ├── argumentos_tipo.cps
    ├── retorno_incompatible.cps
    ├── retorno_falta_camino.cps
    ├── condicion_no_booleana.cps
    ├── break_fuera_de_bucle.cps
    ├── return_fuera_de_funcion.cps
    ├── codigo_muerto.cps
    ├── miembro_inexistente.cps
    ├── this_fuera_de_clase.cps
    ├── herencia_circular.cps
    ├── indice_no_entero.cps
    └── lista_elementos_incompatibles.cps
```

### El formato de los inválidos: el error esperado va en el archivo

Cada programa inválido declara en un comentario **qué error debe producir y en qué
línea**. Así el archivo es su propia especificación:

```cps
// ESPERADO: linea 3, "No se puede asignar 'string' a 'x', declarada como 'integer'"

let x: integer = 5;
x = "hola";
```

Y el test lee esa anotación:

```kotlin
class ProgramasInvalidosTest {

    @TestFactory
    fun `cada programa invalido produce el error esperado`(): List<DynamicTest> =
        programasEn("programas/invalidos").map { archivo ->
            DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                val esperado = leerAnotacionEsperada(archivo)
                val resultado = CompilerPipeline.compile(archivo.readText())

                assertTrue(resultado.hasErrors,
                    "Se esperaba al menos un error en ${archivo.name}")

                val coincide = resultado.errors.any { error ->
                    error.location.line == esperado.linea &&
                        error.message.contains(esperado.fragmento)
                }

                assertTrue(coincide,
                    "En ${archivo.name} se esperaba en la línea ${esperado.linea} " +
                    "un error con '${esperado.fragmento}'.\n" +
                    "Errores obtenidos:\n" +
                    resultado.errors.joinToString("\n") {
                        "  línea ${it.location.line}: ${it.message}"
                    })
            }
        }
}
```

Tres cosas que este diseño da:

1. **Agregar un caso es agregar un archivo.** No hay que tocar código de test.
2. **`@TestFactory` genera un test por archivo**, así el reporte dice exactamente
   cuál falló.
3. **El mensaje de fallo lista los errores que sí salieron.** Cuando un test falla,
   se ve de inmediato qué pasó en vez de tener que correr el programa a mano.

### Los válidos: cero errores, y ejecución donde aplique

```kotlin
class ProgramasValidosTest {

    @TestFactory
    fun `cada programa valido compila sin errores`(): List<DynamicTest> =
        programasEn("programas/validos").map { archivo ->
            DynamicTest.dynamicTest(archivo.nameWithoutExtension) {
                val resultado = CompilerPipeline.compile(archivo.readText())

                assertTrue(resultado.errors.isEmpty(),
                    "${archivo.name} debería compilar sin errores, pero produjo:\n" +
                    resultado.errors.joinToString("\n") {
                        "  línea ${it.location.line}: ${it.message}"
                    })
            }
        }
}
```

Y para los que además deben producir una salida concreta, la salida esperada va en el
mismo archivo:

```cps
// SALIDA: 8
// SALIDA: 120
// SALIDA: Toby ladra.

print(3 + 5);
print(factorial(5));
print(perro.hablar());
```

### La cobertura mínima que hay que garantizar

**Un caso válido y uno inválido por cada viñeta del enunciado.** La lista completa:

| Sección del enunciado | Reglas a cubrir |
|---|---|
| Sistema de Tipos | aritmética, lógicos, comparaciones, asignación, `const` inicializada, listas |
| Manejo de Ámbito | resolución local/global, no declaradas, redeclaración, bloques anidados, un entorno por función/clase/bloque |
| Funciones | cantidad de argumentos, tipo de argumentos, tipo de retorno, recursión, anidadas y closures, redeclaración |
| Control de Flujo | condición booleana en `if`/`while`/`do-while`/`for`, `break`/`continue` en bucles, `return` en funciones |
| Clases y Objetos | atributos y métodos accedidos con `.`, invocación del constructor, `this` en la clase |
| Listas | tipo de los elementos, validación de índices |
| Generales | código muerto, sentido semántico (no multiplicar funciones), declaraciones duplicadas |

### Aceptación

- Existe al menos un programa válido y uno inválido por cada fila de la tabla.
- `./gradlew test` los corre todos y pasan.
- Cada programa inválido produce el error esperado **en la línea esperada**.
- `demo_completa.cps` es el programa por defecto del IDE, compila sin errores, y su
  salida coincide con las anotaciones `// SALIDA:`.
- Agregar un archivo `.cps` nuevo a cualquiera de las dos carpetas lo incorpora a la
  batería **sin tocar código**.

---

## Ticket 8.2 — Documentación de arquitectura y de ejecución

- **Estado**: pendiente
- **Depende de**: 7.4

**Archivos:**

- `README.md` (reescribir)
- `docs/arquitectura.md` (NUEVO)
- `docs/reglas-de-tipos.md` (ya creado en 4.1 — revisar que esté completo)
- `docs/decisiones-gramatica.md` (ya creado en 0.5 — revisar)

### `README.md`: cómo ejecutar

Es lo primero que va a abrir quien evalúe. Debe permitir correr el proyecto **sin
preguntar nada**:

1. Requisitos: JDK 21, y nada más (Gradle viene con el wrapper, ANTLR lo baja
   Gradle).
2. Cómo compilar: `./gradlew build`.
3. Cómo abrir el IDE: `./gradlew runGui`.
4. Cómo correr las pruebas: `./gradlew test`, y dónde queda el reporte.
5. Cómo empaquetar el instalador nativo: `./gradlew packageDmg` / `packageMsi` /
   `packageDeb`.
6. Un recorrido de un minuto por el IDE: escribir código, compilar, y qué muestra
   cada pestaña.
7. La estructura de carpetas, comentada.

**Que la sección de requisitos sea corta y exacta importa.** Si el evaluador no
puede correr el proyecto, la nota no depende de la calidad del código.

### `docs/arquitectura.md`: cómo está hecho

El documento que explica el diseño. Secciones:

1. **El pipeline en un diagrama**: de `.cps` a salida, con las seis etapas.
2. **Qué hace ANTLR y qué se hizo a mano.** Importante decirlo explícitamente: el
   lexer y el parser los genera ANTLR desde `Compiscript.g4`; todo lo semántico es
   propio.
3. **Las dos pasadas y por qué son dos**: las referencias adelantadas.
4. **El árbol de ámbitos**: la estructura de hash de hashes, los dos enlaces
   (`parent` y `superclass`), y por qué los ámbitos no se descartan.
5. **Por qué NO hay tabla de tipos numerada**: la decisión 13 y su razonamiento. Es una omisión deliberada y hay que poder defenderla.
6. **El sistema de tipos y sus cuatro propiedades**, con el lugar del código donde
   cada una se cumple (la tabla del README del roadmap).
7. **Las decisiones de diseño**, con su razón. Se copian del README del roadmap.
8. **Lo que queda fuera de alcance**, y por qué. Que "pendiente" no se confunda con
   "descartado".

### Lo que queda fuera, documentado

| Fuera de alcance | Por qué |
|---|---|
| Propagación de constantes a través de variables (`const A = true; while (A)`) | Requiere análisis de flujo de datos completo; el plegado directo cubre los casos frecuentes |
| Sobrecarga de funciones | El enunciado la prohíbe explícitamente |
| Genéricos, lambdas, `switch` con fall-through | No están en la gramática de Compiscript |
| Generación de código intermedio y asignación de registros | Es la fase siguiente del compilador, no esta |
| Un recolector de basura funcional | Se produce la **información** que necesitaría, no el recolector |

### Aceptación

- Alguien que nunca vio el proyecto puede clonarlo y correr `./gradlew runGui`
  siguiendo solo el `README.md`.
- `docs/arquitectura.md` tiene las ocho secciones.
- `docs/reglas-de-tipos.md` tiene una fila por función pública de `TypeRules`, y
  cada fila nombra un test que existe.
- `docs/decisiones-gramatica.md` lista cada cambio hecho al `.g4` con su
  justificación.
- No hay ningún documento en `docs/` que describa código que no existe. *Fue un
  problema real en el proyecto anterior.*

---

## Ticket 8.3 — Verificación end to end

- **Estado**: pendiente
- **Depende de**: todos los anteriores

**Archivos:** ninguno. Es verificación manual, con el proyecto corriendo.

**Qué se hace:** el ensayo de la presentación. Se abre el IDE y se verifica cada
punto a ojo, con el proyecto tal como se va a entregar.

### Lista de verificación

**Entrada y salida**

- [ ] El IDE abre con el programa de demostración cargado.
- [ ] Se puede abrir un archivo `.cps` del disco.
- [ ] Se puede guardar y volver a abrir.
- [ ] El botón de compilar responde y la ventana no se congela.

**Los tres niveles de error**

- [ ] Un carácter inválido (`@`) produce un error **léxico** con línea y columna.
- [ ] Un `;` faltante produce un error **sintáctico** con línea y columna.
- [ ] `let x: integer = "a";` produce un error **semántico** con línea y columna.
- [ ] Los tres aparecen etiquetados y ordenados por posición.
- [ ] Clic en un error salta a esa línea del editor.

**Árboles**

- [ ] El árbol sintáctico de ANTLR se dibuja.
- [ ] El AST se dibuja y es visiblemente más chico.
- [ ] Los nodos de expresión del AST muestran su tipo.

**Tabla de símbolos** *(25 puntos)*

- [ ] Se ve el ámbito global.
- [ ] Se ve un ámbito por cada clase, función y bloque.
- [ ] Un ámbito de bloque **ya cerrado** sigue siendo visible.
- [ ] Los símbolos muestran nombre, categoría, tipo, número de tipo, offset y línea.
- [ ] El shadowing en varios niveles se ve correctamente.

**Tabla de tipos**

- [ ] Cada tipo aparece una sola vez con su número.
- [ ] Dos variables de la misma clase comparten número de tipo.

**Reporte de vivacidad**

- [ ] Se listan los usos de cada símbolo.
- [ ] Una variable capturada por un closure aparece marcada y como no liberable.

**Ejecución**

- [ ] `print(3 + 5)` imprime `8`.
- [ ] El factorial recursivo da el resultado correcto.
- [ ] La herencia funciona: `Perro` usa su propio `hablar()`.
- [ ] Un índice fuera de rango da error de ejecución con línea, y el `try/catch` lo
      atrapa.
- [ ] Un programa con errores **no** se ejecuta, y la consola lo dice.

**Robustez** — lo que se rompe el día de la presentación

- [ ] Un archivo vacío no revienta.
- [ ] Un archivo con solo un comentario no revienta.
- [ ] Recursión infinita da un mensaje y la ventana sigue viva.
- [ ] Una expresión con 50 paréntesis anidados no mata la aplicación.
- [ ] Compilar dos veces seguidas da el mismo resultado. *Verifica que no quedó
      estado global entre corridas — el problema que se corrigió en el ticket 0.6.*

**Pruebas y documentación**

- [ ] `./gradlew test` pasa completo.
- [ ] `./gradlew clean build` pasa desde cero.
- [ ] El repositorio tiene commits individuales de los tres integrantes.

### Aceptación

Los treinta y tantos puntos verificados a ojo, con el proyecto en el estado en que
se va a entregar. Cualquiera que falle es un ticket antes de la presentación.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 8.1 | Un programa `.cps` válido y uno inválido por regla, corriendo en la suite |
| 8.2 | `README.md`, `arquitectura.md`, `reglas-de-tipos.md`, `decisiones-gramatica.md` |
| 8.3 | La lista de verificación del día de la presentación, recorrida completa |
