package com.hyped.hyped

import org.junit.Assert.*
import org.junit.Test

class InviteAppLinkTest {
    private val host = "links.hyped.example"
    private val token = "A".repeat(43)
    private val link = "https://$host/invite/$token"

    @Test fun canonicalizesSchemeAndHostButPreservesTokenCase() {
        val mixed = "aB_-".repeat(10) + "xYz"
        assertEquals("https://$host/invite/$mixed",
            InviteAppLink.canonical("HTTPS://LINKS.HYPED.EXAMPLE/invite/$mixed", host))
    }
    @Test fun onlyExactConfiguredDomainIsAccepted() {
        for (authority in listOf("evil.example", "sub.$host", "$host.evil.example", "$host.",
            "user@$host", "$host:443", "$host:8443", "links.hyped.examp1e", "línks.hyped.example")) {
            assertNull(InviteAppLink.canonical("https://$authority/invite/$token", host))
        }
    }
    @Test fun rejectsUnexpectedSchemesAndUnrelatedPaths() {
        for (raw in listOf("http://$host/invite/$token", "hyped://$host/invite/$token", "//$host/invite/$token",
            "https://$host/room/$token", "https://$host/Invite/$token", "$link/", "$link/extra", "$link?x=1", "$link#x")) {
            assertNull(InviteAppLink.canonical(raw, host))
        }
    }
    @Test fun rejectsEncodingAliasesTraversalAndMalformedUris() {
        for (raw in listOf("https://$host/invite/%41" + "A".repeat(42),
            "https://$host/a/../invite/$token", "https://$host//invite/$token", " $link", "$link\n",
            "https://$host\\evil/invite/$token", "https://$host/invite/%2F$token", "https://[bad/invite/$token")) {
            assertNull(InviteAppLink.canonical(raw, host))
        }
    }
    @Test fun enforcesBackendTokenShapeAndInputBounds() {
        for (value in listOf("A".repeat(42), "A".repeat(44), "A".repeat(42) + "=", "A".repeat(42) + "+", "A".repeat(2048))) {
            assertNull(InviteAppLink.canonical("https://$host/invite/$value", host))
        }
        assertEquals(link, InviteAppLink.canonical(link, host))
        assertNull(InviteAppLink.canonical(null, host))
    }
    @Test fun absentOrInvalidConfigurationFailsClosed() {
        for (value in listOf("", "https://$host", "*.hyped.example", "LINKS.HYPED.EXAMPLE", "links..example", "-bad.example")) {
            assertNull(InviteAppLink.canonical(link, value))
        }
    }
    @Test fun coldStartConsumesLatestValidLinkOnce() {
        val mailbox = InviteLinkMailbox(host)
        assertNull(mailbox.offer(link))
        val newer = "https://$host/invite/" + "B".repeat(43)
        assertNull(mailbox.offer(newer))
        assertNull(mailbox.offer("https://evil.example/invite/$token"))
        assertEquals(newer, mailbox.start())
        assertNull(mailbox.start())
    }
    @Test fun warmStartUsesSameValidationAndCleanupDropsUnconsumedIntent() {
        val mailbox = InviteLinkMailbox(host)
        mailbox.offer(link)
        mailbox.clear()
        assertNull(mailbox.start())
        assertEquals(link, mailbox.offer(link))
        assertNull(mailbox.offer("http://$host/invite/$token"))
        assertFalse(mailbox.toString().contains(token))
    }
}
