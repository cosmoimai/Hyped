package com.hyped.hyped

import java.net.URI
import java.util.Locale

/** The API issues 32 random bytes as exactly 43 unpadded base64url characters. */
object InviteAppLink {
    private val path = Regex("/invite/[A-Za-z0-9_-]{43}")
    private val hostLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun validHost(host: String): Boolean = host.length in 3..253 && host == host.lowercase(Locale.ROOT) &&
        host.contains('.') && host.split('.').all { hostLabel.matches(it) }

    fun canonical(raw: String?, configuredHost: String): String? {
        if (!validHost(configuredHost) || raw == null || raw.length > 512 ||
            raw.any { it.code !in 33..126 }) return null
        return try {
            val uri = URI(raw)
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                !uri.rawAuthority.equals(configuredHost, ignoreCase = true) ||
                uri.rawQuery != null || uri.rawFragment != null || !path.matches(uri.rawPath ?: "")) null
            else "https://$configuredHost${uri.rawPath}"
        } catch (_: Exception) { null } // URI exceptions can contain credentials; never propagate them.
    }
}

/** One initial handoff, then warm delivery. Latest valid intent wins before Flutter is ready. */
class InviteLinkMailbox(private val host: String) {
    private var pending: String? = null
    private var ready = false
    fun offer(raw: String?): String? {
        val canonical = InviteAppLink.canonical(raw, host) ?: return null
        if (ready) return canonical
        pending = canonical
        return null
    }
    fun start(): String? {
        ready = true
        val result = pending
        pending = null
        return result
    }
    fun clear() { pending = null }
    override fun toString() = "InviteLinkMailbox[REDACTED]"
}
