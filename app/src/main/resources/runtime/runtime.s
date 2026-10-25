@ ════════════════════════════════════════════════════════════════════════
@  Runtime de Compiscript para ARM de 32 bits
@ ════════════════════════════════════════════════════════════════════════
@
@ Rutinas de apoyo que el compilador agrega al final de cada programa. El codigo
@ generado nunca llama a libc directamente: llama a una de estas rutinas, y solo
@ ellas hablan con libc.
@
@ Todas siguen el estandar de llamadas de ARM:
@   - reciben su argumento en r0, o en d0 si es un flotante
@   - pueden pisar r0 a r3, r12 y d0 a d7
@   - devuelven intactos r4 a r11 y sp
@ Ademas, sp tiene que estar alineado a 8 al llamar a libc. Por eso cada rutina
@ guarda en la pila una cantidad par de registros.

    .section .rodata
.Lformato_entero:
    .asciz "%d\n"
.Lformato_string:
    .asciz "%s\n"
.Lformato_flotante:
    .asciz "%.6f"
.Lverdadero:
    .asciz "true"
.Lfalso:
    .asciz "false"
.Lformato_error:
    .asciz "Error: %s\n"
.Lsin_memoria:
    .asciz "sin memoria"

    .text

@ ────────────────────────────────────────────────────────────────────────
@  cps_imprimir_entero
@  Recibe: r0, un entero con signo.
@  Imprime el numero y un salto de linea.
@ ────────────────────────────────────────────────────────────────────────
cps_imprimir_entero:
    push {r4, lr}                   @ dos registros: la pila sigue alineada a 8
    mov r1, r0                      @ el entero es el segundo argumento de printf
    ldr r0, =.Lformato_entero       @ y el formato, el primero
    bl printf
    pop {r4, pc}                    @ recuperar lr directo en pc es volver

