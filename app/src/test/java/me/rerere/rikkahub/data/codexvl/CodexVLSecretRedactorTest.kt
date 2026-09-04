package me.rerere.rikkahub.data.codexvl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLSecretRedactorTest {
    @Test
    fun `redacts authorization setup options json and known secrets`() {
        val secret = "sk-super-secret-value"
        val input = "Authorization: Bearer $secret --key=$secret {\"api_key\":\"$secret\"} raw=$secret"
        val output = CodexVLSecretRedactor.redact(input, secret)
        assertFalse(output.contains(secret))
        assertTrue(output.contains(CodexVLSecretRedactor.REDACTED))
    }
}
