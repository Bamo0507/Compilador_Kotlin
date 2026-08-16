package org.compiler

import org.compiler.frontend.syntaxAnalyzer.lalr1.models.LALR1Table
import org.compiler.frontend.syntaxAnalyzer.runtime.Pipeline
import org.compiler.frontend.syntaxAnalyzer.runtime.models.Action
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseResult
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParseTree
import org.compiler.frontend.syntaxAnalyzer.runtime.models.ParserMethod
import org.compiler.frontend.syntaxAnalyzer.slr1.models.ConflictType
import org.compiler.frontend.syntaxAnalyzer.slr1.models.SLR1Table
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// Verifies that parser.yalp accepts the Java constructs it claims to cover,
// exercising the full pipeline with the real .yal/.yalp resources.
class JavaGrammarSpecTest {

    private fun readYalex(): String = File("src/main/resources/parser_test.yal").readText()
    private fun readYalp(): String = File("src/main/resources/parser.yalp").readText()

    // Mirrors AppState.DEFAULT_INPUT: package/imports, implements, multi-declarators,
    // hex literal, constructor with throws, char/string literals with escapes,
    // do-while, switch, try/catch/finally.
    private val showcaseProgram = """
        package com.example.app;

        import java.util.List;
        import java.io.*;

        public class Counter implements Runnable {
            private int count, step = 1;
            private int offset = -1;
            private static final int LIMIT = 0xFF;

            public Counter(int start) throws Exception {
                count = start;
            }

            public void run() {
                char sep = ':';
                String label = "count:\t";
                do {
                    count = count + step;
                } while (count < LIMIT);

                switch (count % 3) {
                    case 0:
                        label = "multiple\n";
                        break;
                    default:
                        break;
                }

                try {
                    process(label, sep);
                } catch (Exception e) {
                    count = 0;
                } finally {
                    count = count + 1;
                }
            }

            public int process(String label, char sep) {
                int total = -offset;
                for (int i = 0; i < count; i++) {
                    total += i * 2 - offset;
                }
                if (total > 100) {
                    return total;
                } else {
                    return 0;
                }
            }

            public static void main(String[] args) {
                int[] seeds = new int[3];
                for (int seed : seeds) {
                    seed++;
                }
            }
        }
    """.trimIndent()

