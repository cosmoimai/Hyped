package com.hyped.hyped

import org.junit.Assert.*
import org.junit.Test

class PushEnvelopeTest {
    private val session = "019b1f20-4152-7ce8-ae9e-b12b87fe8a31"
    private val id = "019b1f20-4152-7ce8-ae9e-b12b87fe8a32"
    private val room = "019b1f33-e664-7ef4-985e-76b3ac298620"
    private fun data() = mapOf("type" to "EVENT_REMINDER", "sessionId" to session,
        "notificationId" to id, "roomId" to room, "reminderKind" to "day_before")

    @Test fun acceptsValidReminderForCurrentSession() {
        assertEquals(room, PushEnvelope.accept(data(), session, emptySet(), true)?.roomId)
    }
    @Test fun duplicateNotificationIdIsSuppressed() {
        assertNull(PushEnvelope.accept(data(), session, setOf(id), true))
    }
    @Test fun signedOutAndDifferentAccountNeverPresentOldReminder() {
        assertNull(PushEnvelope.accept(data(), null, emptySet(), true))
        assertNull(PushEnvelope.accept(data(), id, emptySet(), true))
    }
    @Test fun deniedPermissionNeverPresentsReminder() {
        assertNull(PushEnvelope.accept(data(), session, emptySet(), false))
    }
    @Test fun onlyThreeFixedKindsAreAccepted() {
        for (kind in setOf("day_before", "hour_before", "event_time")) {
            assertNotNull(PushEnvelope.parse(data() + ("reminderKind" to kind)))
        }
        assertNull(PushEnvelope.parse(data() + ("reminderKind" to "custom")))
    }
    @Test fun invalidPayloadCannotInjectNavigationOrPresentationText() {
        assertNull(PushEnvelope.parse(data() + ("roomId" to "../../private")))
        assertNull(PushEnvelope.parse(data() + ("type" to "UNKNOWN")))
        assertEquals(PushEnvelope(id, session, room), PushEnvelope.parse(data() + ("title" to "Private title")))
    }
}
