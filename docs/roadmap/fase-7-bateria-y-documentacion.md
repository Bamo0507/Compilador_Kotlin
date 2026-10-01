# Fase 7: Batería de pruebas y documentación

**Objetivo de la fase:** cerrar los entregables que se califican además del código: la
batería de pruebas de la generación de código intermedio, el documento del lenguaje
intermedio completo, la documentación de arquitectura y de ejecución, y la verificación
de punta a punta.

**Por qué al final:** la batería fija el TAC exacto de cada programa, así que solo se
puede escribir cuando el generador ya no va a cambiar de forma. Los tests unitarios de
cada ticket ya se fueron escribiendo; aquí se agregan los de programa completo.

El enunciado lo pide con estas palabras:

> - Batería de pruebas que valide casos exitosos y fallidos de la generación de código
>   intermedio, presente y funcional al momento de la evaluación.
> - Documentación de la arquitectura de la implementación y documentación de cómo
>   ejecutar el compilador.
> - Documentación detallada del lenguaje intermedio diseñado, con ejemplos de
>   traducción y los supuestos considerados.

---

## Ticket 7.1: La batería con archivos dorados

- **Estado**: pendiente
- **Depende de**: 5.5, 6.2

**Archivos:**

- `resources/programas/validos/*.tac` (NUEVOS: uno por cada `.cps` válido)
- `resources/programas/validos/` (NUEVOS: los `.cps` que falten, ver la tabla de
  cobertura)
- `app/src/test/kotlin/org/compiler/IntermediateCodeTest.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/InvalidProgramsTest.kt` (AMPLIAR)
- `app/build.gradle.kts` (MODIFICAR: pasar la propiedad `updateGolden` a los tests)

### Los casos exitosos

Cada `.cps` válido lleva al lado un `.tac` con el TAC esperado (decisión 49). El test
compila el programa, imprime su TAC y lo compara contra el archivo:

```
programas/validos/funciones_recursion.cps
programas/validos/funciones_recursion.tac     <- el TAC esperado, línea por línea
```

- Si el `.tac` no existe, el test falla y su mensaje incluye el TAC generado, listo
  para revisarlo y guardarlo.
- Si no coincide, el mensaje muestra la primera línea distinta.
- **Para regenerarlos** después de un cambio deliberado:
  `./gradlew test -DupdateGolden=true` reescribe los `.tac` con lo que genera el
  compilador. El `git diff` muestra exactamente qué cambió, y se revisa antes de
  commitear.

El selector del IDE ya filtra por `.cps`, así que los `.tac` no aparecen en él.

### Los casos fallidos

Los `.cps` inválidos que ya existen: cada uno debe producir el error esperado en su
línea, como hoy, y además **no** debe producir TAC. Es la regla de la decisión 18: con
errores, el generador no corre.

### La cobertura

Al menos un programa válido por cada viñeta de la sección *Especificaciones* del
enunciado:

| Viñeta del enunciado | Programas |
|---|---|
| Expresiones aritméticas, con precedencia y asociatividad | `tipos_aritmetica.cps` |
| Expresiones lógicas y de comparación | `tipos_logicos.cps`, `tipos_comparaciones.cps` |
| Declaraciones y asignaciones de variables y constantes | `tipos_asignacion.cps` |
| `if/else`, `while`, `do-while`, `for` | `flujo_condiciones.cps` |
| `switch` | **`flujo_switch.cps`** (NUEVO) |
| `break` y `continue` | `flujo_break_continue.cps` |
| Definición, parámetros, llamadas y retorno | `funciones_argumentos.cps` |
| Recursión | `funciones_recursion.cps` |
| Funciones anidadas y closures | `funciones_closures.cps`, **`funciones_acceso_anidado.cps`** (NUEVO: dos niveles y una llamada entre hermanas) |
| Creación de objetos y constructor | `clases_constructor.cps`, `clases_constructor_subclase.cps` |
| Atributos, métodos y `this` | `clases_this.cps`, `clases_herencia.cps` |
| Despacho de un método sobrescrito | **`clases_despacho.cps`** (NUEVO: `let a: Animal = new Perro()`) |
| Listas y acceso por índice | `tipos_listas.cps` |
| Temporales y su reciclaje | **`tac_temporales.cps`** (NUEVO: la expresión de la diapositiva 19 con dos temporales) |
| El GDA | **`tac_subexpresiones.cps`** (NUEVO: una subexpresión común, y una con llamada que no se comparte) |
| El ternario y los booleanos como valor | **`flujo_ternario.cps`** (NUEVO) |
| `try/catch` y los chequeos en ejecución | `flujo_try_retorno.cps`, **`flujo_errores_ejecucion.cps`** (NUEVO: división, índice y `null` atrapados) |

Cada programa nuevo lleva sus anotaciones `// NOMBRE:` y `// SALIDA:`, como los
existentes. Así también se verifica con el intérprete que el programa hace lo que dice.

### Aceptación

