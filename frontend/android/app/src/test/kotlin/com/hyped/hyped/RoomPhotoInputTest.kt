package com.hyped.hyped

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class RoomPhotoInputTest {
    @Test fun inputReadStopsAtLimitPlusOne() {
        assertEquals(101, RoomPhotoInput.bounded(ByteArrayInputStream(ByteArray(100000)), 100).size)
        assertEquals(20, RoomPhotoInput.bounded(ByteArrayInputStream(ByteArray(20)), 100).size)
    }
    @Test fun gifIsNeverSilentlyConvertedToFirstFrame() {
        assertFalse(RoomPhotoInput.staticImage("GIF89a........".toByteArray()))
        assertFalse(RoomPhotoInput.staticImage(ByteArray(3)))
    }
    @Test fun animatedPngAndInvalidLengthsAreRejected() {
        val bytes = ByteArray(28)
        bytes[0] = 137.toByte(); "PNG".toByteArray().copyInto(bytes, 1)
        ByteBuffer.wrap(bytes, 8, 4).putInt(8); "acTL".toByteArray().copyInto(bytes, 12)
        assertFalse(RoomPhotoInput.staticImage(bytes))
        "IDAT".toByteArray().copyInto(bytes, 12); ByteBuffer.wrap(bytes, 8, 4).putInt(Int.MAX_VALUE)
        assertFalse(RoomPhotoInput.staticImage(bytes))
    }
    @Test fun staticJpegMayProceedToBoundedBitmapDecode() {
        val bytes = ByteArray(16); bytes[0] = 255.toByte(); bytes[1] = 216.toByte()
        assertTrue(RoomPhotoInput.staticImage(bytes))
    }
}
