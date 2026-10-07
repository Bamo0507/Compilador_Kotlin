package org.compiler.frontend.intermediate.models

/**
 * La forma del registro de activacion de una funcion: que campo va en que
 * desplazamiento y cuanto mide la hoja completa. Se calcula sin ejecutar nada, y cada
 * llamada a la funcion crea una hoja con esta forma.
 */
data class ActivationRecordLayout(
    val function: FunctionLabel,

    // En el orden de la diapositiva 21: parametros, valor devuelto, enlaces, direccion
    // de retorno, locales y temporales.
    val fields: List<ActivationRecordField>,

    // Redondeado a 8, la alineacion de la pila en ARM de 32 bits.
    val size: Int
)

// Un renglon del registro. El nombre es texto porque es lo que muestran el IDE y el
// documento: "a", "valor devuelto", "enlace de control"...
data class ActivationRecordField(
    val name: String,
    val offset: Int,
    val size: Int
)
