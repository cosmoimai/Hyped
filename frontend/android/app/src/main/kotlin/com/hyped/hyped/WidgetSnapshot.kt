package com.hyped.hyped

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The complete allowlist of data permitted outside secure storage. No API DTOs. */
data class WidgetRoom(
    val roomId: String,
    val title: String,
    val eventAt: Long,
    val presetKey: String,
    val syncedAt: Long,
    val state: String,
) {
    fun payload(): Map<String, Any> = mapOf(
        "roomId" to roomId, "title" to title, "eventAt" to eventAt,
        "presetKey" to presetKey, "syncedAt" to syncedAt, "state" to state,
    )

    fun restricted(state: String) = copy(title = "", eventAt = 0, presetKey = "soft-blue-01", state = state)

    companion object {
        val presets = setOf("soft-blue-01", "mint-sky-01", "blue-lilac-02", "sunset-peach-01", "forest-glow-01")
        fun from(values: Map<*, *>): WidgetRoom? {
            val id = values["roomId"] as? String ?: return null
            if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) return null
            val state = values["state"] as? String ?: return null
            if (state !in setOf("active", "archived", "removed", "unavailable")) return null
            val room = WidgetRoom(
                id,
                (values["title"] as? String ?: "").filterNot { it.isISOControl() }.take(160),
                (values["eventAt"] as? Number)?.toLong() ?: return null,
                (values["presetKey"] as? String)?.takeIf { it in presets } ?: "soft-blue-01",
                (values["syncedAt"] as? Number)?.toLong()?.coerceAtLeast(0) ?: return null,
                state,
            )
            return if (state in setOf("removed", "unavailable")) room.restricted(state) else room
        }
    }
}

interface WidgetPersistence {
    fun ids(): Set<Int>
    fun read(id: Int): String?
    fun write(id: Int, value: String)
    fun remove(id: Int)
    fun clear()
}

/** Pure persistence logic, shared by Android and local JVM tests. */
class WidgetSnapshotStore(private val disk: WidgetPersistence) {
    fun room(id: Int): WidgetRoom? = runCatching {
        val json = JSONObject(disk.read(id) ?: return null)
        if (json.getInt("appWidgetId") != id) return null
        WidgetRoom.from(json.keys().asSequence().associateWith { json.get(it) })
    }.getOrNull()

    fun select(id: Int, room: WidgetRoom, activeIds: Set<Int>): Boolean {
        prune(activeIds)
        if (id !in activeIds) return false
        disk.write(id, JSONObject(room.payload() + ("appWidgetId" to id)).toString())
        return true
    }

    fun sync(rooms: List<WidgetRoom>, activeIds: Set<Int>) {
        prune(activeIds)
        val byId = rooms.associateBy { it.roomId }
        activeIds.forEach { id ->
            val current = room(id) ?: return@forEach
            byId[current.roomId]?.let { select(id, it, activeIds) }
        }
    }

    fun mark(roomId: String, state: String, activeIds: Set<Int>) {
        if (state !in setOf("removed", "unavailable")) return
        activeIds.forEach { id ->
            room(id)?.takeIf { it.roomId == roomId }?.let { select(id, it.restricted(state), activeIds) }
        }
    }

    fun remove(id: Int) = disk.remove(id)
    fun clear() = disk.clear()
    fun prune(activeIds: Set<Int>) = (disk.ids() - activeIds).forEach(disk::remove)
}

object WidgetText {
    fun countdown(eventAt: Long, now: Long): String {
        if (eventAt <= now) return "Event started"
        val minutes = (eventAt - now) / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "Less than 1m"
        }
    }

    fun localTime(instant: Long, zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("MMM d, yyyy 'at' h:mm a", locale).apply { timeZone = zone }.format(Date(instant))
}
