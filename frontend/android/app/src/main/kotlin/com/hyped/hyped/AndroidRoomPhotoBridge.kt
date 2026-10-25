package com.hyped.hyped

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import android.provider.MediaStore
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** System picker only. No library permission, persisted URI grants, file paths or original metadata. */
class AndroidRoomPhotoBridge(private val activity: MainActivity, messenger: BinaryMessenger) {
    private val channel = MethodChannel(messenger, "hyped/room_photo")
    private var pending: MethodChannel.Result? = null
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private val requestCode = 6317
    init {
        channel.setMethodCallHandler { call, result ->
            if (call.method != "pick") { result.notImplemented(); return@setMethodCallHandler }
            if (pending != null) { result.error("PHOTO_BUSY", "Finish selecting a photo first.", null); return@setMethodCallHandler }
            try {
                pending = result
                val picker = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
                    else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"))
                activity.startActivityForResult(picker, requestCode)
            } catch (_: Exception) { finish(null, "PHOTO_UNAVAILABLE") }
        }
    }
    fun result(code: Int, result: Int, data: Intent?): Boolean {
        if (code != requestCode) return false
        if (result != Activity.RESULT_OK || data?.data == null) { finish(null, null); return true }
        val uri = data.data!!
        worker.execute {
            try {
                val raw = activity.contentResolver.openInputStream(uri)?.use { RoomPhotoInput.bounded(it, 5 * 1024 * 1024) }
                    ?: throw IllegalArgumentException()
                if (raw.size > 5 * 1024 * 1024) { finish(null, "PHOTO_TOO_LARGE"); return@execute }
                val mime = activity.contentResolver.getType(uri)
                if (mime == "image/gif" || !RoomPhotoInput.staticImage(raw)) {
                    finish(null, "PHOTO_ANIMATED"); return@execute
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) {
                    finish(null, "PHOTO_INVALID"); return@execute
                }
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: throw IllegalArgumentException()
                val orientation = try { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
                val matrix = Matrix()
                when (orientation) {
                    2 -> matrix.setScale(-1f, 1f)
                    3 -> matrix.setRotate(180f)
                    4 -> matrix.setScale(1f, -1f)
                    5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                    6 -> matrix.setRotate(90f)
                    7 -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
                    8 -> matrix.setRotate(270f)
                }
                val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (normalized !== bitmap) bitmap.recycle()
                try {
                    var bytes: ByteArray? = null
                    for (quality in intArrayOf(85, 70, 55, 40)) {
                        val output = ByteArrayOutputStream()
                        normalized.compress(Bitmap.CompressFormat.JPEG, quality, output)
                        if (output.size() <= 1572864) { bytes = output.toByteArray(); break }
                    }
                    if (bytes == null) finish(null, "PHOTO_TOO_LARGE")
                    else finish(mapOf("bytes" to bytes, "width" to normalized.width, "height" to normalized.height), null)
                } finally { normalized.recycle() }
            } catch (_: OutOfMemoryError) { finish(null, "PHOTO_TOO_LARGE") }
            catch (_: Exception) { finish(null, "PHOTO_INVALID") }
        }
        return true
    }
    private fun finish(value: Any?, error: String?) {
        activity.runOnUiThread {
            val callback = pending
            pending = null
            if (!closed) {
                if (error == null) callback?.success(value)
                else callback?.error(error, "Select a static photo up to 5 MB. Please try another photo.", null)
            }
        }
    }
    fun close() {
        pending?.error("PHOTO_UNAVAILABLE", "Photo selection closed.", null)
        pending = null; closed = true; worker.shutdownNow(); channel.setMethodCallHandler(null)
    }
}
