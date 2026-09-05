package me.rerere.rikkahub.data.ai.tools

import android.test.mock.MockContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkillsToolsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun useSkillReadsMetadataDirectoryWhenDisplayNameDiffers() = runBlocking {
        val filesDir = tempFolder.newFolder("files")
        val skillDir = filesDir.resolve("skills/directory-name").apply { mkdirs() }
        skillDir.resolve("SKILL.md").writeText(
            """
                ---
                name: Display Name
                description: Test skill
                ---
                Skill instructions
            """.trimIndent()
        )
        val context = object : MockContext() {
            override fun getFilesDir(): File = filesDir
        }
        @Suppress("UNCHECKED_CAST")
        val skillManager = SkillManager(context, null as SettingsStore)
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(
                SkillMetadata(
                    name = "Display Name",
                    description = "Test skill",
                    skillDir = skillDir,
                )
            ),
            skillManager = skillManager,
        ).single()

        val result = tool.execute(
            buildJsonObject {
                put("name", "Display Name")
            }
        )

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
    }
}
