package me.rerere.rikkahub.data.codexvl

/** Root is a separate, default-off capability; ordinary shell may not smuggle `su`. */
object CodexVLRootGuard {
    private val suToken = Regex("(^|[\\s;&|()])(?:/[^\\s;&|()]*/)?su(?:[\\s;&|()]|$)", RegexOption.IGNORE_CASE)
    private val criticalPaths = Regex("(^|[\\s'\"])/(?:data/data|system|boot|vendor|persist)(?:/|[\\s'\"]|$)")
    private val criticalTools = Regex("\\b(?:magisk|kernelsu|apatch|setenforce|reboot)\\b", RegexOption.IGNORE_CASE)

    fun ordinaryShellAllowed(command: String): Boolean = !requiresRootApproval(command)

    fun requiresRootApproval(command: String): Boolean =
        suToken.containsMatchIn(command) ||
            criticalPaths.containsMatchIn(command) ||
            criticalTools.containsMatchIn(command)
}
