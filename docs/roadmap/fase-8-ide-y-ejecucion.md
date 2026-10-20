# Fase 8: El IDE y la ejecución

**Objetivo de la fase:** que el `.s` se vea en el IDE, se pueda exportar junto al
fuente y se pueda correr con una línea.

**Por qué va aquí:** el IDE es un entregable, pero mostrar el `.s` solo tiene sentido
cuando el generador traduce todo.

**Al terminar:** una pantalla "Código ARM" en el IDE, una opción del menú para guardar
el `.s`, y la explicación de cómo correrlo en el README.

---

## Ticket 8.1: La pantalla del código ARM

- **Estado**: pendiente
- **Depende de**: 2.4

**Archivos:**

- `gui/screens/AssemblyScreen.kt` (NUEVO)
- `gui/components/ViewMenu.kt` y `gui/App.kt` (MODIFICAR: la pantalla nueva)
- Tests: `AppStateTest` (AMPLIAR)

### Qué se hace

La misma forma que `IntermediateCodeScreen`: el `.s` con su sangría, en letra
monoespaciada, con los mismos tres estados (sin compilar, con errores, listo). A la
izquierda, opcionalmente, el TAC de la función seleccionada, para comparar una
instrucción con su traducción.

### Aceptación

- Un programa válido muestra su `.s`; uno con errores muestra el aviso de siempre.

---

## Ticket 8.2: Exportar el `.s`

- **Estado**: pendiente
- **Depende de**: 8.1

**Archivos:**

- `gui/components/FileMenu.kt` (MODIFICAR: `saveAssembly`)
- Tests: `FileMenuTest` (AMPLIAR)

### Qué se hace

Igual que `saveIntermediateCode`: el `.s` se guarda junto al `.cps`, con el mismo
nombre y la extensión `.s`.

### Aceptación

- Guardar `factorial.cps` produce `factorial.s` en la misma carpeta, y
  `scripts/arm.sh factorial.s` lo corre.

---

## Ticket 8.3: Cómo ejecutar, en el README

- **Estado**: pendiente
- **Depende de**: 0.2, 8.2

**Archivos:**

- `README.md` (MODIFICAR)

### Qué se hace

Los pasos completos: construir la imagen, registrar QEMU, generar el `.s` desde el IDE
o desde la línea de comandos, y correrlo. Y cómo hacer lo mismo en una Raspberry Pi
real, donde basta `gcc -o programa programa.s && ./programa`.

### Aceptación

- Alguien que clona el repositorio corre un programa siguiendo solo el README.
