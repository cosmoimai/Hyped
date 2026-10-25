package com.hyped.hyped

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class WidgetSnapshotTest {
    @Test fun widgetSnapshotsPreserveAllBackendThemeKeys() {
        for (key in listOf("soft-blue-01", "sunset-coral-01", "mint-sky-01",
            "blue-lilac-02", "peach-gold-01", "aurora-green-01")) {
            assertEquals(key, WidgetRoom.from(room().payload() + ("presetKey" to key))?.presetKey)
        }
        assertEquals("soft-blue-01", WidgetRoom.from(room().payload() + ("presetKey" to "untrusted-theme"))?.presetKey)
    }
    private class Disk : WidgetPersistence {
        val values = mutableMapOf<Int, String>()
        override fun ids() = values.keys.toSet()
        override fun read(id: Int) = values[id]
        override fun write(id: Int, value: String) { values[id] = value }
        override fun remove(id: Int) { values.remove(id) }
        override fun clear() = values.clear()
    }
    private fun room(id: String = "room-one", state: String = "active") = WidgetRoom(id, "Trip", 1893456000000, "mint-sky-01", 1800000000000, state)

    @Test fun countdownBoundaries() {
        mapOf(1L to "Less than 1m", 999L to "Less than 1m", 59999L to "Less than 1m",
            60000L to "1m", 3599999L to "59m", 3600000L to "1h 0m",
            86399999L to "23h 59m", 86400000L to "1d 0h", 97200000L to "1d 3h").forEach { (remaining, text) ->
            assertEquals(text, WidgetText.countdown(1000 + remaining, 1000))
        }
    }
    @Test fun equalityAndPastAreStarted() {
        assertEquals("Event started", WidgetText.countdown(1000, 1000))
        assertEquals("Event started", WidgetText.countdown(999, 1000))
    }
    @Test fun deviceLocalTimeIncludesDateRollover() {
        assertEquals("Jan 1, 1970 at 5:30 AM", WidgetText.localTime(0, TimeZone.getTimeZone("Asia/Kolkata"), Locale.US))
        assertEquals("Dec 31, 1969 at 4:00 PM", WidgetText.localTime(0, TimeZone.getTimeZone("America/Los_Angeles"), Locale.US))
    }
    @Test fun localTimeFollowsDst() {
        val zone = TimeZone.getTimeZone("America/New_York")
        assertEquals("Mar 8, 2026 at 1:59 AM", WidgetText.localTime(1772953140000, zone, Locale.US))
        assertEquals("Mar 8, 2026 at 3:00 AM", WidgetText.localTime(1772953200000, zone, Locale.US))
    }
    @Test fun widgetsPersistDifferentRoomsAcrossProcessRestart() {
        val disk = Disk()
        val store = WidgetSnapshotStore(disk)
        assertTrue(store.select(41, room(), setOf(41, 42)))
        assertTrue(store.select(42, room("room-two"), setOf(41, 42)))
        val restored = WidgetSnapshotStore(disk)
        assertEquals("room-one", restored.room(41)?.roomId)
        assertEquals("room-two", restored.room(42)?.roomId)
        assertEquals(42, JSONObject(disk.read(42)!!).getInt("appWidgetId"))
    }
    @Test fun noSelectionIsEmpty() {
        assertNull(WidgetSnapshotStore(Disk()).room(41))
    }
    @Test fun updatesTouchOnlyWidgetsSelectingTheSameRoom() {
        val store = WidgetSnapshotStore(Disk())
        val ids = setOf(41, 42, 43)
        store.select(41, room(), ids)
        store.select(42, room("room-two"), ids)
        store.select(43, room(), ids)
        store.sync(listOf(room().copy(title = "Changed", syncedAt = 1900000000000)), ids)
        assertEquals("Changed", store.room(41)?.title)
        assertEquals("Changed", store.room(43)?.title)
        assertEquals("Trip", store.room(42)?.title)
    }
    @Test fun incompleteListAndOfflineLeaveSuccessfulDataUntouched() {
        val store = WidgetSnapshotStore(Disk())
        store.select(41, room(), setOf(41))
        store.sync(emptyList(), setOf(41))
        assertEquals(room(), store.room(41))
    }
    @Test fun archivedRoomIsExplicitAndPreservesSafeSnapshot() {
        val store = WidgetSnapshotStore(Disk())
        store.select(41, room(), setOf(41))
        store.sync(listOf(room(state = "archived")), setOf(41))
        assertEquals("archived", store.room(41)?.state)
        assertEquals("Trip", store.room(41)?.title)
    }
    @Test fun missingAndUnavailableRoomsRemoveProtectedFieldsWithoutAdvancingSyncTime() {
        for (state in listOf("removed", "unavailable")) {
            val store = WidgetSnapshotStore(Disk())
            store.select(41, room(), setOf(41))
            store.mark("room-one", state, setOf(41))
            assertEquals(state, store.room(41)?.state)
            assertEquals("", store.room(41)?.title)
            assertEquals(0L, store.room(41)?.eventAt)
            assertEquals(room().syncedAt, store.room(41)?.syncedAt)
        }
    }
    @Test fun leavingOrDeletingRoomRemovesMappingsAndLateSyncCannotReviveThem() {
        val disk = Disk()
        val store = WidgetSnapshotStore(disk)
        val ids = setOf(41, 42, 43)
        store.select(41, room(), ids)
        store.select(42, room(), ids)
        store.select(43, room("other-room"), ids)
        store.mark("room-one", "removed", ids)
        assertEquals("", store.room(41)?.roomId)
        assertEquals("removed", store.room(42)?.state)
        assertFalse(disk.read(41)!!.contains("room-one"))
        store.sync(listOf(room().copy(title = "Stale private title")), ids)
        assertEquals("", store.room(41)?.title)
        assertEquals("removed", WidgetSnapshotStore(disk).room(42)?.state)
        assertEquals("other-room", store.room(43)?.roomId)
    }
    @Test fun restoredRoomResumesAnExistingArchivedSelection() {
        val store = WidgetSnapshotStore(Disk())
        store.select(41, room(state = "archived"), setOf(41))
        store.sync(listOf(room()), setOf(41))
        assertEquals("active", store.room(41)?.state)
        assertEquals("Trip", store.room(41)?.title)
    }
    @Test fun logoutClearsEverySnapshotAndSelection() {
        val disk = Disk()
        val store = WidgetSnapshotStore(disk)
        store.select(41, room(), setOf(41, 42))
        store.select(42, room("room-two"), setOf(41, 42))
        store.clear()
        assertTrue(disk.values.isEmpty())
        assertNull(store.room(41))
    }
    @Test fun deletionAndStaleIdsArePrunedAndCannotBeSelected() {
        val disk = Disk()
        val store = WidgetSnapshotStore(disk)
        store.select(41, room(), setOf(41, 42))
        store.select(42, room(), setOf(41, 42))
        store.remove(41)
        store.prune(emptySet())
        assertFalse(store.select(42, room(), emptySet()))
        assertTrue(disk.values.isEmpty())
    }
    @Test fun unknownKeysAndUnsafeThemesCannotBeCopiedToPersistence() {
        val incoming = room().payload() + mapOf("accessToken" to "secret", "description" to "private", "presetKey" to "https://unsafe/image")
        val safe = WidgetRoom.from(incoming)!!
        val disk = Disk()
        WidgetSnapshotStore(disk).select(41, safe, setOf(41))
        val json = JSONObject(disk.read(41)!!)
        assertEquals(setOf("appWidgetId", "roomId", "title", "eventAt", "presetKey", "syncedAt", "state"), json.keys().asSequence().toSet())
        assertEquals("soft-blue-01", json.getString("presetKey"))
        assertFalse(disk.read(41)!!.contains("secret"))
    }
    @Test fun corruptOrMismatchedRecordsFailClosed() {
        val disk = Disk()
        val store = WidgetSnapshotStore(disk)
        disk.write(41, "invalid")
        assertNull(store.room(41))
        disk.write(41, JSONObject(room().payload() + ("appWidgetId" to 42)).toString())
        assertNull(store.room(41))
    }
    @Test fun malformedIdentifiersAndStatesAreRejected() {
        assertNull(WidgetRoom.from(room().payload() + ("roomId" to "../../private")))
        assertNull(WidgetRoom.from(room().payload() + ("state" to "unknown")))
    }
}
