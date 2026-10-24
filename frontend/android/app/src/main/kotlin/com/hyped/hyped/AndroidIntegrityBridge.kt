package com.hyped.hyped

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.gms.tasks.Task
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel

/** Provider/evidence remain in memory. No SDK exception or evidence enters diagnostics. */
class AndroidIntegrityBridge(context: Context, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hyped/play_integrity")
    private val manager = IntegrityManagerFactory.createStandard(context.applicationContext)
    private var provider: Task<StandardIntegrityManager.StandardIntegrityTokenProvider>? = null
    private var closed = false

    init {
        channel.setMethodCallHandler { call, result ->
            if (call.method != "token") {
                result.notImplemented()
            } else {
                val hash = call.arguments as? String
                if (closed || BuildConfig.PLAY_INTEGRITY_PROJECT_NUMBER <= 0L ||
                    hash == null || !Regex("[A-Za-z0-9_-]{43}").matches(hash)) {
                    result.success(null)
                } else {
                    try {
                        val prepared = provider ?: manager.prepareIntegrityToken(
                            StandardIntegrityManager.PrepareIntegrityTokenRequest.builder()
                                .setCloudProjectNumber(BuildConfig.PLAY_INTEGRITY_PROJECT_NUMBER).build()
                        ).also { provider = it }
                        prepared.addOnCompleteListener { preparation ->
                            if (closed || !preparation.isSuccessful) {
                                provider = null
                                result.success(null)
                            } else {
                                preparation.result.request(
                                    StandardIntegrityManager.StandardIntegrityTokenRequest.builder()
                                        .setRequestHash(hash).build()
                                ).addOnCompleteListener { token ->
                                    if (!token.isSuccessful) provider = null
                                    result.success(if (!closed && token.isSuccessful) token.result.token() else null)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        provider = null
                        result.success(null)
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        provider = null
        channel.setMethodCallHandler(null)
    }
}
