// Ensambla y corre un .s en el contenedor ARM, con scripts/arm.sh. Lo usan las pruebas
// que ejecutan ARM de verdad; sin Docker o sin la imagen, esas pruebas se saltan.
package org.compiler

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ArmContainer {

    // Lo que imprimio el programa y su codigo de salida.
    data class Execution(val output: String, val exitCode: Int)

    val isAvailable: Boolean by lazy {
        try {
            val process = ProcessBuilder("docker", "image", "inspect", "compiscript-arm")
                .redirectErrorStream(true)
                .start()
            process.inputStream.readAllBytes()
            process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (exception: IOException) {
            false
        }
    }

    // El archivo va dentro del proyecto, en build/arm: Colima solo comparte carpetas
    // dentro de $HOME, y build no se versiona. Cada corrida usa un nombre nuevo, porque la
    // carpeta compartida puede entregarle al contenedor una copia vieja de un archivo que
    // se acaba de reescribir.
    fun run(name: String, assembly: String): Execution {
        val folder = File("build/arm").apply { mkdirs() }
        val source = File(folder, "${name}_${System.nanoTime()}.s").apply { writeText(assembly) }
        try {
            val process = ProcessBuilder(File("../scripts/arm.sh").path, source.path)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(120, TimeUnit.SECONDS)
            return Execution(output, process.exitValue())
        } finally {
            source.delete()
        }
    }
}
