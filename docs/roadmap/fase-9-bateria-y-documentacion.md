# Fase 9: Batería y documentación

**Objetivo de la fase:** demostrar que el ARM calcula lo mismo que el intérprete en
todos los programas de la batería, y cerrar los documentos de la etapa.

**Por qué va al final:** compara el producto terminado.

**Al terminar:** cada programa válido tiene su `.s` dorado; una prueba de punta a punta
corre todos en el contenedor y compara su salida con la del intérprete; y
`docs/codigo-arm.md` está completo.

---

## Ticket 9.1: Los `.s` dorados

- **Estado**: pendiente
- **Depende de**: 7.1

**Archivos:**

- `resources/programas/validos/*.s` (NUEVOS)
- Tests: `AssemblyGoldenTest` (NUEVO)

### Qué se hace

El mismo mecanismo que los `.tac` (decisión 49): cada `.cps` válido lleva su `.s`
esperado, se regeneran con `./gradlew test -DupdateGolden=true`, y el cambio se revisa
en el `git diff`. Los `.s` no incluyen el runtime, que es igual para todos.

### Por qué

**Dorados además de la prueba de punta a punta:** la de punta a punta necesita Docker,
y no siempre está disponible. Los dorados corren en cualquier máquina y detectan
cualquier cambio en la traducción.

### Aceptación

- Los dorados corren sin Docker y pasan.

---

## Ticket 9.2: La prueba de punta a punta

- **Estado**: pendiente
- **Depende de**: 9.1, 0.2

**Archivos:**

- Tests: `ArmExecutionTest` (NUEVO)
- `app/build.gradle.kts` (MODIFICAR: la propiedad `runArm`)

### Qué se hace

Con `./gradlew test -DrunArm=true`, para cada programa válido:

1. Se compila, se escribe su `.s` y se corre con `scripts/arm.sh`.
2. Se compara la salida, línea por línea, con la del intérprete.
3. Si el intérprete terminó con un error en ejecución, el ARM tiene que terminar con
   código 1; si no, con 0.

Sin la propiedad, la prueba se salta, para que `./gradlew test` siga funcionando sin
Docker.

### Aceptación

- Todos los programas válidos coinciden. Las diferencias que se encuentren se corrigen
  o se documentan como limitación con su razón.

---

## Ticket 9.3: Los documentos finales

- **Estado**: pendiente
- **Depende de**: 9.2

**Archivos:**

- `docs/codigo-arm.md` (MODIFICAR: runtime, excepciones, supuestos y limitaciones)
- `README.md` (MODIFICAR: arquitectura y conteos)

### Qué se hace

Se completa el documento con el runtime (cada rutina, qué recibe y qué devuelve), las
excepciones, y una sección de supuestos y limitaciones: sin liberación de memoria, sin
chequeo de desbordamiento de pila, el formato de los flotantes, y los registros que
quedan sin usar.

### Aceptación

- El documento no tiene secciones pendientes y cada ejemplo sale de un `.s` real.
