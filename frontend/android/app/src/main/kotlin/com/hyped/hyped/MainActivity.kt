package com.hyped.hyped

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var integrityBridge: AndroidIntegrityBridge? = null
    private var inviteBridge: AndroidInviteLinkBridge? = null
    private var pushBridge: AndroidPushBridge? = null
    private var widgetChannel: MethodChannel? = null

    override fun onNewIntent(intent: Intent) {
        val inviteHandled = inviteBridge?.opened(intent) == true
        super.onNewIntent(intent)
        setIntent(intent)
        if (inviteHandled) return
        pushBridge?.opened(intent)
        HypedCountdownWidgetProvider.launchLocation(this, intent)?.let {
            widgetChannel?.invokeMethod("widgetTapped", it)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        inviteBridge = AndroidInviteLinkBridge(flutterEngine.dartExecutor.binaryMessenger)
        inviteBridge?.opened(intent)
        super.configureFlutterEngine(flutterEngine)
        integrityBridge = AndroidIntegrityBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        pushBridge = AndroidPushBridge(this, flutterEngine.dartExecutor.binaryMessenger)
        widgetChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "hyped/android_widgets")
        widgetChannel!!.setMethodCallHandler { call, result ->
            try {
                val store = HypedWidgetStore.store(this)
                val ids = HypedWidgetStore.activeIds(this)
                val args = call.arguments as? Map<*, *> ?: emptyMap<Any, Any>()
                when (call.method) {
                    "getLaunchLocation" -> {
                        val location = HypedCountdownWidgetProvider.launchLocation(this, intent)
                        // Consume only widget launches; subsequent Flutter engine setup must not replay them.
                        if (intent?.action == HypedCountdownWidgetProvider.OPEN_WIDGET) intent.action = null
                        result.success(location)
                        return@setMethodCallHandler
                    }
                    "listWidgets" -> {
                        store.prune(ids)
                        result.success(ids.sorted().map { id -> mapOf(
                            "appWidgetId" to id,
                            "roomId" to store.room(id)?.roomId?.takeIf { it.isNotEmpty() },
                            "size" to if (android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className == HypedMediumWidgetProvider::class.java.name) "medium" else "small",
                        ) })
                        return@setMethodCallHandler
                    }
                    "cacheRooms" -> store.sync((call.arguments as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(WidgetRoom::from) } ?: emptyList(), ids)
                    "cacheRoom" -> store.sync(listOfNotNull(WidgetRoom.from(args)), ids)
                    "selectRoom" -> {
                        val id = (args["appWidgetId"] as? Number)?.toInt() ?: error("Invalid widget")
                        val room = (args["room"] as? Map<*, *>)?.let(WidgetRoom::from) ?: error("Invalid room")
                        check(store.select(id, room, ids))
                    }
                    "clearSelection" -> store.remove((args["appWidgetId"] as? Number)?.toInt() ?: error("Invalid widget"))
                    "markRoom" -> store.mark(args["roomId"] as? String ?: "", args["state"] as? String ?: "", ids)
                    "clearProtectedContent" -> store.clear()
                    else -> { result.notImplemented(); return@setMethodCallHandler }
                }
                HypedCountdownWidgetProvider.updateAll(this)
                result.success(null)
            } catch (_: Exception) {
                result.error("WIDGET_UPDATE_FAILED", "Could not update widget data.", null)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (pushBridge?.permissionResult(requestCode) != true) super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        integrityBridge?.close()
        integrityBridge = null
        inviteBridge?.close()
        inviteBridge = null
        pushBridge?.close()
        pushBridge = null
        widgetChannel?.setMethodCallHandler(null)
        widgetChannel = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
}
