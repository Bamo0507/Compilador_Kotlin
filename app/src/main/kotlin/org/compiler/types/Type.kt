package org.compiler.types

/**
 * Un tipo de dato de SQL. Sellado para que un `when` sin `else` no compile si se
 * olvida alguno.
 *
 * Tres llevan parametros, asi que son `data class` y no `data object`: dos
 * DECIMAL con distinta escala son tipos distintos, y compararlos es comparar sus
 * partes.
 */
sealed interface Type {
    // Como se escribe el tipo en un mensaje de error y en el .json de esquema.
    val name: String
}

// Numericos -----------------------------------------------------------------

data object IntType : Type {
    override val name = "INT"
}

// IEEE-754, inexacto: 0.1 + 0.2 no da 0.3. Para medidas y promedios.
data object FloatType : Type {
    override val name = "FLOAT"
}

// Exacto, respaldado por BigDecimal. Para dinero.
//
// DECIMAL y NUMERIC son EL MISMO tipo con dos nombres, como en el estandar. Si
// fueran tipos distintos, DECIMAL(10,2) y NUMERIC(10,2) no podrian compararse.
data class DecimalType(val precision: Int, val scale: Int) : Type {
    override val name = "DECIMAL($precision,$scale)"
}

// Caracter ------------------------------------------------------------------

// Largo fijo: se rellena con espacios al leerlo del CSV.
data class CharType(val length: Int) : Type {
    override val name = "CHAR($length)"
}

// Largo variable con tope. Pasarse del tope es error, no truncamiento.
data class VarcharType(val maxLength: Int) : Type {
    override val name = "VARCHAR($maxLength)"
}

data object TextType : Type {
    override val name = "TEXT"
}

// Temporales ----------------------------------------------------------------

data object DateType : Type {
    override val name = "DATE"
}

data object TimeType : Type {
    override val name = "TIME"
}

// Resto ---------------------------------------------------------------------

data object BooleanType : Type {
    override val name = "BOOLEAN"
}

// El tipo del literal NULL. Compatible con todos los demas.
data object NullType : Type {
    override val name = "NULL"
}

// Se devuelve cuando ya se reporto un error. Corta cascadas: cualquier operacion
// con ErrorType se acepta en silencio, asi un script malo reporta una sola vez.
data object ErrorType : Type {
    override val name = "<error>"
}
