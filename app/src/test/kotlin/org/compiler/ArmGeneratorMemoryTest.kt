// El monticulo en ARM: alloc, los campos y los elementos, las llamadas a metodos por la
// tabla, las listas que crecen, la concatenacion y que pasa cuando no hay memoria.
package org.compiler

import org.compiler.backend.ArmGenerator
import org.compiler.backend.ArmPrinter
import org.compiler.backend.models.AssemblyProgram
import org.compiler.frontend.intermediate.models.Allocate
import org.compiler.frontend.intermediate.models.Constant
import org.compiler.frontend.intermediate.models.Copy
import org.compiler.frontend.intermediate.models.FunctionEnd
import org.compiler.frontend.intermediate.models.Goto
import org.compiler.frontend.intermediate.models.Label
import org.compiler.frontend.intermediate.models.LabelDefinition
import org.compiler.frontend.intermediate.models.OperandKind
import org.compiler.frontend.intermediate.models.Print
import org.compiler.frontend.intermediate.models.Quadruple
import org.compiler.frontend.intermediate.models.Temporary
import org.compiler.runtime.CompilerPipeline
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmGeneratorMemoryTest {

    // ── Infraestructura ────────────────────────────────────────────────────

    private fun assemblyOf(source: String): AssemblyProgram {
        val result = CompilerPipeline.compile(source, execute = false)
        assertTrue(result.errors.isEmpty(), "Errores: ${result.errors.map { it.message }}")
        return assertNotNull(result.assembly, "El backend no tradujo:\n$source")
    }

    // El main de un programa con un temporal, con estas instrucciones escritas a mano en
    // vez de su cuerpo. Sirve para probar casos que un programa de Compiscript no produce
    // facilmente.
    private fun assemblyOf(vararg quadruples: Quadruple): AssemblyProgram {
        val result = CompilerPipeline.compile(
            "let a: integer = 1; print(a + a);", execute = false
        )
        val tac = assertNotNull(result.tac)
        val begin = tac.instructions.first()
        val end = tac.instructions.first { it is FunctionEnd }
        val program = tac.copy(instructions = listOf(begin) + quadruples + listOf(end))
        return ArmGenerator(program, assertNotNull(result.storageLayout)).generate()
    }

    // Las lineas generadas, sin sangria, sin comentarios al final y sin el runtime.
    private fun linesOf(program: AssemblyProgram): List<String> =
        ArmPrinter.print(program, includeRuntime = false).lines()
            .map { it.trim().substringBefore("  @") }

    private fun linesOf(source: String): List<String> = linesOf(assemblyOf(source))

    // Las instrucciones que siguen a un comentario del TAC, hasta el siguiente comentario.
    private fun translationOf(lines: List<String>, tacLine: String): List<String> {
        val start = lines.indexOf("@ $tacLine")
        assertTrue(start >= 0, "No esta '$tacLine' en:\n${lines.joinToString("\n")}")
        return lines.drop(start + 1).takeWhile { !it.startsWith("@") }
    }

    // ── alloc ──────────────────────────────────────────────────────────────

    // El tamaño va en r0, cps_reservar devuelve el bloque en r0, y de ahi pasa al
    // registro del temporal.
    @Test
    fun `alloc pide el bloque a cps_reservar`() {
        val lines = linesOf("let xs: integer[] = [];")

        assertEquals(
            listOf("mov r0, #12", "bl cps_reservar", "mov r4, r0"),
            translationOf(lines, "t1 = alloc 12")
        )
    }

    // new escribe en la casilla 0 la direccion de la tabla de metodos de su clase.
    @Test
    fun `la tabla de metodos se carga por su etiqueta`() {
        val lines = linesOf("class Perro { } let perro: Perro = new Perro();")

        assertEquals(
            listOf("ldr r1, =cps_vtable_Perro", "str r1, [r4]"),
            translationOf(lines, "t1[0] = vtable.Perro")
        )
    }

    // ── Campos y elementos ─────────────────────────────────────────────────

    // Un desplazamiento constante va dentro de la instruccion.
    @Test
    fun `un campo se lee con su desplazamiento constante`() {
        val lines = linesOf(
            "class Caja { let valor: integer; } let caja: Caja = new Caja(); print(caja.valor);"
        )

        assertEquals("ldr r4, [r4, #4]", translationOf(lines, "t1 = caja[4]").last())
    }

    // Un booleano mide un byte dentro del objeto: escribirle cuatro pisaria el campo
    // siguiente.
    @Test
    fun `un campo booleano se escribe y se lee con un solo byte`() {
        val lines = linesOf(
            "class Foco { let encendido: boolean; } let foco: Foco = new Foco(); " +
                "foco.encendido = true; print(foco.encendido);"
        )

        assertTrue(translationOf(lines, "foco[4] = true").last().startsWith("strb "))
        assertTrue(translationOf(lines, "t1 = foco[4]").last().startsWith("ldrb "))
    }

    // El desplazamiento de un elemento depende del indice, que se conoce al ejecutar: va
    // en un registro.
    @Test
    fun `un elemento con indice variable usa el desplazamiento en un registro`() {
        val lines = linesOf(
            "let xs: integer[] = [1, 2]; let i: integer = 1; print(xs[i]);"
        )

        val load = lines.single { it.matches(Regex("""ldr r\d+, \[r\d+, r\d+]""")) }
        assertTrue(load.isNotEmpty())
    }

    // vldr no acepta un registro como desplazamiento: la direccion se suma antes en ip.
    @Test
    fun `un flotante con indice variable suma la direccion en ip`() {
        val lines = linesOf(
            "let xs: float[] = [1.5, 2.5]; let i: integer = 1; print(xs[i]);"
        )

        val sum = lines.indexOfFirst { it.matches(Regex("""add ip, r\d+, r\d+""")) }
        assertTrue(sum >= 0, lines.joinToString("\n"))
        assertEquals("vldr d0, [ip]", lines[sum + 1])
    }

    // ── Metodos ────────────────────────────────────────────────────────────

    // La direccion del metodo sale de la tabla del objeto, asi que el salto es a un
    // registro.
    @Test
    fun `una llamada a un metodo salta a la direccion de la tabla`() {
        val lines = linesOf(
            "class Perro { function hablar(): string { return \"guau\"; } } " +
                "let perro: Perro = new Perro(); print(perro.hablar());"
        )

        val call = translationOf(lines, "t1 = call t1, 1")
        assertTrue(call.any { it.matches(Regex("""blx r\d+""")) }, call.joinToString("\n"))
        assertFalse(call.any { it.startsWith("bl ") })
    }

    // ── Listas ─────────────────────────────────────────────────────────────

    // La rutina devuelve la casilla nueva en r0 y el valor se escribe ahi: un entero
    // mide 4 bytes, un flotante 8.
    @Test
    fun `push pide la casilla nueva y escribe el valor en ella`() {
        val lines = linesOf(
            "let xs: integer[] = []; xs.push(7); let ys: float[] = []; ys.push(2.5);"
        )

        val integerPush = translationOf(lines, "push_i xs, 7")
        assertEquals(
            listOf("mov r1, #4", "bl cps_lista_agregar", "mov r1, #7", "str r1, [r0]"),
            integerPush.takeLast(4)
        )
        val floatPush = translationOf(lines, "push_f ys, 2.5")
        assertTrue("mov r1, #8" in floatPush)
        assertEquals("vstr d0, [r0]", floatPush.last())
    }

    // ── Strings ────────────────────────────────────────────────────────────

    @Test
    fun `concat llama a cps_concatenar con los dos textos`() {
        val lines = linesOf(
            "let nombre: string = \"Ana\"; let saludo: string = \"Hola \" + nombre;"
        )

        val concat = translationOf(lines, "t1 = \"Hola \" concat nombre")
        assertEquals("ldr r0, =.Lstr_2", concat.first())
        assertTrue("bl cps_concatenar" in concat)
    }

    // ── Los flotantes y las libretas ───────────────────────────────────────

    // t1 era un entero en un registro y despues recibe un flotante, que va directo a su
    // casilla. El registro queda con el valor viejo: si la libreta no lo olvidara, el
    // vaciado antes del salto lo guardaria encima del flotante.
    @Test
    fun `un flotante escrito en memoria invalida el registro con el valor viejo`() {
        val t1 = Temporary(1)
        val lines = linesOf(
            assemblyOf(
                Copy(t1, Constant(7L), OperandKind.INTEGER),
                Print(t1, OperandKind.INTEGER),
                Copy(t1, Constant(2.5), OperandKind.FLOAT),
                Goto(Label(1)),
                LabelDefinition(Label(1)),
                Print(t1, OperandKind.FLOAT)
            )
        )

        assertEquals(listOf("b .Lmain_1"), translationOf(lines, "goto L1"))
    }

    // ── En el contenedor ───────────────────────────────────────────────────

    private fun assertSameOutputAsInterpreter(name: String, source: String) {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")
        val result = CompilerPipeline.compile(source)
        val assembly = assertNotNull(result.assembly, "El backend no tradujo:\n$source")

        val execution = ArmContainer.run(name, ArmPrinter.print(assembly))

        assertEquals(0, execution.exitCode, execution.output)
        assertEquals(result.execution?.output, execution.output.lines().dropLast(1))
    }

    // La lista se muda de capacidad 4 a 8, 16, ... hasta 1024, y ningun elemento se
    // pierde en las mudanzas.
    @Test
    fun `una lista crece hasta mil elementos sin perder ninguno`() {
        assertSameOutputAsInterpreter(
            "mil_elementos",
            """
            let xs: integer[] = [];
            let i: integer = 0;
            while (i < 1000) { xs.push(i); i = i + 1; }
            let suma: integer = 0;
            foreach (x in xs) { suma = suma + x; }
            print(xs.length);
            print(suma);
            print(xs[999]);
            """.trimIndent()
        )
    }

    // La copia apunta a la misma ficha: despues de la mudanza del contenido sigue viendo
    // todo, porque lo que cambia es la direccion del contenido dentro de la ficha.
    @Test
    fun `una copia hecha antes de la mudanza ve los elementos nuevos`() {
        assertSameOutputAsInterpreter(
            "copia_lista",
            """
            let xs: float[] = [0.5];
            let copia: float[] = xs;
            xs.push(1.5); xs.push(2.5); xs.push(3.5); xs.push(4.5);
            print(copia.length);
            print(copia[4]);
            let marcas: boolean[] = [];
            marcas.push(true); marcas.push(false); marcas.push(true);
            print(marcas[2]);
            """.trimIndent()
        )
    }

    @Test
    fun `los objetos y los textos imprimen lo mismo que el interprete`() {
        assertSameOutputAsInterpreter(
            "objetos",
            """
            class Animal {
              let nombre: string;
              let vivo: boolean;
              function constructor(nombre: string) { this.nombre = nombre; this.vivo = true; }
              function hablar(): string { return this.nombre + " hace ruido."; }
            }
            class Perro : Animal {
              let peso: float;
              function hablar(): string { return this.nombre + " ladra."; }
            }
            let animales: Animal[] = [new Animal("Gato"), new Perro("Toby")];
            foreach (animal in animales) { print(animal.hablar()); print(animal.vivo); }
            """.trimIndent()
        )
    }

    // calloc no puede dar 4000 millones de bytes en un proceso de 32 bits: el programa
    // termina con el mensaje y con codigo 1, en vez de seguir con una direccion nula.
    @Test
    fun `sin memoria el programa termina con codigo 1`() {
        assumeTrue(ArmContainer.isAvailable, "Sin Docker o sin la imagen compiscript-arm")
        val program = assemblyOf(
            Print(Constant(1L), OperandKind.INTEGER),
            Allocate(Temporary(1), Constant(4_000_000_000L)),
            Print(Constant(2L), OperandKind.INTEGER)
        )

        val execution = ArmContainer.run("sin_memoria", ArmPrinter.print(program))

        assertEquals(1, execution.exitCode, execution.output)
        assertEquals(listOf("1", "Error: sin memoria"), execution.output.lines().dropLast(1))
    }
}
