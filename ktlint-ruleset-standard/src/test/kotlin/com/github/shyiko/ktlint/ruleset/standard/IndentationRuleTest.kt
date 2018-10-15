package com.github.shyiko.ktlint.ruleset.standard

import com.github.shyiko.ktlint.core.LintError
import com.github.shyiko.ktlint.test.format
import com.github.shyiko.ktlint.test.lint
import jdk.nashorn.internal.ir.annotations.Ignore
import org.assertj.core.api.Assertions.assertThat
import org.testng.annotations.Test

class IndentationRuleTest {

    @Test
    fun testLint() {
        assertThat(IndentationRule().lint(
            """
            /**
             * _
             */
            fun main() {
                val a = 0
                    val b = 0
                if (a == 0) {
                    println(a)
                }
                val b = builder().setX().setY()
                    .build()
               val c = builder("long_string" +
                     "")
            }

            class A {
                var x: String
                    get() = ""
                    set(v: String) { x = v }
            }
            """.trimIndent()
        )).isEqualTo(listOf(
            LintError(6, 1, "indent", "Unexpected indentation (8) (it should be 4)"),
            LintError(12, 1, "indent", "Unexpected indentation (3) (it should be 4)"),
            LintError(13, 1, "indent", "Unexpected indentation (9) (it should be 8)")
        ))
    }

    @Test
    fun testLintCustomIndentSize() {
        assertThat(IndentationRule().lint(
            """
            fun main() {
               val v = ""
                println(v)
            }
            """.trimIndent(),
            mapOf("indent_size" to "3")
        )).isEqualTo(listOf(
            LintError(3, 1, "indent", "Unexpected indentation (4) (it should be 3)")
        ))
    }

    @Test
    fun testLintCustomIndentSizeValid() {
        assertThat(IndentationRule().lint(
            """
            /**
             * _
             */
            fun main() {
              val v = ""
              println(v)
            }

            class A {
              var x: String
                get() = ""
                set(v: String) { x = v }
            }
            """.trimIndent(),
            mapOf("indent_size" to "2")
        )).isEmpty()
    }

    @Test
    fun testLintIndentSizeUnset() {
        assertThat(IndentationRule().lint(
            """
            fun main() {
               val v = ""
                println(v)
            }
            """.trimIndent(),
            mapOf("indent_size" to "unset")
        )).isEmpty()
    }

    @Test
    fun testLintWithContinuationIndentSizeSet() {
        assertThat(IndentationRule().lint(
            """
            fun main() {
                val v = ""
                      .call()
                 call()
            }
            """.trimIndent(),
            mapOf("indent_size" to "4", "continuation_indent_size" to "6")
        )).isEqualTo(listOf(
            LintError(4, 1, "indent", "Unexpected indentation (5) (it should be 4)")
        ))

        assertThat(IndentationRule().lint(
            """
            fun main() {
                val v = ""
                      .call()
                 call()
            }
            """.trimIndent(),
            mapOf("indent_size" to "4", "continuation_indent_size" to "2")
        )).isEqualTo(listOf(
            LintError(3, 1, "indent", "Unexpected indentation (10) (it should be 6)"),
            LintError(4, 1, "indent", "Unexpected indentation (5) (it should be 4)")
        ))

        assertThat(IndentationRule().lint(
            """
            fun main() {
                val v = ""
                    .call()
                     .call()
                      .call()
            }
            """.trimIndent(),
            mapOf("indent_size" to "4", "continuation_indent_size" to "3")
        )).isEqualTo(listOf(
            LintError(3, 1, "indent", "Unexpected indentation (8) (it should be 7)"),
            LintError(4, 1, "indent", "Unexpected indentation (9) (it should be 7)"),
            LintError(5, 1, "indent", "Unexpected indentation (10) (it should be 7)")
        ))
    }

    @Test
    fun testDotChain() {
        assertThat(IndentationRule().lint(
            """
            val a =
                listOf(listOf(1, 2, 3))
                    .map {
                        it
                            .map { it + 1 }
                            .filter { it % 2 == 0 }
                    }
                    .reduce { acc, curr -> acc + curr }
                    .toString()


            val b = 1
            """.trimIndent()
        )).isEmpty()
    }
    // https://kotlinlang.org/docs/reference/coding-conventions.html#method-call-formatting
    @Test
    fun testLintMultilineFunctionCall() {
        assertThat(IndentationRule().lint(
            """
            fun main() {
                fn(a,
                   b,
                   c)
            }
            """.trimIndent()
        )).isEqualTo(listOf(
            LintError(3, 1, "indent", "Unexpected indentation (7) (it should be 8)"),
            LintError(4, 1, "indent", "Unexpected indentation (7) (it should be 8)")
        ))
    }

    @Test
    fun testLintCommentsAreIgnored() {
        assertThat(IndentationRule().lint(
            """
            fun funA(argA: String) =
                // comment
            // comment
                call(argA)
            fun main() {
                addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
             // comment
                    override fun onLayoutChange(
                    )
                })
            }
            """.trimIndent(),
            mapOf("indent_size" to "4")
        )).isEmpty()
    }

    @Test(description = "https://github.com/shyiko/ktlint/issues/180")
    fun testLintWhereClause() {
        assertThat(IndentationRule().lint(
            """
            class BiAdapter<C : RecyclerView.ViewHolder, V1 : C, V2 : C, out A1, out A2>(
                val adapter1: A1,
                val adapter2: A2
            ) : RecyclerView.Adapter<C>()
                where A1 : RecyclerView.Adapter<V1>, A1 : ComposableAdapter.ViewTypeProvider,
                      A2 : RecyclerView.Adapter<V2>, A2 : ComposableAdapter.ViewTypeProvider {
            }
            """.trimIndent()
        )).isEmpty()
    }

    @Test
    fun testTab() {
        val ktScript = "fun main() {\n\t\treturn 0\n\t}"
        assertThat(IndentationRule().format(ktScript)).isEqualTo("fun main() {\n        return 0\n    }")
        assertThat(IndentationRule().lint(ktScript)).isEqualTo(listOf(
            LintError(line = 2, col = 1, ruleId = "indent", detail = "Unexpected indentation (2) (it should be 4)"),
            LintError(line = 2, col = 1, ruleId = "indent", detail = "Unexpected Tab character(s)"),
            LintError(line = 3, col = 1, ruleId = "indent", detail = "Unexpected indentation (1) (it should be 0)"),
            LintError(line = 3, col = 1, ruleId = "indent", detail = "Unexpected Tab character(s)")
        ))
    }

    @Test
    fun testTabEditorConfig() {
        val ktScript = "fun main() {\n\t\treturn 0\n\t}"
        assertThat(IndentationRule().format(ktScript, mapOf("indent_size" to "3"))).isEqualTo("fun main() {\n      return 0\n   }")
        assertThat(IndentationRule().lint(ktScript, mapOf("indent_size" to "3"))).isEqualTo(listOf(
            LintError(line = 2, col = 1, ruleId = "indent", detail = "Unexpected indentation (2) (it should be 3)"),
            LintError(line = 2, col = 1, ruleId = "indent", detail = "Unexpected Tab character(s)"),
            LintError(line = 3, col = 1, ruleId = "indent", detail = "Unexpected indentation (1) (it should be 0)"),
            LintError(line = 3, col = 1, ruleId = "indent", detail = "Unexpected Tab character(s)")
        ))
    }
}
