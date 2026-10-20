package com.hyped.hyped

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HypedWidgetStore {
    private const val PREFS = "hyped_countdown_widgets"
    private const val SELECTED_ROOM_ID = "selected_room_id"
    private const val ROOMS_JSON = "rooms_json"
    private const val SELECTED_ROOM_JSON = "selected_room_json"

    fun cacheRooms(context: Context, rooms: List<Map<String, Any?>>) {
        val array = JSONArray()
        rooms.forEach { array.put(JSONObject(it)) }
        val selected = selectedRoomId(context)
        context.prefs().edit().putString(ROOMS_JSON, array.toString()).apply()
        if (selected != null) {
            selectRoom(context, selected)
        }
    }

    fun cacheRoom(context: Context, room: Map<String, Any?>) {
        val roomId = room["roomId"] as? String ?: return
        val rooms = rooms(context).filterNot { it.optString("roomId") == roomId }.toMutableList()
        rooms.add(JSONObject(room))
        val array = JSONArray()
        rooms.forEach { array.put(it) }
        val edits = context.prefs().edit().putString(ROOMS_JSON, array.toString())
        if (selectedRoomId(context) == roomId) {
            edits.putString(SELECTED_ROOM_JSON, JSONObject(room).toString())
        }
        edits.apply()
    }

    fun selectedRoomId(context: Context): String? = context.prefs().getString(SELECTED_ROOM_ID, null)

    fun selectRoom(context: Context, roomId: String) {
        val room = rooms(context).firstOrNull { it.optString("roomId") == roomId }
        val edits = context.prefs().edit().putString(SELECTED_ROOM_ID, roomId)
        if (room != null) {
            edits.putString(SELECTED_ROOM_JSON, room.toString())
        } else {
            edits.remove(SELECTED_ROOM_JSON)
        }
        edits.apply()
    }

    fun clearSelection(context: Context) {
        context.prefs().edit()
            .remove(SELECTED_ROOM_ID)
            .remove(SELECTED_ROOM_JSON)
            .apply()
    }

    fun selectedRoom(context: Context): JSONObject? {
        val json = context.prefs().getString(SELECTED_ROOM_JSON, null) ?: return null
        return runCatching { JSONObject(json) }.getOrNull()
    }

    private fun rooms(context: Context): List<JSONObject> {
        val json = context.prefs().getString(ROOMS_JSON, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) { index -> array.getJSONObject(index) }
        }.getOrDefault(emptyList())
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
