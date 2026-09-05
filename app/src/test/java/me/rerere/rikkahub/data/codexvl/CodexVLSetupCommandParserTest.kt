package me.rerere.rikkahub.data.codexvl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLSetupCommandParserTest {
    @Test
    fun `parses canonical command without executing it`() {
        val parsed = success(
            "curl -s https://vibe.example/setup-codex.sh | bash -s -- " +
                "--url https://api.example/codex --key sk-test --model gpt-5.6-sol"
        )
        assertEquals("https://api.example/codex", parsed.baseUrl)
        assertEquals("sk-test", parsed.apiKey)
        assertEquals("gpt-5.6-sol", parsed.model)
    }

    @Test
    fun `parses quotes spaces reordered equals and base url alias`() {
        val cases = listOf(
            "curl -fsSL 'https://host/setup.sh' | bash -s -- --key 'secret' --model \"model-id\" --base-url \"https://api.host/root/\"",
            "curl https://host/setup.sh | bash -s -- --model=model-id --key=secret --url=https://api.host/root",
            "curl   -s   https://host/setup.sh   |   bash -s --   --url https://api.host/root   --key secret",
        )
        cases.forEach {
            val parsed = success(it)
            assertEquals("https://api.host/root", parsed.baseUrl)
            assertEquals("secret", parsed.apiKey)
        }
    }

    @Test
    fun `parses line continuations and unicode model ids`() {
        val parsed = success(
            "curl -s https://host/setup.sh | bash -s -- \\\n" +
                "  --url https://api.host/codex \\\n" +
                "  --key secret --model 模型-v1"
        )
        assertEquals("模型-v1", parsed.model)
    }

    @Test
    fun `reports unsupported options but never interprets them`() {
        val parsed = success(
            "curl https://host/setup.sh | bash -s -- --unknown ignored --url https://api.host --key secret"
        )
        assertEquals(listOf("--unknown"), parsed.unsupportedOptions)
    }

    @Test
    fun `rejects duplicate and missing option values`() {
        assertFailure(
            "curl https://host/setup.sh | bash -s -- --url https://a.example --url https://b.example --key secret",
            CodexVLSetupCommandParser.FailureReason.DUPLICATE_OPTION,
        )
        assertFailure(
            "curl https://host/setup.sh | bash -s -- --url --key secret",
            CodexVLSetupCommandParser.FailureReason.MISSING_VALUE,
        )
    }

    @Test
    fun `rejects all shell injection syntax`() {
        val injections = listOf(
            "; rm -rf /", "&& whoami", "|| true", "\$(id)", "`id`", "> out", ">> out", "< in",
            "| sh", "| bash", "su -c id", "sh -c id", "rm -rf /",
        )
        injections.forEach { injection ->
            val result = CodexVLSetupCommandParser.parse(
                "curl https://host/setup.sh | bash -s -- --url https://api.host --key secret $injection"
            )
            assertTrue("Accepted injection: $injection", result is CodexVLSetupCommandParser.Result.Failure)
        }
    }

    @Test
    fun `rejects unsafe urls`() {
        listOf(
            "javascript:alert(1)",
            "http://api.example/codex",
            "https://user:pass@api.example/codex",
            "https://api.example/codex#fragment",
        ).forEach { url ->
            assertFailure(
                "curl https://host/setup.sh | bash -s -- --url $url --key secret",
                CodexVLSetupCommandParser.FailureReason.INVALID_URL,
            )
        }
        assertEquals("http://127.0.0.1:9876/v1", success(
            "curl https://host/setup.sh | bash -s -- --url http://127.0.0.1:9876/v1 --key secret"
        ).baseUrl)
    }

    @Test
    fun `rejects overly long input`() {
        val result = CodexVLSetupCommandParser.parse("x".repeat(CodexVLSetupCommandParser.MAX_INPUT_LENGTH + 1))
        assertEquals(
            CodexVLSetupCommandParser.Result.Failure(CodexVLSetupCommandParser.FailureReason.TOO_LONG),
            result,
        )
    }

    private fun success(command: String): CodexVLSetupCommandParser.Parsed {
        val result = CodexVLSetupCommandParser.parse(command)
        assertTrue("Expected success but was $result", result is CodexVLSetupCommandParser.Result.Success)
        return (result as CodexVLSetupCommandParser.Result.Success).value
    }

    private fun assertFailure(command: String, reason: CodexVLSetupCommandParser.FailureReason) {
        assertEquals(CodexVLSetupCommandParser.Result.Failure(reason), CodexVLSetupCommandParser.parse(command))
    }
}
