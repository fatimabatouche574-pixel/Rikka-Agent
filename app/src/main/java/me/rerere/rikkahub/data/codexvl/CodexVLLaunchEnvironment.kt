package me.rerere.rikkahub.data.codexvl

/** Explicit child environment: Android apps do not inherit a usable shell HOME or library path. */
internal object CodexVLLaunchEnvironment {
    fun values(home: String, codexHome: String, temp: String, nativeDir: String,
               inheritedLibraryPath: String?): Map<String, String> = mapOf(
        "HOME" to home,
        "CODEX_HOME" to codexHome,
        "TMPDIR" to temp,
        "LD_LIBRARY_PATH" to listOfNotNull(nativeDir, inheritedLibraryPath?.takeIf { it.isNotBlank() })
            .joinToString(":"),
    )
}
