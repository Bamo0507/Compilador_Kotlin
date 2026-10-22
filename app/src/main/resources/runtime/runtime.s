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
