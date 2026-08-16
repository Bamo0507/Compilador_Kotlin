package org.compiler

import org.compiler.frontend.lexicalAnalyzer.lexer.Lexer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Verifies that parser_test.yal tokenizes Java faithfully: reserved words are
// never IDs, numeric literals carry the right category, and string/char
// literals accept exactly the JLS escape sequences.
class JavaLexerSpecTest {

    private fun readYalex(): String =
        File("src/main/resources/parser_test.yal").readText()

    private fun categoriesOf(source: String): List<String> {
        val result = Lexer.tokenize(readYalex(), source)
        assertTrue(result.errors.isEmpty(), "Unexpected lexer errors: ${result.errors}")
        return result.entries.map { it.token.category }
    }

    @Test
    fun `every Java reserved word lexes as its own token, never as ID`() {
        val reservedWords = listOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null"
        )
        val categories = categoriesOf(reservedWords.joinToString(" "))
        assertTrue(categories.none { it == "ID" }, "Reserved word leaked as ID: $categories")
        assertTrue(categories.all { it.startsWith("KW_") }, "Non-keyword category: $categories")
        assertEquals(reservedWords.size, categories.size)
    }

    @Test
    fun `identifiers that contain reserved words as prefix stay IDs by longest match`() {
        val categories = categoriesOf("classy interface2 iffy doIt")
        assertEquals(listOf("ID", "ID", "ID", "ID"), categories)
    }

    @Test
    fun `dollar sign is valid in identifiers like in the JLS`() {
        val categories = categoriesOf("${'$'}var name${'$'} a${'$'}b")
        assertEquals(listOf("ID", "ID", "ID"), categories)
    }

    @Test
    fun `numeric literals are categorized like Java`() {
        val cases = mapOf(
            "42" to "INT",
            "0xFF" to "INT",
            "0b1010" to "INT",
            "42L" to "LONG",
            "0x1Fl" to "LONG",
            "3.14" to "FLOAT",
            ".5" to "FLOAT",
            "1e10" to "FLOAT",
            "2.5e-3f" to "FLOAT",
            "7f" to "FLOAT",
            "9D" to "FLOAT"
        )
        for ((literal, expected) in cases) {
            val categories = categoriesOf(literal)
            assertEquals(listOf(expected), categories, "Literal $literal")
        }
    }

    @Test
    fun `string literals accept JLS escapes`() {
        val source = "\"hola\" \"tab\\tnewline\\n\" \"quote:\\\" backslash:\\\\\" \"unicode:\\u00e9\""
        val categories = categoriesOf(source)
        assertEquals(listOf("STRING_LIT", "STRING_LIT", "STRING_LIT", "STRING_LIT"), categories)
    }

    @Test
    fun `char literals accept plain chars and escapes`() {
        val source = "'a' '0' '\\n' '\\\\' '\\''"
        val categories = categoriesOf(source)
        assertEquals(List(5) { "CHAR_LIT" }, categories)
    }

    // The .yal declares ERROR_* rules for malformed constructs. They must fire, name the
    // problem, and consume the bad text so the parser never sees stray tokens from it.
    @Test
    fun `malformed constructs report a named lexical error`() {
        val cases = mapOf(
            "\"abc" to "Unclosed string",
            "'a" to "Unclosed char",
            "/* abc" to "Unclosed comment",
            "1_000" to "Malformed number",
            "0x" to "Malformed number",
            "1.2.3" to "Malformed number",
            "1e" to "Malformed number",
            "12abc" to "Malformed number"
        )
        for ((source, expectedPrefix) in cases) {
            val result = Lexer.tokenize(readYalex(), source)
            assertTrue(result.errors.isNotEmpty(), "Expected a lexical error for '$source'")
            assertTrue(
                result.errors.first().message.startsWith(expectedPrefix),
                "For '$source' expected '$expectedPrefix...' but got '${result.errors.first().message}'"
            )
        }
    }

    // The error rules are declared last on purpose: longest match plus declaration order must
    // keep every valid literal winning. This is the false-positive guard for that claim.
    @Test
    fun `error rules never fire on valid input`() {
        val valid = listOf(
            "42", "42L", "0xFF", "0b1010", "3.14", ".5", "1e10", "2.5e-3f", "7f", "9D", "0755",
            "\"abc\"", "\"a\\tb\"", "'a'", "'\\n'", "/* c */", "// c",
            "a / b", "a * b", "a[0].b", "int x = 1;"
        )
        for (source in valid) {
            val result = Lexer.tokenize(readYalex(), source)
            assertTrue(
                result.errors.isEmpty(),
                "'$source' must not produce a lexical error: ${result.errors}"
            )
        }
    }

    @Test
    fun `invalid escape is a lexical error like in javac`() {
        val result = Lexer.tokenize(readYalex(), "\"bad\\xescape\"")
        assertTrue(result.errors.isNotEmpty(), "Expected a lexical error for invalid escape")
    }

    @Test
    fun `operators and separators tokenize including colon question and at`() {
        val categories = categoriesOf("? : @ >>>= >>> && ||")
        assertEquals(
            listOf("QUESTION", "COLON", "AT", "OP_USHR_ASSIGN", "OP_USHR", "OP_AND", "OP_OR"),
            categories
        )
    }

    // A source saved on Windows must lex exactly like one saved on Unix. Before '\r' was
    // added to the alphabet (and to the .yal escape set) every CRLF line produced a
    // spurious lexical error.
    @Test
    fun `CRLF line endings lex identically to LF`() {
        val withLf = "public class A {\n    int x = 1;\n    // nota\n}"
        val withCrlf = withLf.replace("\n", "\r\n")

        val lfResult = Lexer.tokenize(readYalex(), withLf)
        val crlfResult = Lexer.tokenize(readYalex(), withCrlf)

        assertTrue(
            crlfResult.errors.isEmpty(),
            "CRLF must not produce lexical errors: ${crlfResult.errors}"
        )
        assertEquals(
            lfResult.entries.map { it.token.category to it.token.lexeme },
            crlfResult.entries.map { it.token.category to it.token.lexeme }
        )
        // Line numbers must not drift either, or every diagnostic would point one off.
        assertEquals(
            lfResult.entries.map { it.location.line },
            crlfResult.entries.map { it.location.line }
        )
    }

    @Test
    fun `keywords carry no symbol table index`() {
        val result = Lexer.tokenize(readYalex(), "class Foo")
        val keywordEntry = result.entries.first { it.token.category == "KW_CLASS" }
        val idEntry = result.entries.first { it.token.category == "ID" }
        assertEquals(null, keywordEntry.token.symbolIndex)
        assertTrue(idEntry.token.symbolIndex != null)
    }
}
