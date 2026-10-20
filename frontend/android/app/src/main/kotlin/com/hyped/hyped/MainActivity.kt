package com.hyped.hyped

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "hyped/android_widgets")
            .setMethodCallHandler { call, result ->
                try {
                    when (call.method) {
                        "cacheRooms" -> {
                            @Suppress("UNCHECKED_CAST")
                            HypedWidgetStore.cacheRooms(
                                this,
                                call.arguments as? List<Map<String, Any?>> ?: emptyList(),
                            )
                            HypedCountdownWidgetProvider.updateAll(this)
                            result.success(null)
                        }
                        "cacheRoom" -> {
                            @Suppress("UNCHECKED_CAST")
                            val room = call.arguments as? Map<String, Any?>
                            if (room != null) HypedWidgetStore.cacheRoom(this, room)
                            HypedCountdownWidgetProvider.updateAll(this)
                            result.success(null)
                        }
                        "selectedRoomId" -> result.success(HypedWidgetStore.selectedRoomId(this))
                        "selectRoom" -> {
                            val roomId = call.arguments as? String
                            if (roomId != null) HypedWidgetStore.selectRoom(this, roomId)
                            HypedCountdownWidgetProvider.updateAll(this)
                            result.success(null)
                        }
                        "clearSelection" -> {
                            HypedWidgetStore.clearSelection(this)
                            HypedCountdownWidgetProvider.updateAll(this)
                            result.success(null)
                        }
                        else -> result.notImplemented()
                    }
                } catch (error: Exception) {
                    result.error("WIDGET_UPDATE_FAILED", "Could not update widget data.", null)
                }
            }
    }
}
