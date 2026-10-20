@ Programa minimo para probar el entorno: imprime un entero y un flotante con printf
@ y termina con codigo 0.
    .syntax unified
    .arm
    .fpu vfp

    .section .rodata
formato:
    .asciz "int=%d double=%f\n"
    .balign 8
tres_y_medio:
    .double 3.5

    .text
    .global main
main:
    push {fp, lr}
    mov fp, sp

    ldr r0, =formato
    mov r1, #42
    @ printf recibe argumentos variables: el double va en r2 y r3, no en d0.
    ldr ip, =tres_y_medio
    vldr d0, [ip]
    vmov r2, r3, d0
    bl printf

    mov r0, #0
    pop {fp, pc}

    .section .note.GNU-stack,"",%progbits
