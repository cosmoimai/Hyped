package com.hyped.hyped

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object HypedWidgetStore {
    private const val PREFS = "hyped_countdown_widgets"

    fun activeIds(context: Context): Set<Int> {
        val manager = AppWidgetManager.getInstance(context)
        return (manager.getAppWidgetIds(ComponentName(context, HypedSmallWidgetProvider::class.java)).toList() +
            manager.getAppWidgetIds(ComponentName(context, HypedMediumWidgetProvider::class.java)).toList()).toSet()
    }

    fun store(context: Context): WidgetSnapshotStore {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Discard the old global selection and room catalog, including any unknown legacy fields.
        val legacy = prefs.all.keys.filterNot { it.matches(Regex("widget\\.[1-9][0-9]*")) }
        if (legacy.isNotEmpty()) prefs.edit().apply { legacy.forEach(::remove) }.commit()
        return WidgetSnapshotStore(object : WidgetPersistence {
            override fun ids() = prefs.all.keys.mapNotNull { it.removePrefix("widget.").toIntOrNull() }.toSet()
            override fun read(id: Int): String? = prefs.getString("widget.$id", null)
            override fun write(id: Int, value: String) { check(prefs.edit().putString("widget.$id", value).commit()) }
            override fun remove(id: Int) { check(prefs.edit().remove("widget.$id").commit()) }
            override fun clear() { check(prefs.edit().clear().commit()) }
        })
    }
}
