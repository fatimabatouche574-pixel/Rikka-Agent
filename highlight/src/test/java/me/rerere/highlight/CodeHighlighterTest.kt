package me.rerere.highlight

import org.junit.Assert.assertEquals
import org.junit.Test

/** Ensures nested tokens can be flattened without losing source characters. */
class HighlightTokenRenderingTest {
    @Test
    fun flatteningNestedTokensPreservesSourceText() {
        val tokens: List<HighlightToken> = listOf(
            HighlightToken.Plain("before "),
            HighlightToken.Token.Nested(
                content = listOf(
                    HighlightToken.Token.StringContent("value", "variable", 5),
                    HighlightToken.Token.StringContent(" = ", "operator", 3),
                    HighlightToken.Token.StringContent("1", "number", 1),
                ),
                type = "expression",
                length = 9,
            ),
            HighlightToken.Plain(" after"),
        )

        assertEquals(
            "before value = 1 after",
            tokens.joinToString(separator = "") { it.sourceText() },
        )
    }

    private fun HighlightToken.sourceText(): String = when (this) {
        is HighlightToken.Plain -> content
        is HighlightToken.Token.StringContent -> content
        is HighlightToken.Token.StringListContent -> content.joinToString(separator = "")
        is HighlightToken.Token.Nested -> content.joinToString(separator = "") { it.sourceText() }
    }
}
