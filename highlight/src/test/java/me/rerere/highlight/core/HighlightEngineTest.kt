package me.rerere.highlight

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the token model used by the current Prism/QuickJS-backed highlighter.
 *
 * The production highlighter requires an Android Context, so JVM tests keep the
 * serialization and token-shape contract deterministic and host-side.
 */
class HighlightTokenTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun plainTokensPreserveTheirSourceText() {
        val token = HighlightToken.Plain("let value = 1")
        assertEquals("let value = 1", token.content)
    }

    @Test
    fun decodesAStyledStringToken() {
        val token = json.decodeFromString(
            HighlightTokenSerializer,
            """{"content":"let","type":"keyword","length":3}""",
        )

        assertEquals(
            HighlightToken.Token.StringContent(
                content = "let",
                type = "keyword",
                length = 3,
            ),
            token,
        )
    }

    @Test
    fun decodesNestedTokenContent() {
        val token = json.decodeFromString(
            HighlightTokenSerializer,
            """{"content":[{"content":"let","type":"keyword","length":3}," value"],"type":"code","length":8}""",
        )

        assertEquals(
            HighlightToken.Token.Nested(
                content = listOf(
                    HighlightToken.Token.StringContent(
                        content = "let",
                        type = "keyword",
                        length = 3,
                    ),
                    HighlightToken.Token.StringContent(
                        content = " value",
                        type = "code",
                        length = 8,
                    ),
                ),
                type = "code",
                length = 8,
            ),
            token,
        )
    }
}
