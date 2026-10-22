package org.compiler.backend.models

// Los registros de ARM de 32 bits que usa el backend, con su nombre en el ensamblador.
// r9 no aparece: algunas plataformas lo reservan.
enum class Register(val text: String) {
    // Argumentos y resultado al llamar al runtime y a libc. Quien es llamado puede
    // pisarlos sin avisar.
    R0("r0"),
    R1("r1"),
    R2("r2"),
    R3("r3"),

    // Los que reparte getReg. Quien es llamado debe devolverlos intactos, asi que un
    // valor que vive en ellos sobrevive a una llamada.
    R4("r4"),
    R5("r5"),
    R6("r6"),
    R7("r7"),
    R8("r8"),
    R10("r10"),

    // El inicio del registro de activacion actual.
    FP("fp"),

    // De paso, para calcular una direccion por un instante.
    IP("ip"),

    // El tope de la pila.
    SP("sp"),

    // A donde se vuelve despues de una llamada.
    LR("lr"),

    // La siguiente instruccion.
    PC("pc");

    companion object {
        // Los registros donde getReg guarda los valores del programa.
        val VALUE_REGISTERS = listOf(R4, R5, R6, R7, R8, R10)
    }
}

// Los registros de punto flotante de doble precision. Los flotantes no pasan por getReg:
// cada instruccion carga sus operandos en d1 y d2 y deja el resultado en d0.
enum class FloatRegister(val text: String) {
    D0("d0"),
    D1("d1"),
    D2("d2")
}
