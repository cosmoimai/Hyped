package com.hyped.hyped

/** No state or logging: credentials exist only in the explicitly requested share payload. */
object InvitationShareText {
    private val codePattern = Regex("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}")
    fun build(code: String?, url: String?, configuredHost: String): String? {
        if (code == null || !codePattern.matches(code)) return null
        val link = InviteAppLink.canonical(url, configuredHost)
        return "Join my private countdown on Hyped!\nRoom code: $code\n" +
            "Open Hyped and tap Join, then enter this code." +
            (link?.let { "\n$it" } ?: "")
    }
}
