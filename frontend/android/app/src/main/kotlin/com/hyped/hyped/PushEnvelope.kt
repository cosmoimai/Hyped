package com.hyped.hyped

import java.util.UUID

/** No room titles or body content are accepted from push payloads. */
data class PushEnvelope(val notificationId: String, val sessionId: String, val roomId: String) {
    companion object {
        private fun uuid(value: String?): Boolean = value != null && runCatching {
            UUID.fromString(value).toString() == value.lowercase()
        }.getOrDefault(false)

        fun parse(data: Map<String, String>): PushEnvelope? {
            if (data["type"] != "EVENT_REMINDER" || data["reminderKind"] !in setOf("day_before", "hour_before", "event_time")) return null
            val id = data["notificationId"]
            val session = data["sessionId"]
            val room = data["roomId"]
            if (!uuid(id) || !uuid(session) || !uuid(room)) return null
            return PushEnvelope(id!!, session!!, room!!)
        }

        fun accept(data: Map<String, String>, activeSession: String?, seen: Set<String>, permitted: Boolean): PushEnvelope? {
            val envelope = parse(data) ?: return null
            return envelope.takeIf { permitted && it.sessionId == activeSession && it.notificationId !in seen }
        }
    }
}
