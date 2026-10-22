package org.compiler.backend.models

// Una linea de un archivo de ensamblador .s. Cada linea es una de cuatro cosas: una
// directiva, una etiqueta, una instruccion o un comentario.
sealed interface ArmLine {

    /**
     * Una indicacion para el ensamblador. No se ejecuta: le dice como organizar el
     * archivo o cuanto espacio reservar. Se escribe con un punto al inicio.
     *
     * Las que usa el backend, agrupadas por para que sirven:
     *
     * Secciones, los cajones del programa. Todo lo que sigue a una va a ese cajon:
     *   .data              datos con valor inicial: las tablas de metodos
     *   .bss               datos que empiezan en cero: la zona de las globales
     *   .section .rodata   datos de solo lectura: los textos y los flotantes constantes
     *   .text              el codigo
     *
     * Datos, lo que se escribe dentro de un cajon:
     *   .asciz "hola"      un texto terminado con un byte en cero, como lo espera C
     *   .space 8           8 bytes en cero
     *   .word cps_f        un valor de 4 bytes: una direccion o un entero
     *   .double 3.5        un flotante de 8 bytes
     *
     * Alineacion:
     *   .balign 8          avanza hasta la siguiente direccion multiplo de 8
     *
     * Configuracion del archivo, al inicio:
     *   .syntax unified    la sintaxis moderna de ARM
     *   .arm               instrucciones de 32 bits, no las cortas de Thumb
     *   .fpu vfp           habilita la unidad de punto flotante
     *   .global main       hace visible una etiqueta fuera del archivo: libc busca main
     *
     * `name` va sin el punto y los argumentos ya formateados: Directive("asciz",
     * listOf("\"hola\"")) se imprime `.asciz "hola"`.
     */
    data class Directive(
        val name: String,
        val arguments: List<String> = emptyList()
    ) : ArmLine

    // Un nombre para una direccion: el inicio de una funcion, el destino de un salto o
    // la ubicacion de un dato. Se imprime pegada al margen y con dos puntos: `main:`.
    data class LabelLine(val name: String) : ArmLine

    /**
     * Una instruccion de la maquina: `add r4, r5, r6`.
     *
     * mnemonic: el nombre de la instruccion, con su sufijo de condicion si lleva uno,
     *   como `ldr`, `strb` o `blt`.
     * operands: los operandos ya formateados, en el orden de ARM, donde el destino va
     *   primero: registros (`r4`), constantes (`#5`) o direcciones de memoria
     *   (`[fp, #-8]`).
     * comment: una nota opcional al final de la linea, como la variable a la que se
     *   accede, para poder leer el .s junto al TAC.
     */
    data class Instruction(
        val mnemonic: String,
        val operands: List<String> = emptyList(),
        val comment: String? = null
    ) : ArmLine

    // Una linea que solo es comentario. En ARM empieza con @: el # marca las
    // constantes dentro de una instruccion.
    data class Comment(val text: String) : ArmLine
}

// Un archivo .s completo: las lineas que genera el compilador y, al final, el runtime,
// que es texto escrito a mano y va tal cual. Se guardan aparte para poder mostrar o
// comparar solo lo que se genero.
data class AssemblyProgram(
    val lines: List<ArmLine>,
    val runtime: String = ""
)
