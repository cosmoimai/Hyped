package com.hyped.hyped

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class HypedMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Fetch the current SDK token through the foreground bridge; never log it or copy it into app preferences.
        AndroidPushBridge.tokenChanged()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Backend uses data-only messages; presentation is always generic and session-gated.
        if (message.notification == null) PushNotifications.receive(this, message.data)
    }
}
