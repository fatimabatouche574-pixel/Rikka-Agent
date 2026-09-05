package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexVLEventMapperTest {
    @Test
    fun `maps public summary and never raw hidden reasoning`() {
        val summary = message("""{"method":"item/reasoning/summaryTextDelta","params":{"delta":"Checking files"}}""")
        assertEquals(CodexVLEventMapper.Event.ReasoningSummary("Checking files"), CodexVLEventMapper.map(summary))

        val hidden = message("""{"method":"item/reasoning/textDelta","params":{"delta":"private chain"}}""")
        assertNull(CodexVLEventMapper.map(hidden))
    }

    @Test
    fun `maps command approval with risk and command`() {
        val event = CodexVLEventMapper.map(message(
            """{"id":42,"method":"item/commandExecution/requestApproval","params":{"command":"git status","cwd":"/repo"}}"""
        ))
        assertTrue(event is CodexVLEventMapper.Event.WaitingForPermission)
        event as CodexVLEventMapper.Event.WaitingForPermission
        assertEquals("42", event.requestId)
        assertEquals(CodexVLEventMapper.Risk.HIGH, event.risk)
        assertEquals("git status", event.summary)
    }

    @Test
    fun `su command is critical`() {
        val event = CodexVLEventMapper.map(message(
            """{"id":"root-1","method":"item/commandExecution/requestApproval","params":{"command":"su -c id"}}"""
        )) as CodexVLEventMapper.Event.WaitingForPermission
        assertEquals(CodexVLEventMapper.Risk.CRITICAL, event.risk)
    }

    private fun message(value: String) = Json.parseToJsonElement(value).jsonObject
}
