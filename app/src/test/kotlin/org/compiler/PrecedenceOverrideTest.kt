package org.compiler

import org.compiler.frontend.syntaxAnalyzer.grammar.GrammarValidator
import org.compiler.frontend.syntaxAnalyzer.grammar.PrecedenceRewriter
import org.compiler.frontend.syntaxAnalyzer.grammar.YalpReader
import org.compiler.frontend.syntaxAnalyzer.grammar.models.Symbol
import org.compiler.frontend.syntaxAnalyzer.grammar.models.ValidationError.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Covers the %prec override: a production takes the precedence level of its label
// instead of the level where its own operator token was declared. Without it, unary
// minus would inherit the additive level and "-2 + 3" would group as -(2 + 3).
class PrecedenceOverrideTest {

    // Three levels: additive (0), multiplicative (1), unary pseudo-token (2).
    private fun grammarText(unaryAlternative: String) = """
        %token ID OP_PLUS OP_MINUS OP_TIMES
        %left OP_PLUS OP_MINUS
        %left OP_TIMES
        %right UMINUS
        %%
        expr : expr OP_PLUS expr
             | expr OP_MINUS expr
             | expr OP_TIMES expr
             | $unaryAlternative
             | ID
             ;
    """.trimIndent()

    private fun cascadeOf(unaryAlternative: String): List<String> =
        PrecedenceRewriter.rewrite(YalpReader.parse(grammarText(unaryAlternative)))
            .productions
            .map { production ->
                "${production.head.name} -> ${production.body.joinToString(" ") { it.name }}"
            }

    @Test
    fun `YalpReader strips the directive from the body and records the label`() {
        val grammar = YalpReader.parse(grammarText("OP_MINUS expr %prec UMINUS"))
        val unary = grammar.productions.single { production ->
            production.body.size == 2 && production.body[0].name == "OP_MINUS"
        }

        // The body is exactly OP_MINUS expr -- "%prec" and "UMINUS" are not symbols.
        assertEquals(listOf("OP_MINUS", "expr"), unary.body.map { it.name })
        assertEquals(Symbol.Terminal("UMINUS"), unary.precedenceLabel)
    }

    @Test
    fun `productions without the directive keep a null label`() {
        val grammar = YalpReader.parse(grammarText("OP_MINUS expr"))
        val unary = grammar.productions.single { production ->
            production.body.size == 2 && production.body[0].name == "OP_MINUS"
        }
        assertNull(unary.precedenceLabel)
    }

    @Test
    fun `with a prec override the unary production lands on the label's level`() {
        val cascade = cascadeOf("OP_MINUS expr %prec UMINUS")

        // UMINUS is the third declaration, so its level is expr_lvl2 (tightest binding).
        assertTrue(
            "expr_lvl2 -> OP_MINUS expr_lvl2" in cascade,
            "Unary minus must sit at the UMINUS level. Cascade: $cascade"
        )
        assertTrue(
            "expr_lvl0 -> OP_MINUS expr_lvl0" !in cascade,
            "Unary minus must NOT sit at the additive level. Cascade: $cascade"
        )
        // Binary subtraction must stay at the additive level.
        assertTrue(
            "expr_lvl0 -> expr_lvl0 OP_MINUS expr_lvl1" in cascade,
            "Binary minus must stay additive. Cascade: $cascade"
        )
    }

    @Test
    fun `without a prec override the unary production inherits the operator's own level`() {
        val cascade = cascadeOf("OP_MINUS expr")

        // This is the old (wrong for Java) placement, kept as a regression guard so the
        // override is provably what moves the production.
        assertTrue(
            "expr_lvl0 -> OP_MINUS expr_lvl0" in cascade,
            "Without %prec the unary must stay at the operator's level. Cascade: $cascade"
        )
        assertTrue("expr_lvl2 -> OP_MINUS expr_lvl2" !in cascade)
    }

