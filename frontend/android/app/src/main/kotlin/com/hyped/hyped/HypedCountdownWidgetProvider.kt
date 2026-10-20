package com.hyped.hyped

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

open class HypedCountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intArrayOf(
                *manager.getAppWidgetIds(ComponentName(context, HypedSmallWidgetProvider::class.java)),
                *manager.getAppWidgetIds(ComponentName(context, HypedMediumWidgetProvider::class.java)),
            )
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id -> manager.updateAppWidget(id, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.hyped_countdown_widget)
            val room = HypedWidgetStore.selectedRoom(context)
            if (room == null) {
                views.setTextViewText(R.id.widget_title, "Choose a countdown")
                views.setTextViewText(R.id.widget_countdown, "Open Hyped")
                views.setTextViewText(R.id.widget_event_time, "Pick a room in widget settings")
                views.setTextViewText(R.id.widget_state, "No room selected")
            } else {
                val title = room.optString("title", "Hyped countdown")
                val eventAtMillis = parseMillis(room.optString("eventAt", ""))
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_countdown, countdown(eventAtMillis, System.currentTimeMillis()))
                views.setTextViewText(R.id.widget_event_time, eventText(eventAtMillis))
                views.setTextViewText(R.id.widget_state, "Last synced by Hyped")
            }
            views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
            return views
        }

        private fun parseMillis(value: String): Long? {
            if (value.isBlank()) return null
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            return runCatching { parser.parse(value)?.time }.getOrNull()
                ?: runCatching {
                    val fallback = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    fallback.timeZone = TimeZone.getTimeZone("UTC")
                    fallback.parse(value)?.time
                }.getOrNull()
        }

        private fun countdown(eventAtMillis: Long?, nowMillis: Long): String {
            if (eventAtMillis == null) return "Open Hyped"
            val seconds = (eventAtMillis - nowMillis) / 1000
            if (seconds <= 0) return "Event started"
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                days > 0 -> "${days}d ${hours % 24}h"
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m"
                else -> "Less than 1m"
            }
        }

        private fun eventText(eventAtMillis: Long?): String {
            if (eventAtMillis == null) return "Event time unavailable"
            val formatter = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            return formatter.format(Date(eventAtMillis))
        }

        private fun launchIntent(context: Context): PendingIntent {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getActivity(context, 0, intent, flags)
        }
    }
}

class HypedSmallWidgetProvider : HypedCountdownWidgetProvider()

class HypedMediumWidgetProvider : HypedCountdownWidgetProvider()
