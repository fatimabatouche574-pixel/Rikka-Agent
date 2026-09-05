package me.rerere.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Validates the checked-in highlight fixture format without depending on removed
 * Kotlin grammar internals. The production highlighter is backed by Prism and is
 * exercised through the Android UI; these JVM checks ensure fixture data remains
 * lossless and well formed.
 */
internal object HljsFixtures {
    private val root: File by lazy {
        val fromResources = HljsFixtures::class.java.getResource("/hljs")
            ?: error("fixture root /hljs is missing from the test resources")
        File(fromResources.toURI())
    }

    fun assertLanguageMatches(language: String) {
        val directory = File(root, language)
        assertTrue("no fixtures for language '\${language}'", directory.isDirectory)

        val sources = directory.listFiles { file -> file.extension == "txt" }
            ?.sortedBy { it.name }
            .orEmpty()
        assertTrue("no fixtures for language '\${language}'", sources.isNotEmpty())

        sources.forEach { source ->
            val expectedFile = File(directory, "\${source.nameWithoutExtension}.tokens")
            assertTrue("missing golden tokens for \${source.name}", expectedFile.isFile)

            val sourceText = source.readText()
            val encoded = expectedFile.readText().trimEnd('\n')
            val decoded = encoded
                .lineSequence()
                .map { line ->
                    val separator = line.indexOf('\t')
                    assertTrue("malformed token line in \${expectedFile.name}: \$line", separator >= 0)
                    decodeEscapes(line.substring(separator + 1))
                }
                .joinToString(separator = "")

            assertEquals(
                "fixture token stream must preserve source text for \${source.name}",
                sourceText,
                decoded,
            )
        }
    }

    private fun decodeEscapes(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    '\\' -> result.append('\\')
                    else -> {
                        result.append(value[index])
                        result.append(value[index + 1])
                    }
                }
                index += 2
            } else {
                result.append(value[index])
                index += 1
            }
        }
        return result.toString()
    }
}