- Cada `.cps` válido tiene su `.tac`, y `./gradlew test` los compara todos.
- Cada `.cps` inválido produce su error y ningún TAC.
- Cada fila de la tabla de cobertura tiene al menos un programa.
- Agregar un caso sigue siendo agregar archivos, sin tocar código de tests.

---

## Ticket 7.2: El documento del lenguaje intermedio, completo

- **Estado**: pendiente
- **Depende de**: 7.1, 5.6

**Archivos:**

- `docs/lenguaje-intermedio.md` (REVISAR Y COMPLETAR)

Las secciones 1 a 26 ya se escribieron fase por fase. Este ticket las revisa como un
solo documento, que es como lo va a leer quien califica:

1. **Un índice** al inicio, y una sección *"Cómo leer este documento"* que explique la
   notación de los ejemplos.
2. **Una tabla resumen** con todas las familias de instrucciones, su sintaxis y la
   sección donde se explican.
3. **Los supuestos**, juntados en una sola lista al final. Hoy están repartidos en las
   secciones de cada fase.
4. **Cada ejemplo** es el contenido de un `.tac` de la batería, citado por su nombre de
   archivo, para que el calificador pueda abrirlo y compilarlo él mismo.

### Aceptación

- Cada familia de `Quadruple` aparece en la tabla resumen.
- Cada decisión de diseño del TAC (17 a 49) que afecta su forma está explicada en
  alguna sección.
- Ningún ejemplo del documento difiere de su `.tac` en la batería.

---

## Ticket 7.3: Arquitectura y README

- **Estado**: pendiente
- **Depende de**: 7.2

**Archivos:**

- `docs/arquitectura.md` (NUEVO: quedó pendiente desde la etapa anterior)
- `README.md` (MODIFICAR)

### `docs/arquitectura.md`

1. **El pipeline**, de `.cps` a TAC, con sus siete etapas y qué produce cada una.
2. **Qué hace ANTLR y qué se hizo a mano.**
3. **Las dos pasadas semánticas** y por qué son dos.
4. **El árbol de ámbitos** y cómo se extiende con zona, tamaño y desplazamiento.
5. **El generador**: el GDA, el pool de temporales, las condiciones con caída y los
   registros de activación, cada uno con el archivo donde vive.
6. **Las decisiones de diseño**, con su razón: las de la etapa anterior y las 17 a 49.
7. **Lo que queda fuera de alcance**, y por qué: el recolector de basura, la
   recursión ilimitada, las funciones como valores, y lo que es de la fase de
   assembler.

### `README.md`

- La tabla de fases del compilador con la etapa de código intermedio.
- La cuarta vista del IDE.
- La batería: cuántos programas hay, qué verifica cada nivel, y cómo regenerar los
  `.tac`.
- La estructura de carpetas, con `frontend/intermediate/` y `docs/lenguaje-intermedio.md`.

### Aceptación

- Alguien que nunca vio el proyecto puede clonarlo, correr `./gradlew run`, compilar un
  programa y ver su TAC, siguiendo solo el README.
- No hay ningún documento en `docs/` que describa código que no existe.

---

## Ticket 7.4: Verificación de punta a punta

- **Estado**: pendiente
- **Depende de**: todos los anteriores

**Archivos:** ninguno. Es el ensayo de la presentación, con el proyecto tal como se va
a entregar.

### Lista de verificación

**Generación**

- [ ] `grep -rn "TODO" app/src/main` no devuelve nada.
- [ ] Todo `.cps` válido genera TAC sin excepción.
- [ ] Ningún `.cps` inválido genera TAC.

**IDE**

- [ ] El IDE abre con el programa de demostración, y su TAC se ve en **Código
      intermedio**, en texto y como cuádruplos.
- [ ] La tabla de símbolos muestra zona, tamaño y desplazamiento, y el registro de
      activación de cada función.
- [ ] Exportar el TAC produce un archivo igual a lo que muestra la pantalla.
- [ ] Un programa con errores muestra sus errores y la pantalla del TAC dice que no
      está disponible.

**Robustez**

- [ ] Un archivo vacío genera solo `begin_func $main` y `end_func $main`.
- [ ] Compilar dos veces seguidas da el mismo TAC.
- [ ] Un programa largo, como `demo_completa.cps`, no congela la ventana.

**Entregables**

- [ ] `./gradlew clean build` pasa desde cero.
- [ ] `docs/lenguaje-intermedio.md`, `docs/arquitectura.md` y el README están completos.
- [ ] El repositorio tiene commits individuales de los tres integrantes.

### Aceptación

Todos los puntos verificados a mano. Cualquiera que falle se convierte en un ticket
antes de la presentación.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 7.1 | La batería con un `.tac` por programa válido, y los inválidos sin TAC |
| 7.2 | El documento del lenguaje intermedio como una sola pieza |
| 7.3 | `docs/arquitectura.md` y el README |
| 7.4 | La lista de verificación del día de la presentación, recorrida |