    // Third production shape the rewriter understands: the left operand chains through the
    // cascade but the right one is a fixed symbol. This is what makes `expr instanceof type`
    // sit on the relational level instead of being dumped into the atom level.
    @Test
    fun `an operator with a fixed right operand lands on its own level`() {
        val content = """
            %token ID OP_PLUS OP_IS TYPE_NAME
            %left OP_PLUS
            %left OP_IS
            %%
            expr : expr OP_PLUS expr
                 | expr OP_IS type_name
                 | ID
                 ;
            type_name : TYPE_NAME
                      ;
        """.trimIndent()

        val cascade = PrecedenceRewriter.rewrite(YalpReader.parse(content))
            .productions
            .map { production ->
                "${production.head.name} -> ${production.body.joinToString(" ") { it.name }}"
            }

        // OP_IS is the second declaration, so level 1; left-recursive on the chaining side,
        // with the fixed right operand copied through untouched.
        assertTrue(
            "expr_lvl1 -> expr_lvl1 OP_IS type_name" in cascade,
            "Fixed-right operator must sit on its declared level. Cascade: $cascade"
        )
        // It must NOT have been treated as an atom (that would lose the precedence entirely).
        assertTrue(
            cascade.none { it == "expr_atom -> expr OP_IS type_name" },
            "Fixed-right operator must not fall through to the atom level. Cascade: $cascade"
        )
        assertTrue("expr_lvl0 -> expr_lvl0 OP_PLUS expr_lvl1" in cascade)
    }

    // Fourth shape: two separators around a middle operand. Note CLOSER is deliberately NOT
    // given a precedence level -- only the opening separator needs one, which is what keeps
    // COLON out of the precedence table in the real grammar (it is also used by case labels,
    // assert messages, labels and for-each).
    @Test
    fun `a ternary operator nests to the right and takes the loosest middle`() {
        val content = """
            %token ID OP_ASSIGN OPENER CLOSER OP_OR
            %right OP_ASSIGN
            %right OPENER
            %left OP_OR
            %%
            expr : expr OP_ASSIGN expr
                 | expr OPENER expr CLOSER expr
                 | expr OP_OR expr
                 | ID
                 ;
        """.trimIndent()

        val cascade = PrecedenceRewriter.rewrite(YalpReader.parse(content))
            .productions
            .map { production ->
                "${production.head.name} -> ${production.body.joinToString(" ") { it.name }}"
            }

        // Levels: 0 = OP_ASSIGN, 1 = OPENER, 2 = OP_OR. The condition slot takes the tighter
        // level (2), the middle the loosest (0), and the last slot recurses on the same level
        // (1) so that "a ? b : c ? d : e" groups as "a ? b : (c ? d : e)".
        assertTrue(
            "expr_lvl1 -> expr_lvl2 OPENER expr_lvl0 CLOSER expr_lvl1" in cascade,
            "Ternary must be right-recursive with the loosest middle. Cascade: $cascade"
        )
        assertTrue(
            cascade.none { it.startsWith("expr_atom -> expr OPENER") },
            "Ternary must not fall through to the atom level. Cascade: $cascade"
        )
    }

    @Test
    fun `a pseudo-token used as a label is accepted by the validator`() {
        val grammar = YalpReader.parse(grammarText("OP_MINUS expr %prec UMINUS"))
        val errors = GrammarValidator.validate(
            grammar,
            setOf("ID", "OP_PLUS", "OP_MINUS", "OP_TIMES")
        )
        val blocking = errors.filter { it.severity == Severity.ERROR }
        assertTrue(blocking.isEmpty(), "UMINUS is a legitimate pseudo-token but got: $blocking")
    }

    @Test
    fun `a precedence operator that is neither token nor label is an error`() {
        val content = """
            %token ID OP_PLUS
            %left OP_PLUS
            %right TYPO_TOKEN
            %%
            expr : expr OP_PLUS expr
                 | ID
                 ;
        """.trimIndent()
        val errors = GrammarValidator.validate(YalpReader.parse(content), setOf("ID", "OP_PLUS"))
        assertTrue(errors.any { it.severity == Severity.ERROR && "TYPO_TOKEN" in it.message })
    }

    @Test
    fun `a prec label with no declared level is an error`() {
        val content = """
            %token ID OP_MINUS
            %left OP_MINUS
            %%
            expr : expr OP_MINUS expr
                 | OP_MINUS expr %prec NOT_DECLARED
                 | ID
                 ;
        """.trimIndent()
        val errors = GrammarValidator.validate(YalpReader.parse(content), setOf("ID", "OP_MINUS"))
        assertTrue(
            errors.any { it.severity == Severity.ERROR && "NOT_DECLARED" in it.message },
            "A typo in a %prec label must be reported, not silently ignored: $errors"
        )
    }
}
