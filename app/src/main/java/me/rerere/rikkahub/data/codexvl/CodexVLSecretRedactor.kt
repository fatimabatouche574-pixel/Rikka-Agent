package me.rerere.rikkahub.data.codexvl

object CodexVLSecretRedactor {
    private val bearer = Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+")
    private val optionKey = Regex("(?i)(--key(?:=|\\s+))(?:(?:'[^']*')|(?:\"[^\"]*\")|[^\\s]+)")
    private val jsonKey = Regex("(?i)(\"(?:api_?key|access_?token|authorization)\"\\s*:\\s*\")[^\"]*(\")")

    fun redact(value: String, knownSecret: String? = null): String {
        var redacted = value
        if (!knownSecret.isNullOrEmpty()) redacted = redacted.replace(knownSecret, REDACTED)
        redacted = bearer.replace(redacted) { "${it.groupValues[1]}$REDACTED" }
        redacted = optionKey.replace(redacted) { "${it.groupValues[1]}$REDACTED" }
        redacted = jsonKey.replace(redacted) { "${it.groupValues[1]}$REDACTED${it.groupValues[2]}" }
        return redacted
    }

    const val REDACTED = "[REDACTED]"
}

