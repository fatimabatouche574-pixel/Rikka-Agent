package me.rerere.highlight

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** Regression coverage for the public token serializer contract. */
class HighlightTokenSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun preservesStringListTokens() {
        val token = HighlightToken.Token.StringListContent(
            content = listOf("one", "two"),
            type = "string",
            length = 6,
        )

        assertEquals(listOf("one", "two"), token.content)
        assertEquals("string", token.type)
        assertEquals(6, token.length)
    }

    @Test
    fun rejectsTokensWithoutRequiredFields() {
        try {
            json.decodeFromString(
                HighlightTokenSerializer,
                """{"content":"value","type":"string"}""",
            )
            fail("missing length must be rejected")
        } catch (_: IllegalStateException) {
            // Expected: malformed runtime payloads must not be silently accepted.
        }
    }
}
