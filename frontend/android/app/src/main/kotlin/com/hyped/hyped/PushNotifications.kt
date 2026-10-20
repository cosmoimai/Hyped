package com.hyped.hyped

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

object PushNotifications {
    const val OPEN = "com.hyped.hyped.OPEN_REMINDER"
    private const val CHANNEL = "event_reminders"
    private const val PREFS = "hyped_notifications"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun permitted(context: Context): Boolean {
        val manager = manager(context)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return manager.areNotificationsEnabled()
    }

    @Synchronized
    fun activate(context: Context, session: String) {
        if (prefs(context).getString("session", null) != session) clear(context)
        check(prefs(context).edit().putString("session", session).commit())
    }

    @Synchronized
    fun clear(context: Context) {
        manager(context).cancelAll()
        check(prefs(context).edit().clear().commit())
    }

    @Synchronized
    fun receive(context: Context, data: Map<String, String>) {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val records = prefs.all.keys.filter { it.startsWith("message.") }
        val expired = records.filter {
            runCatching { JSONObject(prefs.getString(it, "{}")!!).optLong("receivedAt") < now - 86_400_000 }
                .getOrDefault(true)
        }.toSet()
        val live = records.filterNot { it in expired }
        // Never evict a recent dedup key to make room: drop excessive new messages instead.
        if (live.size >= 4096) return
        val seen = live.map { it.removePrefix("message.") }.toSet()
        val envelope = PushEnvelope.accept(data, prefs.getString("session", null), seen, permitted(context)) ?: return
        val edit = prefs.edit()
        expired.forEach(edit::remove)
        edit.putString("message.${envelope.notificationId}", JSONObject(mapOf(
            "sessionId" to envelope.sessionId, "roomId" to envelope.roomId, "receivedAt" to now,
        )).toString())
        if (!edit.commit()) return // Persist deduplication before presentation.
        val intent = Intent(context, MainActivity::class.java).apply {
            action = OPEN
            this.data = Uri.parse("hyped-reminder://open/${envelope.notificationId}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap = PendingIntent.getActivity(context, envelope.notificationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val notification = builder.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hyped!")
            .setContentText("A countdown reminder is ready. Open Hyped to view it.")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(tap).build()
        try { manager(context).notify(envelope.notificationId, 0, notification) } catch (_: SecurityException) { }
    }

    @Synchronized
    fun tap(context: Context, intent: Intent?): Map<String, String>? {
        if (intent?.action != OPEN || intent.data?.scheme != "hyped-reminder" || intent.data?.host != "open") return null
        val id = intent.data?.lastPathSegment ?: return null
        val value = prefs(context).getString("message.$id", null) ?: return null
        val json = runCatching { JSONObject(value) }.getOrNull() ?: return null
        val session = prefs(context).getString("session", null) ?: return null
        if (json.optString("sessionId") != session) return null
        return mapOf("sessionId" to session, "roomId" to json.getString("roomId"))
    }
}
