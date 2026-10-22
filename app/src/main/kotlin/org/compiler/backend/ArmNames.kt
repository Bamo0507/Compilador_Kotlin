package org.compiler.backend

import org.compiler.frontend.intermediate.StorageAllocator
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Label

/**
 * Como se llama en el .s cada cosa que tiene nombre en el TAC. Todas las etiquetas se
 * arman aqui, para que dos partes del generador nunca escriban la misma de forma
 * distinta.
 *
 * El ensamblador acepta puntos y signos de dolar dentro de una etiqueta, asi que los
 * nombres del TAC como `externa.sumar` o `Perro.$init` se usan tal cual.
 */
object ArmNames {

    // Las etiquetas que empiezan con .L son locales al archivo: no aparecen en la tabla
    // de simbolos del ejecutable.
    private const val LOCAL_PREFIX = ".L"

    // El prefijo de todo lo que nombra el programa. Evita chocar con libc: una funcion
    // de Compiscript llamada `exit` o `printf` romperia el enlace.
    private const val PROGRAM_PREFIX = "cps_"

    // El punto de entrada que busca libc al arrancar el programa.
    const val ENTRY_POINT = "main"

    // La zona de las variables globales, en .bss.
    const val STATIC_ZONE = "${PROGRAM_PREFIX}estatico"

    // Las rutinas del runtime, escritas a mano en runtime.s.
    const val PRINT_INTEGER = "${PROGRAM_PREFIX}imprimir_entero"
    const val PRINT_FLOAT = "${PROGRAM_PREFIX}imprimir_flotante"
    const val PRINT_STRING = "${PROGRAM_PREFIX}imprimir_string"
    const val PRINT_BOOLEAN = "${PROGRAM_PREFIX}imprimir_booleano"
    const val COMPARE_STRINGS = "${PROGRAM_PREFIX}comparar_strings"

    // `factorial` -> `cps_factorial`; el main implicito -> `main`.
    fun function(function: FunctionLabel): String =
        if (isMain(function)) ENTRY_POINT else PROGRAM_PREFIX + function.name

    // `L7` dentro de `factorial` -> `.Lfactorial_7`. El numero ya es unico en todo el
    // programa; el nombre de la funcion es para leer el .s y saber de quien es el salto.
    fun label(function: FunctionLabel, label: Label): String {
        val owner = if (isMain(function)) ENTRY_POINT else function.name
        return "$LOCAL_PREFIX${owner}_${label.index}"
    }

    // La tabla de metodos de una clase, en .data: `cps_vtable_Perro`.
    fun virtualTable(className: String): String = "${PROGRAM_PREFIX}vtable_$className"

    // Un literal de texto, en .rodata: `.Lstr_2`.
    fun string(index: Int): String = "${LOCAL_PREFIX}str_$index"

    // Una constante flotante, en .rodata: `.Lfloat_1`. Va en memoria porque un flotante
    // de 8 bytes no cabe dentro de una instruccion.
    fun float(index: Int): String = "${LOCAL_PREFIX}float_$index"

    private fun isMain(function: FunctionLabel): Boolean =
        function.name == StorageAllocator.MAIN_LABEL
}
