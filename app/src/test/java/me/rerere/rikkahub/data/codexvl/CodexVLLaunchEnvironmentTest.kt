package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CodexVLLaunchEnvironmentTest {
    @Test fun bundledLibrariesAreSearchedBeforeInheritedPaths() {
        val env = CodexVLLaunchEnvironment.values("/private/files", "/private/codex", "/private/cache", "/apk/lib", "/system/lib64")
        assertEquals("/apk/lib:/system/lib64", env["LD_LIBRARY_PATH"])
        assertEquals("/private/files", env["HOME"])
        assertEquals("/private/codex", env["CODEX_HOME"])
        assertEquals("/private/cache", env["TMPDIR"])
        assertFalse(env.containsKey(CodexVLProviderConfig.API_KEY_ENV))
    }
    @Test fun absentInheritedLibraryPathDoesNotAddCurrentDirectory() {
        for (value in listOf(null, "", "  ")) {
            assertEquals("/apk/lib", CodexVLLaunchEnvironment.values("/home", "/codex", "/tmp", "/apk/lib", value)["LD_LIBRARY_PATH"])
        }
    }
    @Test fun providerSecretsNeverReachDiagnostic() {
        val error = Json.parseToJsonElement("""{"code":-32602,"message":"invalid params Authorization Bearer secret-fixture"}""")
        val failure = CodexVLRuntimeFailure("THREAD_START", CodexVLRuntimeFailure.classify(error))
        assertEquals("Codex-VL [THREAD_START:INVALID_PARAMS]", failure.message)
        assertFalse(failure.message!!.contains("secret-fixture"))
    }
}
