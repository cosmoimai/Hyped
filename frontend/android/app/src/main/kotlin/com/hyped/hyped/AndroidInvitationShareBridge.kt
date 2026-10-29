package com.hyped.hyped

import android.content.Intent
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

class AndroidInvitationShareBridge(private val activity: MainActivity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hyped/invitation_share")
    init {
        channel.setMethodCallHandler { call, result ->
            if (call.method != "share") { result.notImplemented(); return@setMethodCallHandler }
            try {
                val args = call.arguments as? Map<*, *>
                val text = InvitationShareText.build(args?.get("roomCode") as? String,
                    args?.get("inviteUrl") as? String, BuildConfig.INVITE_HOST)
                if (text == null || activity.isFinishing || activity.isDestroyed) {
                    result.error("SHARE_UNAVAILABLE", "Could not share invitation.", null)
                    return@setMethodCallHandler
                }
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                activity.startActivity(Intent.createChooser(send, "Share Hyped invite"))
                result.success(null)
            } catch (_: Exception) {
                result.error("SHARE_UNAVAILABLE", "Could not share invitation.", null)
            }
        }
    }
    fun close() { channel.setMethodCallHandler(null) }
}
