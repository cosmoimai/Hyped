package com.hyped.hyped

import android.content.Intent
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

class AndroidInviteLinkBridge(messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hyped/android_invite_links")
    private val mailbox = InviteLinkMailbox(BuildConfig.INVITE_HOST)
    init {
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getInitialLink" -> result.success(mapOf("host" to BuildConfig.INVITE_HOST, "uri" to mailbox.start()))
                "clearPending" -> { mailbox.clear(); result.success(null) }
                else -> result.notImplemented()
            }
        }
    }
    fun opened(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val raw = intent.dataString
        // Consume VIEW data even when rejected. Engine/activity recreation must not replay a credential.
        intent.data = null
        intent.action = null
        mailbox.offer(raw)?.let { channel.invokeMethod("inviteLink", it) }
        return true
    }
    fun close() { mailbox.clear(); channel.setMethodCallHandler(null) }
}
