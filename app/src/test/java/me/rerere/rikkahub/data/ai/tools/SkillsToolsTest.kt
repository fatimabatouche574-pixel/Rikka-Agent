package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkillsToolsTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun useSkillReadsBodyFromSkillBackend() = runBlocking {
        val skillDir = tempFolder.newFolder("directory-name")
        val backend = object : SkillToolBackend {
            override fun getContent(skillName: String) = null

            override fun readSkillBody(skillName: String): String? =
                if (skillName == "Display Name") "Skill instructions" else null

            override fun readSkillFileCached(skillName: String, relativePath: String) = null

            override fun getSkillDir(skillName: String) = skillDir

            override fun resolveSkillFile(skillName: String, relativePath: String) = null
        }
        val tool = createSkillTools(
            enabledSkills = setOf("Display Name"),
            allSkills = listOf(
                SkillMetadata(
                    name = "Display Name",
                    description = "Test skill",
                    skillDir = skillDir,
                )
            ),
            backend = backend,
        ).single { it.name == "use_skill" }

        val result = tool.execute(
            buildJsonObject {
                put("name", "Display Name")
            }
        )

        assertEquals("Skill instructions", (result.single() as UIMessagePart.Text).text)
    }
}
