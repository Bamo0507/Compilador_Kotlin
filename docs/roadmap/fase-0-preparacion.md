# Fase 0: Preparación

**Objetivo de la fase:** dejar el terreno listo para generar ARM. El roadmap del código
intermedio queda archivado, el entorno para ensamblar y correr ARM de 32 bits queda
probado en la Mac, y el documento de la etapa arranca con el mapa de MIPS a ARM.

**Por qué va primero:** sin un lugar donde correr el `.s`, ningún ticket del backend se
puede verificar. Y el mapa de MIPS a ARM es la base de todo lo que sigue, porque la
referencia del curso es MIPS.

**Al terminar:** un roadmap nuevo con su índice, un script que ensambla y corre un `.s`
con una línea, y `docs/codigo-arm.md` con sus dos primeras secciones.

**Teoría que la sostiene:** puntos 1, 4 y 9 (registros, directivas y llamadas al
sistema), estudiados en la sesión de investigación.

---

## Ticket 0.1: Archivar el roadmap del código intermedio

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `docs/roadmap/README.md` y las ocho fases de la etapa anterior (MOVER a
  `docs/roadmap/intermedio/`)
- `docs/roadmap/intermedio/README.md` (MODIFICAR: nota de archivo y enlace corregido)
- `docs/roadmap/semantico/README.md` (MODIFICAR: enlaces a las dos etapas)
- `docs/roadmap/README.md` (NUEVO: índice de esta etapa)

### Qué se hace

Se mueven con `git mv`, nombre por nombre, para conservar la historia. La nota de
archivo dice que las decisiones 17 a 57 siguen vigentes y que los tickets 7.2 a 7.4
quedaron sin marcar porque la documentación final se entregó en Google Docs.

### Aceptación

- `docs/roadmap/` contiene solo `README.md`, las fases de esta etapa, `semantico/` e
  `intermedio/`.
- `git log --follow docs/roadmap/intermedio/fase-5-objetos-y-listas.md` muestra la
  historia previa al movimiento.
- El README nuevo tiene las decisiones 58 a 73.

---

## Ticket 0.2: El entorno ARM en Docker

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `scripts/arm.sh` (NUEVO)
- `scripts/hola.s` (NUEVO: programa de prueba)
- `README.md` (MODIFICAR: sección "Ejecutar el código ARM")

### Qué es esto, en simple

Una Mac no puede correr ARM de 32 bits. Docker levanta una pequeña Linux ARM emulada
con QEMU, con `gcc` adentro, y ahí se ensambla y se corre el `.s`. El script esconde
los comandos para que correr un programa sea una línea.

### Qué se hace

```bash
#!/usr/bin/env bash
# Uso: scripts/arm.sh programa.s
# Ensambla el programa con gcc dentro de un contenedor ARM de 32 bits y lo ejecuta.
set -euo pipefail

archivo="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
carpeta="$(dirname "$archivo")"
nombre="$(basename "$archivo" .s)"

docker run --rm --platform linux/arm/v7 \
  -v "$carpeta":/trabajo -w /trabajo compiscript-arm \
  sh -c "gcc -o /tmp/$nombre $nombre.s && /tmp/$nombre"
```

La imagen `compiscript-arm` se construye una vez desde `scripts/Dockerfile`:
`debian:bookworm-slim` con `gcc` instalado. Así cada corrida no reinstala nada.

`hola.s` es el programa mínimo con libc: imprime un entero y un flotante con `printf`
y termina con código 0. El flotante sale de `.rodata` con `vldr` y pasa a r2 y r3 con
`vmov`, como lo exige `printf`.

### Por qué

- **Docker y no CPUlator:** CPUlator no implementa las llamadas al sistema de Linux ni
  tiene libc.
- **El ejecutable en `/tmp` del contenedor:** así no queda basura binaria junto al
  fuente.

**Notas del entorno**, que van al README:

- Con Colima, el manejador de QEMU para ARM se registra con
  `docker run --privileged --rm tonistiigi/binfmt --install arm`, y hay que repetirlo
  cada vez que Colima reinicia.
- Colima solo comparte carpetas dentro de `$HOME`.

### Aceptación

- `scripts/arm.sh scripts/hola.s` imprime `int=42 double=3.500000` y devuelve 0.
- El README explica cómo construir la imagen y registrar QEMU.

---

## Ticket 0.3: El documento de la etapa, secciones 1 y 2

- **Estado**: completado
- **Depende de**: ninguno

**Archivos:**

- `docs/codigo-arm.md` (NUEVO)

### Qué se hace

El documento entregable de esta etapa, escrito como documento final desde el primer
ticket. Arranca con:

1. **De la representación intermedia a la máquina.** Qué hace el back-end, por qué
   recibe TAC y no el AST, y por qué ARM de 32 bits (decisiones 58 a 60).
2. **De MIPS a ARM.** Las tablas de equivalencia que pidió el catedrático:
   - los registros y su papel, con `$t` y `$s` frente a r0 a r3 y r4 a r10
   - el mapa completo de directivas (`.data`, `.bss`, `.section .rodata`, `.text`,
     `.asciz`, `.ascii`, `.space`, `.word`, `.byte`, `.double`, `.align`, `.balign`,
     `.global`)
   - las llamadas al sistema de MARS frente a las de Linux, y por qué se enlaza con
     libc (decisión 61)

Las secciones siguientes se agregan en la fase donde se implementa cada tema.

### Aceptación

- Las dos secciones están en prosa, con tablas de dos o tres columnas y sin bloques de
  código.
- Cada directiva de la tabla tiene su equivalente de MARS y un ejemplo de una línea.

### Respaldo

Manual de GNU as (Directivas y Directivas de ARM); AAPCS32, sección 6; documentación de
llamadas al sistema de MARS.
