package com.hyped.hyped

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object RoomPhotoInput {
    fun bounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= maximum) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
            if (count < 0) break
            if (count == 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    fun staticImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12 || String(bytes, 0, 3, Charsets.US_ASCII) == "GIF") return false
        val png = bytes[0] == 137.toByte() && String(bytes, 1, 3, Charsets.US_ASCII) == "PNG"
        val webp = String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        var position = if (png) 8 else if (webp) 12 else return true
        while (position + 12 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, position + if (png) 0 else 4, 4)
                .order(if (png) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN).int
            val chunk = String(bytes, position + if (png) 4 else 0, 4, Charsets.US_ASCII)
            if (chunk in setOf("acTL", "ANIM", "ANMF")) return false
            val advance = length.toLong() + if (png) 12 else 8 + (length and 1)
            if (length < 0 || advance < 8 || advance > bytes.size - position) return false
            position += advance.toInt()
        }
        return true
    }
}
