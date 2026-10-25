// El generador de ARM: los nombres, las secciones de datos, el registro de activacion
// visto desde fp, el prologo y el epilogo, y print con constantes.
package org.compiler

import org.compiler.backend.ArmGenerator
import org.compiler.backend.ArmNames
import org.compiler.backend.ArmPrinter
import org.compiler.backend.Frame
import org.compiler.backend.UntranslatedInstruction
import org.compiler.backend.models.AssemblyProgram
import org.compiler.frontend.intermediate.models.Arithmetic
import org.compiler.frontend.intermediate.models.ArithmeticOperator
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.FunctionLabel
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.frontend.intermediate.models.VirtualTableDefinition
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmGeneratorTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun assemblyOf(source: String): AssemblyProgram {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        val tac = assertNotNull(result.tac)
        return ArmGenerator(tac, assertNotNull(result.storageLayout)).generate()
    }

    // Las lineas generadas, sin sangria y sin el runtime.
    private fun linesOf(source: String): List<String> = linesOf(assemblyOf(source))

    private fun linesOf(program: AssemblyProgram): List<String> =
        ArmPrinter.print(program, includeRuntime = false).lines().map { it.trim() }

    // Un main vacio con estas instrucciones adentro, y la zona estatica del tamaño dado.
    // Sirve para probar una parte del generador sin que el resto del programa necesite
    // instrucciones que todavia no se traducen.
    private fun linesOf(staticSize: Int, vararg quadruples: Quadruple): List<String> {
        val result = CompilerPipeline.compile("", execute = false)
        val tac = assertNotNull(result.tac)
        val (begin, end) = tac.instructions
        val program = tac.copy(instructions = listOf(begin) + quadruples + listOf(end))
        val layout = assertNotNull(result.storageLayout).copy(staticSize = staticSize)
        return linesOf(ArmGenerator(program, layout).generate())
    }

    // Las instrucciones que siguen a un comentario del TAC, hasta el siguiente comentario.
    private fun translationOf(lines: List<String>, tacLine: String): List<String> {
        val start = lines.indexOf("@ $tacLine")
        assertTrue(start >= 0, "No esta '$tacLine' en:\n${lines.joinToString("\n")}")
        return lines.drop(start + 1).takeWhile { !it.startsWith("@") }
    }

    // ── Los nombres ────────────────────────────────────────────────────────

    @Test
    fun `cada cosa del TAC tiene su nombre en el ensamblador`() {
        assertEquals("main", ArmNames.function(FunctionLabel("\$main")))
        assertEquals("cps_factorial", ArmNames.function(FunctionLabel("factorial")))
        assertEquals("cps_Perro.hablar", ArmNames.function(FunctionLabel("Perro.hablar")))
        assertEquals(".Lfactorial_7", ArmNames.label(FunctionLabel("factorial"), Label(7)))
        assertEquals(".Lmain_2", ArmNames.label(FunctionLabel("\$main"), Label(2)))
        assertEquals("cps_vtable_Perro", ArmNames.virtualTable("Perro"))
        assertEquals(".Lstr_2", ArmNames.string(2))
        assertEquals(".Lfloat_1", ArmNames.float(1))
    }

    // ── El registro de activacion visto desde fp ───────────────────────────

    // factorial(n): n en 0, valor devuelto en 4, enlaces en 8 y 12, direccion de
    // retorno en 16, y sus dos temporales despues. La casilla d de tamaño s vive en
    // fp - d - s.
    //
    // La parte fija llega hasta el final de la direccion de retorno, o sea 20, y se
    // redondea a 24: si no, quien llama dejaria sp en un multiplo de 4 durante toda la
    // funcion. Las casillas no se mueven, porque se cuentan desde fp.
    @Test
    fun `el registro de factorial visto desde fp`() {
        val result = CompilerPipeline.compile(
            "function factorial(n: integer): integer { if (n <= 1) { return 1; } " +
                "return n * factorial(n - 1); } print(factorial(5));",
            execute = false
        )
        val record = assertNotNull(result.tac).activationRecords
            .first { it.function.name == "factorial" }
        val frame = Frame(record)

        assertEquals(40, frame.size)
        assertEquals(24, frame.fixedPartSize)
        assertEquals(-12, frame.controlLinkOffset)
        assertEquals(-20, frame.returnAddressOffset)
        assertEquals(-4, frame.fpOffsetOf(offset = 0, size = 4))
        assertEquals(-40, frame.fpOffsetOf(offset = 32, size = 8))
    }

    // ── Las secciones de datos ─────────────────────────────────────────────

    // Una entrada de 4 bytes por metodo, en el orden de la tabla.
    @Test
    fun `cada clase tiene su tabla de metodos en data`() {
        val lines = linesOf(
            staticSize = 0,
            VirtualTableDefinition("Animal", listOf(FunctionLabel("Animal.hablar"))),
            VirtualTableDefinition(
                "Perro", listOf(FunctionLabel("Perro.hablar"), FunctionLabel("Animal.comer"))
            )
        )

        assertEquals(
            listOf(
                ".data", ".balign 4",
                "cps_vtable_Animal:", ".word cps_Animal.hablar",
                "cps_vtable_Perro:", ".word cps_Perro.hablar", ".word cps_Animal.comer"
            ),
            lines.subList(lines.indexOf(".data"), lines.indexOf(".text"))
        )
    }

    // Dos globales enteras: 8 bytes en ceros, alineados a 8.
    @Test
    fun `las globales van en bss`() {
        val lines = linesOf(staticSize = 8)

        val start = lines.indexOf(".bss")
        assertEquals(
            listOf(".bss", ".balign 8", "cps_estatico:", ".space 8"),
            lines.subList(start, start + 4)
        )
    }

    // Un texto repetido comparte su etiqueta; el flotante va despues, alineado a 8.
    @Test
    fun `los textos y los flotantes van una sola vez en rodata`() {
        val lines = linesOf("""print("hola"); print("hola"); print("chao"); print(3.5);""")

        val start = lines.indexOf(".section .rodata")
        assertEquals(
            listOf(
                ".section .rodata",
                ".Lstr_1:", ".asciz \"hola\"",
                ".Lstr_2:", ".asciz \"chao\"",
                ".balign 8",
                ".Lfloat_1:", ".double 3.5"
            ),
            lines.subList(start, start + 8)
        )
    }

    @Test
    fun `un programa sin clases ni globales ni literales no tiene esas secciones`() {
        val lines = linesOf("")

        assertTrue(".data" !in lines && ".bss" !in lines && ".section .rodata" !in lines)
    }

    // Las comillas, las barras y los saltos de linea se escapan como en C.
    @Test
    fun `los textos se escapan como en C`() {
        val text = "dijo \"hola\"\\fin\n"
        val lines = linesOf(staticSize = 0, Print(Constant(text), OperandKind.STRING))

        assertTrue(".asciz \"dijo \\\"hola\\\"\\\\fin\\n\"" in lines, lines.joinToString("\n"))
    }

    // ── El prologo y el epilogo ────────────────────────────────────────────

    // main guarda los registros de getReg, que libc espera intactos, reserva su parte
    // fija, porque libc no se la reservo, y devuelve 0.
    @Test
    fun `main guarda los enlaces al entrar y devuelve 0 al salir`() {
        val lines = linesOf("")

        assertEquals(
            listOf(
                ".global main",
                "main:",
                "push {r4, r5, r6, r7, r8, r10}",
                "sub sp, sp, #16",
                "add ip, sp, #16",
                "str fp, [ip, #-4]  @ enlace de control",
                "str lr, [ip, #-12]  @ direccion de retorno",
                "mov fp, ip",
                "sub sp, fp, #16"
            ),
            translationOf(lines, "begin_func \$main, 16")
        )

        // El epilogo se escribe una sola vez, detras de la etiqueta de salida a la que
        // salta cada return.
        assertEquals(
            listOf(
                ".Lmain_fin:",
                "mov r0, #0",
                "ldr lr, [fp, #-12]",
                "sub sp, fp, #16",
                "ldr fp, [fp, #-4]",
                "add sp, sp, #16",
                "pop {r4, r5, r6, r7, r8, r10}",
                "bx lr"
            ),
            translationOf(lines, "end_func \$main").takeWhile { !it.startsWith(".section") }
        )
    }

    // Una funcion no reserva su parte fija: ya se la reservo quien la llama.
    @Test
    fun `una funcion empieza en el tope que le dejo reservado quien la llama`() {
        val lines = linesOf("function f() { }")

        assertEquals(
            listOf(
                "cps_f:",
                "add ip, sp, #16",
                "str fp, [ip, #-4]  @ enlace de control",
                "str lr, [ip, #-12]  @ direccion de retorno",
                "mov fp, ip",
                "sub sp, fp, #16"
            ),
            translationOf(lines, "begin_func f, 16")
        )
    }

    // El residuo entre flotantes no existe en el lenguaje, porque el verificador lo
    // rechaza. Escrito a mano en el TAC, el generador no lo traduce y lo dice.
    @Test
    fun `una instruccion que no se traduce falla con su texto`() {
        val modulo = Arithmetic(
            Temporary(1), Constant(5.5), ArithmeticOperator.MODULO, Constant(2.0),
            OperandKind.FLOAT
        )

        val error = assertFailsWith<UntranslatedInstruction> { linesOf(0, modulo) }

        assertTrue(error.message.orEmpty().startsWith("Todavía no se traduce: t1 = 5.5 %"))
    }

    // ── print ──────────────────────────────────────────────────────────────

    // Cada tipo va en el registro donde lo espera su rutina.
    @Test
    fun `print de una constante carga el valor y llama a su rutina`() {
        val lines = linesOf("""print(42); print("hola"); print(true); print(false); print(3.5);""")

        assertEquals(
            listOf("mov r0, #42", "bl cps_imprimir_entero"),
            translationOf(lines, "print_i 42")
        )
        assertEquals(
            listOf("ldr r0, =.Lstr_1", "bl cps_imprimir_string"),
            translationOf(lines, "print_s \"hola\"")
        )
        assertEquals(
            listOf("mov r0, #1", "bl cps_imprimir_booleano"),
            translationOf(lines, "print_b true")
        )
        assertEquals(
            listOf("mov r0, #0", "bl cps_imprimir_booleano"),
            translationOf(lines, "print_b false")
        )
        assertEquals(
            listOf("ldr ip, =.Lfloat_1", "vldr d0, [ip]", "bl cps_imprimir_flotante"),
            translationOf(lines, "print_f 3.5")
        )
    }

    // Un entero que no cabe en la instruccion: su complemento si cabe, o el literal pool.
    @Test
    fun `los enteros que no caben en la instruccion se cargan de otra forma`() {
        val lines = linesOf("print(-7); print(100001);")

        assertEquals("mvn r0, #6", translationOf(lines, "print_i -7").first())
        assertEquals("ldr r0, =100001", translationOf(lines, "print_i 100001").first())
    }

    // El programa completo corre en el contenedor e imprime lo mismo que el interprete.
    @Test
    fun `print de constantes imprime lo mismo que el interprete`() {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")
        val source = """
            print("hola");
            print(42);
            print(-7);
            print(100001);
            print(3.5);
            print(2.0);
            print(true);
            print(false);
        """.trimIndent()

        val interpreted = CompilerPipeline.compile(source).execution?.output
        val execution = ArmContainer.run("print_constantes", ArmPrinter.print(assemblyOf(source)))

        assertEquals(0, execution.exitCode, execution.output)
        assertEquals(interpreted, execution.output.lines().dropLast(1))
    }
}