    @Test
    fun `showcase program is Accepted with SLR1 and lexes cleanly`() {
        val result = Pipeline.runFull(readYalex(), readYalp(), showcaseProgram, ParserMethod.SLR1)
        assertTrue(result.lexerResult.errors.isEmpty(), "Lexer errors: ${result.lexerResult.errors}")
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "SLR1 rejected: ${(result.parseResult as? ParseResult.Rejected)?.errors}"
        )
        assertNoUnexpectedConflicts(result.slr1Table)
    }

    @Test
    fun `showcase program is Accepted with LALR1`() {
        val result = Pipeline.runFull(readYalex(), readYalp(), showcaseProgram, ParserMethod.LALR1)
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "LALR1 rejected: ${(result.parseResult as? ParseResult.Rejected)?.errors}"
        )
        assertNoUnexpectedConflicts(result.lalr1Table)
    }

    @Test
    fun `assert and synchronized statements parse`() {
        val program = """
            public class A {
                public void f(int n) {
                    assert n > 0;
                    assert n > 0 : n;
                    synchronized (this) {
                        n = n + 1;
                    }
                }
            }
        """.trimIndent()
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "Rejected: ${(result.parseResult as? ParseResult.Rejected)?.errors}"
        )
    }

    private fun assertAccepted(program: String) {
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        assertTrue(result.lexerResult.errors.isEmpty(), "Lexer errors: ${result.lexerResult.errors}")
        assertTrue(
            result.parseResult is ParseResult.Accepted,
            "Rejected: ${(result.parseResult as? ParseResult.Rejected)?.errors}"
        )
        assertNoUnexpectedConflicts(result.slr1Table)
    }

    // The grammar has exactly two deliberate shift/reduce conflicts, both
    // resolved by shift, both matching Java semantics (Dragon Book §4.8.2):
    //
    //   1. KW_ELSE  -- dangling-else: the else binds to the nearest if.
    //   2. LBRACKET -- greedy array creation: new int[2][3] is a 2D creation,
    //      not (new int[2])[3]. Only accepted when the losing reduction is
    //      precisely the 1D `primary : KW_NEW simple_type [ expr ]`.
    //
    // Anything else is a regression. SLR1Conflict and LALR1Conflict are distinct
    // types with the same shape, so both call sites project onto a triple.
    private fun assertExpectedConflictsOnly(
        conflicts: List<Triple<String, ConflictType, List<Action>>>
    ) {
        val unexpected = conflicts.filterNot { (terminalName, type, actions) ->
            val resolvedByShift = type == ConflictType.SHIFT_REDUCE &&
                actions.any { action -> action is Action.Shift }
            val reducedProductions = actions.filterIsInstance<Action.Reduce>().map { it.production }
            when {
                !resolvedByShift -> false
                terminalName == "KW_ELSE" -> true
                terminalName == "LBRACKET" -> reducedProductions.all { production ->
                    production.head.name == "primary" &&
                        production.body.firstOrNull()?.name == "KW_NEW"
                }
                else -> false
            }
        }
        assertTrue(unexpected.isEmpty(), "Unexpected conflicts: $unexpected")
    }

    private fun assertNoUnexpectedConflicts(table: SLR1Table) =
        assertExpectedConflictsOnly(table.conflicts.map { Triple(it.terminal.name, it.type, it.actions) })

    private fun assertNoUnexpectedConflicts(table: LALR1Table) =
        assertExpectedConflictsOnly(table.conflicts.map { Triple(it.terminal.name, it.type, it.actions) })

    @Test
    fun `canonical main signature with class array parameter parses`() {
        assertAccepted("""
            public class Main {
                private String[] names;
                private int[] counts = new int[10];

                public static void main(String[] args) {
                    int[] local = new int[3];
                    local[0] = 1;
                }

                public String[] getNames() {
                    return names;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `classic for with postfix increment and for-each parse`() {
        assertAccepted("""
            public class Loops {
                public int sum(int n) {
                    int total = 0;
                    for (int i = 0; i < n; i++) {
                        total += i;
                    }
                    int[] values = new int[5];
                    for (int v : values) {
                        total += v;
                    }
                    n++;
                    n--;
                    return total;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `interface with constants default and abstract methods parses`() {
        assertAccepted("""
            public interface Shape extends Comparable {
                int SIDES = 4;

                double area();
                void scale(double factor);

                default String describe() {
                    return "shape";
                }
            }
        """.trimIndent())
    }

    @Test
    fun `enum declarations parse with and without members`() {
        assertAccepted("""
            public enum Direction { NORTH, SOUTH, EAST, WEST }

            enum Status implements Marker {
                OPEN, CLOSED;

                private int code;

                public int getCode() {
                    return code;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `nested types and initializer blocks parse`() {
        assertAccepted("""
            public class Outer {
                private static int counter;

                static {
                    counter = 0;
                }

                {
                    counter++;
                }

                private class Inner {
                    int x;
                }

                interface Callback {
                    void run();
                }
            }
        """.trimIndent())
    }

    @Test
    fun `constructor chaining and super member access parse`() {
        assertAccepted("""
            public class Derived extends Base {
                public Derived() {
                    this(0);
                }

                public Derived(int x) {
                    super(x);
                    super.init();
                    this.value = super.value + 1;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `marker annotations parse as modifiers`() {
        assertAccepted("""
            @Deprecated
            public class Old {
                @Override
                public String describe() {
                    return "old";
                }

                @Deprecated private int legacy;
            }
        """.trimIndent())
    }

    @Test
    fun `labeled statements and labeled break continue parse`() {
        assertAccepted("""
            public class Labels {
                public void scan(int n) {
                    outer: while (n > 0) {
                        while (true) {
                            if (n == 3) {
                                break outer;
                            }
                            if (n == 5) {
                                continue outer;
                            }
                            n--;
                        }
                    }
                }
            }
        """.trimIndent())
    }

    @Test
    fun `braceless bodies parse and else binds to the nearest if`() {
        assertAccepted("""
            public class T {
                public void m(int x) {
                    if (x > 0) return;
                    while (x > 0) x = x / 2;
                    for (int i = 0; i < 3; i++) m(i);
                    if (x > 0) if (x > 1) m(1); else m(2);
                    do m(x); while (false);
                }
            }
        """.trimIndent())
    }

    @Test
    fun `declaration is rejected as a braceless body like in javac`() {
        // JLS 14.2: the body of an if without braces is a Statement, not a
        // BlockStatement, so a local declaration there is illegal.
        val result = Pipeline.runFull(
            readYalex(),
            readYalp(),
            "public class T { public void m() { if (true) int y = 1; } }",
            ParserMethod.SLR1
        )
        val errors = when (val parse = result.parseResult) {
            is ParseResult.Accepted -> parse.errors
            is ParseResult.Rejected -> parse.errors
        }
        assertTrue(errors.isNotEmpty(), "Declaration as braceless body must be a syntax error")
    }

    @Test
    fun `empty statement and final parameters parse`() {
        assertAccepted("""
            public class T {
                public void m(final int x, final String s) {
                    ;
                    for (;;) break;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `two-dimensional arrays parse in declarations and creation`() {
        assertAccepted("""
            public class T {
                private int[][] grid;
                private String[][] names;

                public int[][] build(int[][] source) {
                    int[][] copy = new int[2][3];
                    copy[0][1] = source[1][0];
                    return copy;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `for with multiple updates parses`() {
        assertAccepted("""
            public class T {
                public void m() {
                    for (int i = 0, j = 10; i < j; i++, j--) {
                        m();
                    }
                }
            }
        """.trimIndent())
    }

    @Test
    fun `multi-catch parses`() {
        assertAccepted("""
            public class T {
                public void m() {
                    try {
                        m();
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        m();
                    } catch (final Exception e) {
                        m();
                    }
                }
            }
        """.trimIndent())
    }

    @Test
    fun `annotations with arguments parse`() {
        assertAccepted("""
            @SuppressWarnings("all")
            public class T {
                @SuppressWarnings("unchecked")
                private int x;

                @Override
                public String toString() {
                    return "t";
                }
            }
        """.trimIndent())
    }

    @Test
    fun `enum constants with arguments parse`() {
        assertAccepted("""
            public enum Color {
                RED("r", 1), BLUE("b", 2);

                private String code;

                public String getCode() {
                    return code;
                }
            }
        """.trimIndent())
    }

    @Test
    fun `unary minus and plus parse in every position`() {
        assertAccepted("""
            public class T {
                private int offset = -1;
                private double scale = -2.5;

                public int m(int a, int b) {
                    int x = -1;
                    int y = +1;
                    int z = -a;
                    int w = a - -b;
                    int v = -(a + b);
                    int u = a * -b;
                    call(-1, -a);
                    if (-a > 0) return -a;
                    return -1;
                }

                public void call(int p, int q) { }
            }
        """.trimIndent())
    }

    // Renders the tree with parentheses around each operator application, so the
    // grouping -- and therefore the precedence -- is directly assertable.
    private fun grouping(tree: ParseTree): String = when (tree) {
        is ParseTree.LeafNode -> tree.entry.token.lexeme
        is ParseTree.InternalNode -> {
            val parts = tree.children.map { grouping(it) }.filter { it.isNotBlank() }
            when {
                parts.isEmpty() -> ""
                parts.size == 1 -> parts.first()
                else -> "(" + parts.joinToString(" ") + ")"
            }
        }
        ParseTree.EpsilonNode -> ""
    }

    private fun groupingOfInitializer(expression: String): String {
        val program = "public class T { public void m(int a, int b) { int r = $expression; } }"
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        val parse = result.parseResult
        assertTrue(parse is ParseResult.Accepted, "Rejected '$expression'")
        return grouping(parse.parseTree)
    }

    @Test
    fun `unary minus binds tighter than binary operators`() {
        // The whole point of %prec UMINUS: without it these would group as -(2 + 3)
        // and -(a * b), because OP_MINUS is declared at the additive level.
        assertTrue("((- 2) + 3)" in groupingOfInitializer("-2 + 3"))
        assertTrue("((- a) * b)" in groupingOfInitializer("-a * b"))
        assertTrue("(a * (- b))" in groupingOfInitializer("a * -b"))
    }

    @Test
    fun `binary minus keeps additive precedence and left associativity`() {
        assertTrue("(a - 1)" in groupingOfInitializer("a - 1"))
        assertTrue("(a - (- 1))" in groupingOfInitializer("a - -1"))
        assertTrue("((1 - 2) - 3)" in groupingOfInitializer("1 - 2 - 3"))
    }

    @Test
    fun `postfix increment and decrement work inside expressions`() {
        assertAccepted("""
            public class T {
                private int i, j, count;
                private int[] arr;

                public void m() {
                    i++;
                    i--;
                    this.count++;
                    arr[0]++;
                    int x = i++;
                    int y = i++ + 1;
                    int z = ++i;
                    int u = i++ + j--;
                    int v = i++ * 2;
                    f(i++);
                    while (i++ < 3) { }
                    for (int k = 0, l = 3; k < l; k++, l--) { }
                }

                public void f(int p) { }
            }
        """.trimIndent())
    }

    @Test
    fun `postfix binds tighter than the arithmetic and unary operators`() {
        // Postfix ++ sits on the `postfix` non-terminal, the same tier as . and [],
        // so it groups before anything in the precedence cascade can take the operand.
        assertTrue("((a ++) + 1)" in groupingOfInitializer("a++ + 1"))
        assertTrue("((a ++) * 2)" in groupingOfInitializer("a++ * 2"))
        assertTrue("(- (a ++))" in groupingOfInitializer("-a++"))
    }

    @Test
    fun `postfix applies to member access and indexing results`() {
        assertTrue("((a . b) ++)" in groupingOfInitializer("a.b++"))
        assertTrue("((c [ 0 ]) ++)" in groupingOfInitializerWithArray("c[0]++"))
    }

    private fun groupingOfInitializerWithArray(expression: String): String {
        val program = "public class T { private int[] c; public void m(int a, int b) " +
            "{ int r = $expression; } }"
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        val parse = result.parseResult
        assertTrue(parse is ParseResult.Accepted, "Rejected '$expression'")
        return grouping(parse.parseTree)
    }

    @Test
    fun `instanceof parses with class and array types`() {
        assertAccepted("""
            public class T {
                private Object o;
                private int x;

                public void m() {
                    boolean a = o instanceof String;
                    boolean b = o instanceof T;
                    boolean c = o instanceof int[];
                    boolean d = o instanceof String[];
                    boolean e = !(o instanceof String);
                    if (o instanceof String) { }
                    while (o instanceof T) { x++; }
                }
            }
        """.trimIndent())
    }

    @Test
    fun `instanceof sits on the relational precedence level`() {
        // Tighter than && and than assignment, exactly like < and >.
        assertTrue(
            "((o instanceof String) && (a > 0))" in groupingOfInstanceof("o instanceof String && a > 0")
        )
        assertTrue(
            "((a > 0) && (o instanceof String))" in groupingOfInstanceof("a > 0 && o instanceof String")
        )
        assertTrue(
            "(b = (o instanceof String))" in groupingOfInstanceof("b = o instanceof String")
        )
    }

    private fun groupingOfInstanceof(expression: String): String {
        val program = "public class T { private Object o; private boolean b; " +
            "public void m(int a) { boolean r = $expression; } }"
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        val parse = result.parseResult
        assertTrue(parse is ParseResult.Accepted, "Rejected '$expression'")
        return grouping(parse.parseTree)
    }

    @Test
    fun `ternary conditional parses in every position`() {
        assertAccepted("""
            public class T {
                private int a, b, c, x;
                private int field = 1 > 0 ? 1 : 2;

                public int m() {
                    int r = a > 0 ? 1 : 2;
                    int nested = a > 0 ? b : c > 0 ? b : c;
                    int withUnary = a > 0 ? a : -a;
                    x = a > 0 ? 1 : 2;
                    f(a > 0 ? 1 : 2);
                    if ((a > 0 ? 1 : 2) > 1) { }
                    return a > 0 ? a : 0;
                }

                public void f(int p) { }
            }
        """.trimIndent())
    }

    @Test
    fun `ternary is right associative and sits between assignment and the logical operators`() {
        // Right associative: the tail nests, so this is a ? b : (c ? d : e).
        assertTrue("(a ? b : (c ? d : e))" in groupingOfTernary("a ? b : c ? d : e"))
        // The condition is a tighter level, so the comparison groups first.
        assertTrue("((a > 0) ? 1 : 2)" in groupingOfTernary("a > 0 ? 1 : 2"))
        // Assignment is looser than the ternary, so it wraps it.
        assertTrue("(x = ((a > 0) ? 1 : 2))" in groupingOfTernary("x = a > 0 ? 1 : 2"))
        // Arithmetic in the tail binds tighter than the ternary itself.
        assertTrue("((a > 0) ? 1 : (2 + 3))" in groupingOfTernary("a > 0 ? 1 : 2 + 3"))
    }

    @Test
    fun `the ternary colon does not clash with case labels asserts labels or for-each`() {
        // COLON is shared by four other constructs; a ternary inside a case body is the
        // tightest of those interactions.
        assertAccepted("""
            public class T {
                private int a, b;
                private int[] arr;

                public void m() {
                    switch (a) {
                        case 1:
                            b = a > 0 ? 1 : 2;
                            break;
                        default:
                            break;
                    }
                    assert a > 0;
                    assert a > 0 : b;
                    lbl: while (true) { break lbl; }
                    for (int v : arr) { }
                }
            }
        """.trimIndent())
    }

    private fun groupingOfTernary(expression: String): String {
        val program = "public class T { private int a, b, c, d, e, x; " +
            "public void m() { int r = $expression; } }"
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        val parse = result.parseResult
        assertTrue(parse is ParseResult.Accepted, "Rejected '$expression'")
        return grouping(parse.parseTree)
    }

    @Test
    fun `unsupported Java construct produces a located syntax error not a crash`() {
        // A lambda is documented as unsupported: it must be flagged with position info
        // rather than crash or be silently accepted.
        val program = """
            public class A {
                public void f() {
                    Runnable r = () -> { g(); };
                }
            }
        """.trimIndent()
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        // Panic-mode recovery may repair the parse (Accepted with errors) or give up
        // (Rejected); either way the construct must be flagged with a location.
        val errors = when (val parse = result.parseResult) {
            is ParseResult.Accepted -> parse.errors
            is ParseResult.Rejected -> parse.errors
        }
        assertTrue(errors.isNotEmpty(), "A lambda must produce a syntax error")
        assertTrue(errors.first().location != null, "Error must carry a location")
    }

    @Test
    fun `lexical and syntax errors are reported together`() {
        // '#' is not a Java token (lexical error); the missing semicolon after
        // 'int x = 1' is a syntax error. Both must surface in one run.
        val program = """
            public class A {
                public void f() {
                    int x = 1 #
                }
            }
        """.trimIndent()
        val result = Pipeline.runFull(readYalex(), readYalp(), program, ParserMethod.SLR1)
        assertTrue(result.lexerResult.errors.isNotEmpty(), "Expected a lexical error for '#'")
        assertTrue(result.parseResult is ParseResult.Rejected)
    }
}
