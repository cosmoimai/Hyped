package com.hyped.hyped

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

open class HypedCountdownWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_LOCALE_CHANGED)) {
            updateAll(context)
        } else {
            super.onReceive(context, intent)
        }
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)
    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { HypedWidgetStore.store(context).remove(it) }
    }
    override fun onDisabled(context: Context) = updateAll(context)
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        // Snapshots are excluded from backup; never restore another account's content.
        oldIds.forEach { HypedWidgetStore.store(context).remove(it) }
        newIds.forEach { HypedWidgetStore.store(context).remove(it) }
        updateAll(context)
    }

    companion object {
        const val OPEN_WIDGET = "com.hyped.hyped.OPEN_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = HypedWidgetStore.activeIds(context)
            val store = HypedWidgetStore.store(context)
            store.prune(ids)
            ids.forEach { id ->
                val medium = manager.getAppWidgetInfo(id)?.provider?.className == HypedMediumWidgetProvider::class.java.name
                val views = RemoteViews(context.packageName,
                    if (medium) R.layout.hyped_countdown_widget_medium else R.layout.hyped_countdown_widget)
                val room = store.room(id)
                val active = room?.state == "active"
                val title = when (room?.state) {
                    "active", "archived" -> room.title
                    "removed" -> "Room removed"
                    "unavailable" -> "Room unavailable"
                    else -> "Choose a countdown"
                }
                val state = when (room?.state) {
                    "active" -> "Last synced ${WidgetText.localTime(room.syncedAt)}"
                    "archived" -> "Archived · Last synced ${WidgetText.localTime(room.syncedAt)}"
                    "removed" -> "Choose another room"
                    "unavailable" -> "Open Hyped to check access"
                    else -> "Tap to select a room"
                }
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_countdown, if (active) WidgetText.countdown(room!!.eventAt, System.currentTimeMillis())
                    else if (room?.state == "archived") "Room archived" else "Open Hyped")
                views.setTextViewText(R.id.widget_event_time, if (active || room?.state == "archived") WidgetText.localTime(room!!.eventAt) else "")
                views.setTextViewText(R.id.widget_state, state)
                views.setInt(R.id.widget_root, "setBackgroundResource", background(room?.presetKey))
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent(context, id, !active && room?.state != "archived"))
                views.setOnClickPendingIntent(R.id.widget_brand, pendingIntent(context, id, true))
                manager.updateAppWidget(id, views)
            }
        }

        private fun background(preset: String?) = when (preset) {
            "mint-sky-01" -> R.drawable.widget_mint_background
            "blue-lilac-02" -> R.drawable.widget_lilac_background
            "peach-gold-01", "sunset-coral-01" -> R.drawable.widget_peach_background
            "aurora-green-01" -> R.drawable.widget_forest_background
            else -> R.drawable.widget_soft_blue_background
        }

        private fun pendingIntent(context: Context, id: Int, settings: Boolean): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = OPEN_WIDGET
                data = Uri.parse("hyped-widget://${if (settings) "settings" else "room"}/$id")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            // IDs are unique per installed widget; URI/action also separate room and settings intents.
            return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun launchLocation(context: Context, intent: Intent?): String? {
            if (intent?.action != OPEN_WIDGET) return null
            val uri = intent.data ?: return null
            if (uri.scheme != "hyped-widget" || uri.pathSegments.size != 1) return null
            val id = uri.lastPathSegment?.toIntOrNull() ?: return null
            if (id !in HypedWidgetStore.activeIds(context)) return null
            val room = HypedWidgetStore.store(context).room(id)
            return when (uri.host) {
                "settings" -> "/widgets/room?widgetId=$id"
                "room" -> if (room != null && room.state in setOf("active", "archived")) "/rooms/${Uri.encode(room.roomId)}"
                    else "/widgets/room?widgetId=$id"
                else -> null
            }
        }
    }
}

class HypedSmallWidgetProvider : HypedCountdownWidgetProvider()
class HypedMediumWidgetProvider : HypedCountdownWidgetProvider()
