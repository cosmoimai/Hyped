package com.hyped.hyped

import org.junit.Assert.*
import org.junit.Test

class InvitationShareTextTest {
    private val code = "7K4M-P9QX"
    private val token = "a".repeat(43)
    @Test fun unconfiguredDomainSharesOnlyUsableCode() {
        val text = InvitationShareText.build(code, "https://localhost:8443/invite/$token", "")!!
        assertTrue(text.contains(code))
        assertFalse(text.contains("https://"))
        assertFalse(text.contains(token))
    }
    @Test fun configuredDomainAddsCanonicalLink() {
        val url = "https://invites.hyped.test/invite/$token"
        assertTrue(InvitationShareText.build(code, url, "invites.hyped.test")!!.endsWith(url))
    }
    @Test fun foreignOrMalformedLinksAreNeverShared() {
        for (url in listOf("https://evil.test/invite/$token", "http://invites.hyped.test/invite/$token",
            "https://invites.hyped.test/invite/$token?extra=1", "https://invites.hyped.test/invite/bad")) {
            assertFalse(InvitationShareText.build(code, url, "invites.hyped.test")!!.contains("http"))
        }
    }
    @Test fun rejectsInvalidCodesAndInjectedText() {
        for (value in listOf(null, "", "0000-1111", "$code\nprivate", "7K4MP9QX")) {
            assertNull(InvitationShareText.build(value, null, ""))
        }
    }
    @Test fun noDomainNeverProducesAPlaceholderLink() {
        assertEquals(InvitationShareText.build(code, null, ""),
            InvitationShareText.build(code, "https://example.com/invite/$token", ""))
    }
}
