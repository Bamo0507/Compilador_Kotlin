package org.compiler.interpreter

/**
 * Los valores visibles en un punto de la ejecucion.
 *
 * Es el gemelo en ejecucion de Scope: misma estructura de padre e hijos, pero
 * guarda valores en vez de tipos. Scope dice QUE TIPO tiene cada nombre;
 * Environment dice QUE VALE.
 */
class Environment(private val parent: Environment? = null) {

    private val values = mutableMapOf<String, RuntimeValue>()

    fun define(name: String, value: RuntimeValue) {
        values[name] = value
    }

    // Busca en este nivel y sube. Igual que Scope.lookup.
    fun get(name: String): RuntimeValue? =
        values[name] ?: parent?.get(name)

    // Asigna a la variable EXISTENTE, buscando hacia arriba.
    fun assign(name: String, value: RuntimeValue): Boolean = when {
        values.containsKey(name) -> { values[name] = value; true }
        parent != null -> parent.assign(name, value)
        else -> false
    }

    fun child(): Environment = Environment(parent = this)
}
