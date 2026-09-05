package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLRootShellToolTest {
    @Test
    fun `parses a bounded root command request`() {
        val parsed = parseCodexVLRootShellRequest(
            Json.parseToJsonElement(
                """{"command":"id","working_directory":"/data/local/tmp","timeout_seconds":45}"""
            ),
            "/default",
        )

        assertTrue(parsed is CodexVLRootShellParseResult.Success)
        val request = (parsed as CodexVLRootShellParseResult.Success).request
        assertEquals("id", request.command)
        assertEquals("/data/local/tmp", request.workingDirectory)
        assertEquals(45, request.timeoutSeconds)
    }

    @Test
    fun `uses safe defaults`() {
        val parsed = parseCodexVLRootShellRequest(
            Json.parseToJsonElement("""{"command":"getprop ro.build.version.release"}"""),
            "/app/files",
        ) as CodexVLRootShellParseResult.Success

        assertEquals("/app/files", parsed.request.workingDirectory)
        assertEquals(60, parsed.request.timeoutSeconds)
    }

    @Test
    fun `rejects invalid commands working directories and timeouts`() {
        listOf(
            """{"command":""}""",
            """{"command":"id","working_directory":"relative/path"}""",
            """{"command":"id","timeout_seconds":0}""",
            """{"command":"id","timeout_seconds":121}""",
            """{"command":"id","timeout_seconds":"fast"}""",
        ).forEach { input ->
            assertTrue(
                input,
                parseCodexVLRootShellRequest(Json.parseToJsonElement(input), "/default") is
                    CodexVLRootShellParseResult.Failure,
            )
        }
    }
}