@ ────────────────────────────────────────────────────────────────────────
@  cps_imprimir_string
@  Recibe: r0, la direccion de un texto terminado en cero.
@  Imprime el texto y un salto de linea.
@ ────────────────────────────────────────────────────────────────────────
cps_imprimir_string:
    push {r4, lr}
    mov r1, r0
    ldr r0, =.Lformato_string
    bl printf
    pop {r4, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_imprimir_booleano
@  Recibe: r0, 0 para false y cualquier otro valor para true.
@  Imprime "true" o "false" y un salto de linea.
@ ────────────────────────────────────────────────────────────────────────
cps_imprimir_booleano:
    push {r4, lr}
    cmp r0, #0
    ldreq r0, =.Lfalso              @ solo una de las dos cargas se ejecuta
    ldrne r0, =.Lverdadero
    bl cps_imprimir_string
    pop {r4, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_comparar_strings
@  Recibe: r0 y r1, las direcciones de dos textos.
@  Devuelve en r0 un numero negativo si el primero va antes en orden
@  alfabetico, cero si son iguales y positivo si va despues. El codigo
@  generado lo compara con 0 usando la misma relacion del TAC.
@ ────────────────────────────────────────────────────────────────────────
cps_comparar_strings:
    push {r4, lr}
    bl strcmp
    pop {r4, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_imprimir_flotante
@  Recibe: d0, un flotante de doble precision.
@  Imprime el numero con seis decimales, sin los ceros sobrantes y dejando
@  siempre al menos uno: 3.5, 1.0, 0.333333.
@
@  La pila durante la rutina, desde sp hacia arriba:
@    [sp, #0]    el flotante, como argumento de snprintf (8 bytes)
@    [sp, #8]    el bufer donde snprintf escribe el texto (40 bytes)
@ ────────────────────────────────────────────────────────────────────────
cps_imprimir_flotante:
    push {r4, r5, r6, lr}           @ cuatro registros: 16 bytes
    sub sp, sp, #48                 @ 16 + 48 = 64: la pila sigue alineada a 8

    @ snprintf(bufer, 40, "%.6f", flotante). Recibe una cantidad variable de
    @ argumentos, asi que el flotante no va en d0: iria en un par de registros
    @ de enteros, pero r0 a r2 ya estan ocupados y solo queda r3, asi que va
    @ en la pila.
    vstr d0, [sp]
    add r0, sp, #8                  @ el bufer
    mov r1, #40                     @ su tamaño
    ldr r2, =.Lformato_flotante
    bl snprintf                     @ devuelve en r0 cuantos caracteres escribio

    @ Recortar los ceros del final: r5 avanza desde el byte cero que cierra el
    @ texto hacia atras, mientras el caracter anterior sea '0'. Siempre hay un
    @ punto antes de los decimales, asi que el ciclo termina.
    add r4, sp, #8                  @ r4: el inicio del texto
    add r5, r4, r0                  @ r5: el byte cero del final
.Lrecortar_ceros:
    ldrb r6, [r5, #-1]              @ el ultimo caracter que queda
    cmp r6, #'0'
    bne .Lrevisar_punto
    sub r5, r5, #1
    b .Lrecortar_ceros

    @ Si el recorte llego hasta el punto, como en "3.", se conserva un cero:
    @ el byte en r5 todavia es el '0' que habia, asi que basta con avanzar uno.
.Lrevisar_punto:
    cmp r6, #'.'
    addeq r5, r5, #1
    mov r6, #0
    strb r6, [r5]                   @ el nuevo final del texto

    mov r0, r4
    bl cps_imprimir_string

    add sp, sp, #48
    pop {r4, r5, r6, pc}


@ ════════════════════════════════════════════════════════════════════════
@  El monticulo
@ ════════════════════════════════════════════════════════════════════════
@
@ malloc, calloc y realloc son el administrador del monticulo: les pides
@ bytes y te dan la direccion de un bloque libre, alineado a 8. Cuando ya no
@ queda espacio devuelven 0. Estas rutinas revisan esa respuesta, para que
@ ningun programa siga trabajando con una direccion nula.

@ ────────────────────────────────────────────────────────────────────────
@  cps_reservar
@  Recibe: r0, cuantos bytes.
@  Devuelve en r0 la direccion de un bloque nuevo, con todos sus bytes en
@  cero: un campo de referencia sin inicializar queda en null, que es 0.
@  Si no hay memoria, termina el programa.
@ ────────────────────────────────────────────────────────────────────────
cps_reservar:
    push {r4, lr}
    mov r1, r0                      @ calloc(1, bytes): un bloque de ese tamaño
    mov r0, #1
    bl calloc
    cmp r0, #0
    beq cps_sin_memoria             @ no vuelve
    pop {r4, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_sin_memoria
@  Imprime "Error: sin memoria" y termina con codigo 1. No es un error que
@  un catch pueda arreglar: el manejador mismo podria necesitar memoria.
@ ────────────────────────────────────────────────────────────────────────
cps_sin_memoria:
    ldr r0, =.Lsin_memoria
    b cps_terminar_con_error

@ ────────────────────────────────────────────────────────────────────────
@  cps_lista_agregar
@  Recibe: r0, la ficha de una lista; r1, cuantos bytes mide un elemento.
@  Devuelve en r0 la direccion de la casilla nueva, al final de la lista,
@  con la longitud ya aumentada. El codigo generado escribe ahi el valor:
@  asi la misma rutina sirve para enteros, booleanos, flotantes y
@  referencias.
@
@  La ficha mide 12 bytes:
@    [ficha, #0]   la longitud: cuantos elementos tiene
@    [ficha, #4]   la capacidad: cuantos caben en el contenido
@    [ficha, #8]   la direccion del contenido
@
@  Si el contenido esta lleno, se muda a uno del doble de capacidad, o de 4
@  si estaba vacio. realloc copia los elementos y libera el bloque viejo.
@  La ficha no se mueve, asi que toda variable que apunta a la lista ve los
@  elementos nuevos.
@ ────────────────────────────────────────────────────────────────────────
cps_lista_agregar:
    push {r4, r5, r6, lr}
    mov r4, r0                      @ r4: la ficha
    mov r5, r1                      @ r5: el tamaño de un elemento

    ldr r0, [r4, #0]                @ la longitud
    ldr r1, [r4, #4]                @ la capacidad
    cmp r0, r1
    bne .Lhay_lugar

    @ Lleno: la capacidad nueva es el doble, o 4 si era 0.
    movs r6, r1, lsl #1             @ r6: el doble; movs deja la bandera Z si es 0
    moveq r6, #4
    ldr r0, [r4, #8]                @ realloc(contenido, capacidad * tamaño). Con
    mul r1, r6, r5                  @ un contenido nulo funciona como malloc
    bl realloc
    cmp r0, #0
    beq cps_sin_memoria
    str r0, [r4, #8]                @ la ficha apunta al contenido nuevo
    str r6, [r4, #4]

.Lhay_lugar:
    ldr r0, [r4, #0]                @ la longitud
    ldr r1, [r4, #8]                @ el contenido
    mla r2, r0, r5, r1              @ la casilla: contenido + longitud * tamaño
    add r0, r0, #1
    str r0, [r4, #0]                @ un elemento mas
    mov r0, r2
    pop {r4, r5, r6, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_concatenar
@  Recibe: r0 y r1, las direcciones de dos textos.
@  Devuelve en r0 un texto nuevo en el monticulo, con el primero seguido del
@  segundo. Los dos originales no cambian: los literales viven en .rodata y
@  nunca se modifican.
@ ────────────────────────────────────────────────────────────────────────
cps_concatenar:
    push {r4, r5, r6, r7, r8, lr}   @ seis registros: 24 bytes
    mov r4, r0                      @ r4: el primer texto
    mov r5, r1                      @ r5: el segundo
    bl strlen
    mov r6, r0                      @ r6: cuanto mide el primero
    mov r0, r5
    bl strlen
    mov r7, r0                      @ r7: cuanto mide el segundo

    add r0, r6, r7
    add r0, r0, #1                  @ mas el byte cero del final, que cps_reservar
    bl cps_reservar                 @ ya deja en cero
    mov r8, r0                      @ r8: el texto nuevo

    mov r1, r4                      @ memcpy(nuevo, primero, largo del primero)
    mov r2, r6
    bl memcpy
    add r0, r8, r6                  @ memcpy(nuevo + largo del primero, segundo,
    mov r1, r5                      @        largo del segundo)
    mov r2, r7
    bl memcpy

    mov r0, r8
    pop {r4, r5, r6, r7, r8, pc}

@ ════════════════════════════════════════════════════════════════════════
@  Los errores: la pila de manejadores
@ ════════════════════════════════════════════════════════════════════════
@
@ Cada try deja una nota en una pila: "si algo falla, vuelve a este registro
@ de activacion, con la pila asi, y salta a esta etiqueta". throw toma la
@ nota de arriba y hace exactamente eso. Restaurar sp y fp descarta de golpe
@ los registros de activacion de todas las funciones que estaban de por
@ medio. Es la idea de setjmp y longjmp de C.
@
@ Cada nota mide 16 bytes y vive en el monticulo:
@    [nota, #0]    la nota anterior, o 0 si es la ultima
@    [nota, #4]    el sp de la funcion que abrio el try
@    [nota, #8]    su fp
@    [nota, #12]   la direccion de la etiqueta del catch

    .bss
    .balign 4
cps_manejadores:                    @ la nota de arriba, o 0 si no hay ningun try
    .space 4
cps_excepcion:                      @ el mensaje del ultimo error lanzado
    .space 4

    .text

@ ────────────────────────────────────────────────────────────────────────
@  cps_try_entrar
@  Recibe: r0, la direccion de la etiqueta del catch.
@  Apila una nota con el sp y el fp de quien llama. Como se entra con bl, sin
@  tocar la pila, el sp de quien llama es el que habia antes del push.
@ ────────────────────────────────────────────────────────────────────────
cps_try_entrar:
    push {r4, r5, r6, lr}
    mov r4, r0                      @ r4: la etiqueta del catch
    add r5, sp, #16                 @ r5: el sp de quien llama, antes del push
    mov r6, fp                      @ r6: su fp

    mov r0, #16
    bl cps_reservar                 @ r0: la nota nueva

    ldr r1, =cps_manejadores
    ldr r2, [r1]
    str r2, [r0, #0]                @ la nota anterior
    str r5, [r0, #4]
    str r6, [r0, #8]
    str r4, [r0, #12]
    str r0, [r1]                    @ la nueva queda arriba
    pop {r4, r5, r6, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_try_salir
@  El bloque protegido termino sin errores: desapila su nota y la libera.
@ ────────────────────────────────────────────────────────────────────────
cps_try_salir:
    push {r4, lr}
    ldr r1, =cps_manejadores
    ldr r0, [r1]                    @ la nota de arriba
    ldr r2, [r0, #0]
    str r2, [r1]                    @ la anterior queda arriba
    bl free
    pop {r4, pc}

@ ────────────────────────────────────────────────────────────────────────
@  cps_lanzar
@  Recibe: r0, el mensaje del error. No vuelve.
@
@  Sin notas, nadie atrapa el error: imprime "Error: " y el mensaje en la
@  salida de errores y termina con codigo 1, igual que un error sin catch en
@  el interprete. Con una nota, la desapila, deja el mensaje en
@  cps_excepcion, restaura el sp y el fp que guardo el try y salta al catch.
@  El catch lee el mensaje de cps_excepcion y lo guarda en su variable.
@ ────────────────────────────────────────────────────────────────────────
cps_lanzar:
    ldr r1, =cps_manejadores
    ldr r2, [r1]                    @ r2: la nota de arriba
    cmp r2, #0
    beq cps_terminar_con_error

    ldr r3, =cps_excepcion
    str r0, [r3]                    @ el mensaje, para el catch

    ldr r3, [r2, #0]
    str r3, [r1]                    @ la anterior queda arriba
    ldr r4, [r2, #4]                @ r4: el sp del try
    ldr r5, [r2, #8]                @ r5: su fp
    ldr r6, [r2, #12]               @ r6: la etiqueta del catch
    mov r0, r2
    bl free                         @ sp sigue alineado: se entro con bl

    mov sp, r4                      @ los registros de en medio se descartan
    mov fp, r5
    bx r6

@ ────────────────────────────────────────────────────────────────────────
@  cps_terminar_con_error
@  Recibe: r0, el mensaje. No vuelve.
@
@  Primero vacia lo que printf tenga pendiente: la salida normal se guarda en
@  un bufer y la de errores no, y sin esto el error saldria antes que los
@  print que lo precedieron.
@ ────────────────────────────────────────────────────────────────────────
cps_terminar_con_error:
    push {r4, lr}
    mov r4, r0                      @ r4: el mensaje
    mov r0, #0                      @ fflush(NULL) vacia todas las salidas
    bl fflush
    mov r0, #2                      @ dprintf(2, "Error: %s\n", mensaje): 2 es
    ldr r1, =.Lformato_error        @ la salida de errores
    mov r2, r4
    bl dprintf
    mov r0, #1
    bl exit
