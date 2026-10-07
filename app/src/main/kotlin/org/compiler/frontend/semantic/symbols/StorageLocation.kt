package org.compiler.frontend.semantic.symbols

// Donde vive un simbolo en memoria mientras el programa corre.
sealed interface StorageLocation {

    // Datos estaticos: las globales, que existen todo el programa y hay una sola de
    // cada una. El desplazamiento es desde el inicio de esa zona.
    data class Static(val offset: Int) : StorageLocation

    // La pila: un parametro o una local, dentro del registro de activacion de su
    // funcion. El desplazamiento es desde el inicio del registro.
    data class Frame(val offset: Int) : StorageLocation
}
