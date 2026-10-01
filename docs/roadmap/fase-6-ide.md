# Fase 6: El IDE

**Objetivo de la fase:** que el código intermedio se vea en el IDE, en texto y como
tabla de cuádruplos, y que se pueda guardar en un archivo.

**Por qué es una fase aparte:** el enunciado lo pide como salida (*"el programa muestra
el código intermedio generado o, en su caso, los errores encontrados"*) y como
entregable (*"IDE funcional que permita escribir y compilar código Compiscript"*). La
tabla de símbolos extendida ya quedó en los tickets 4.5 y 5.1; aquí falta solo la vista
del TAC.

**Al terminar:** el menú **Vista** tiene una cuarta pantalla, **Código intermedio**, y el
menú **Archivo** puede exportar el TAC a un `.tac`.

**Teoría que la sostiene:** ninguna nueva. La vista de cuádruplos es la tabla de la
diapositiva 25 de la presentación 06.

---

## Ticket 6.1: La pantalla de código intermedio

- **Estado**: pendiente
- **Depende de**: 1.2, 2.3

**Archivos:**

- `gui/components/ViewMenu.kt` (MODIFICAR: `AppView.INTERMEDIATE_CODE`)
- `gui/App.kt` (MODIFICAR: la rama nueva)
- `gui/screens/IntermediateCodeScreen.kt` (NUEVO)
- `gui/components/QuadrupleTable.kt` (NUEVO)
- `app/src/test/kotlin/org/compiler/AppStateTest.kt` (AMPLIAR)

### Qué se hace

La misma forma que `SymbolTableScreen`: dos chips arriba para elegir la vista.

```
[ TAC ]  [ Cuádruplos ]                          Temporales usados: 3

 1  vtable Perro: Perro.hablar, Animal.comer
 2  begin_func $main, 24
 3      t1 = alloc 12
 4      t1[0] = vtable.Perro
 ...
```

- **TAC:** el texto de `TacPrinter`, en fuente monoespaciada, con número de línea. Las
  etiquetas van pegadas al margen y el resto con sangría, como en la diapositiva 22.
- **Cuádruplos:** una tabla con las columnas `#`, operador, arg1, arg2 y resultado,
  armada con `toRow()`.
- **Temporales usados:** la suma de los `temporaryCount` de todas las funciones. Es la
  evidencia visible del reciclaje que pide el enunciado.

| Estado | Qué muestra |
|---|---|
| Sin compilar | *"Presiona compilar para ver el código intermedio."* |
| Con errores | *"No disponible: el programa tiene errores. Revísalos en el Editor."* |
| Compiló | El TAC |

**Por qué una pantalla propia y no un panel del Editor:** el TAC de un programa mediano
tiene cientos de líneas. Al lado del editor y la consola no habría espacio para leerlo,
y las otras dos salidas grandes, los árboles y la tabla de símbolos, ya tienen su
pantalla.

### Aceptación

- Compilar `demo_completa.cps` y abrir **Código intermedio** muestra su TAC, y la vista
  de cuádruplos tiene una fila por instrucción.
- Con un programa con errores, la pantalla muestra el mensaje y no se cae.
- `AppStateTest`: después de compilar un programa válido, `result.tac` no es nulo.

---

## Ticket 6.2: Exportar el TAC

- **Estado**: pendiente
- **Depende de**: 6.1

**Archivos:**

- `gui/components/FileMenu.kt` (MODIFICAR: *Exportar código intermedio*)

### Qué se hace

Una entrada nueva en el menú **Archivo** que guarda el texto del TAC en un `.tac`,
con el mismo diálogo que ya usa *Guardar*. Está deshabilitada si no hay TAC.

**Por qué:** el enunciado pone el TAC como salida del programa, y un archivo es la
forma de entregarlo o revisarlo fuera del IDE. Además, es la forma más rápida de
crear el `.tac` esperado de un caso nuevo de la batería (ticket 7.1).

### Aceptación

- Exportar escribe exactamente el texto que muestra la pantalla.
- Sin TAC, la entrada está deshabilitada.

---

## Resumen de la fase

| Ticket | Deja listo |
|---|---|
| 6.1 | La pantalla de código intermedio, en texto y como tabla de cuádruplos |
| 6.2 | Exportar el TAC a un archivo |
