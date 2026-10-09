package org.compiler.frontend.intermediate.models

data class ClassLayout(
    val className: String,

    // Cuanto pide `new` al monticulo, con la casilla de la tabla incluida.
    val size: Int,

    // La posicion de cada entrada es su indice en la lista.
    val methods: List<FunctionLabel>,

    // Cada casilla del objeto en orden: la de la tabla, los campos heredados
    val fields: List<ActivationRecordField>
) {
    // La posicion de un metodo en la tabla, por su nombre. -1 si no esta.
    fun slotOf(methodName: String): Int =
        methods.indexOfFirst { it.name.substringAfterLast('.') == methodName }
}
