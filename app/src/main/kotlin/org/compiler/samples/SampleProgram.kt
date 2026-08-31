package org.compiler.samples

/**
 * Un programa de ejemplo cargado desde los recursos.
 *
 * Son los mismos .cps de la bateria de pruebas: viven en src/main/resources y no en
 * src/test/resources para que la GUI tambien los pueda leer, y asi hay una sola
 * copia que el IDE muestra y las pruebas verifican.
 */
data class SampleProgram(
    val id: String,
    val name: String,
    val group: SampleGroup,
    val source: String
)

enum class SampleGroup(val label: String) {
    // Los dos primeros no salen de un archivo: son los puntos de partida del editor.
    STARTER("Punto de partida"),
    VALID("Compilan sin errores"),
    INVALID("Producen errores")
}
