package com.hyped.hyped

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

class AndroidPushBridge(private val activity: MainActivity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hyped/notifications")
    private var permissionResult: MethodChannel.Result? = null
    private val permissionCode = 6291

    init {
        events = channel
        channel.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "status" -> result.success(mapOf("supported" to true,
                        "configured" to FirebaseApp.getApps(activity).isNotEmpty(), "enabled" to PushNotifications.permitted(activity)))
                    "requestPermission" -> {
                        if (Build.VERSION.SDK_INT >= 33 && !PushNotifications.permitted(activity)) {
                            if (permissionResult != null) { result.error("BUSY", "Permission request in progress.", null) }
                            else { permissionResult = result; activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), permissionCode) }
                        } else result.success(PushNotifications.permitted(activity))
                    }
                    "openSettings" -> {
                        activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                        result.success(null)
                    }
                    "getToken" -> {
                        if (!PushNotifications.permitted(activity)) result.success(null)
                        else {
                            val messaging = FirebaseMessaging.getInstance()
                            messaging.isAutoInitEnabled = true
                            messaging.token.addOnCompleteListener { task ->
                                if (task.isSuccessful) result.success(task.result)
                                else result.error("PUSH_UNAVAILABLE", "Notifications are unavailable. Please retry.", null)
                            }
                        }
                    }
                    "activateSession" -> {
                        val session = call.arguments as? String ?: error("Missing session")
                        java.util.UUID.fromString(session)
                        PushNotifications.activate(activity, session)
                        result.success(true)
                    }
                    "clearSession" -> { PushNotifications.clear(activity); result.success(null) }
                    "deleteToken" -> {
                        if (FirebaseApp.getApps(activity).isNotEmpty()) {
                            val messaging = FirebaseMessaging.getInstance()
                            messaging.isAutoInitEnabled = false
                            messaging.deleteToken().addOnCompleteListener { result.success(null) }
                        } else result.success(null)
                    }
                    "getInitialTap" -> {
                        result.success(PushNotifications.tap(activity, activity.intent))
                        if (activity.intent?.action == PushNotifications.OPEN) activity.intent.action = null
                    }
                    else -> result.notImplemented()
                }
            } catch (_: Exception) { result.error("PUSH_UNAVAILABLE", "Notifications are unavailable. Please retry.", null) }
        }
    }

    fun permissionResult(code: Int): Boolean {
        if (code != permissionCode) return false
        permissionResult?.success(PushNotifications.permitted(activity))
        permissionResult = null
        return true
    }

    fun opened(intent: Intent) {
        PushNotifications.tap(activity, intent)?.let { tap -> channel.invokeMethod("notificationTapped", tap) }
    }

    fun close() {
        if (events === channel) events = null
        permissionResult?.error("PUSH_UNAVAILABLE", "Permission request interrupted.", null)
        permissionResult = null
        channel.setMethodCallHandler(null)
    }

    companion object {
        private var events: MethodChannel? = null
        fun tokenChanged() { android.os.Handler(android.os.Looper.getMainLooper()).post { events?.invokeMethod("tokenChanged", null) } }
    }
}
