package me.rerere.rikkahub.data.codexvl

/** Root is a separate, default-off capability; ordinary shell may not smuggle `su`. */
object CodexVLRootGuard {
    private val suToken = Regex("(^|[\\s;&|()])(?:/[^\\s;&|()]*/)?su(?:[\\s;&|()]|$)", RegexOption.IGNORE_CASE)
    private val criticalPaths = Regex(
        "(^|[\\s'\"])/(?:data/(?:data|user/0|adb)|system|boot|vendor|persist)(?:/|[\\s'\"]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val criticalTools = Regex(
        "\\b(?:magisk|kernelsu|apatch|setenforce|reboot)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun ordinaryShellAllowed(command: String): Boolean = !requiresRootApproval(command)

    fun ordinaryShellAllowed(
        command: String?,
        executable: String?,
        arguments: Iterable<String>,
    ): Boolean = listOfNotNull(command, executable).plus(arguments).none(::requiresRootApproval)

    fun requiresRootApproval(command: String): Boolean {
        val dequoted = command.replace(Regex("[\\\\'\"\\u2018\\u2019\\u201c\\u201d]"), "")
        return suToken.containsMatchIn(command) ||
            suToken.containsMatchIn(dequoted) ||
            criticalPaths.containsMatchIn(command) ||
            criticalPaths.containsMatchIn(dequoted) ||
            criticalTools.containsMatchIn(command)
    }
}
