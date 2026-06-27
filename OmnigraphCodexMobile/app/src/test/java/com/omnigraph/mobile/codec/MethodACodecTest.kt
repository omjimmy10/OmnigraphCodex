package com.omnigraph.mobile.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class MethodACodecTest {
    @Test
    fun int16ToUInt8MatchesPythonTruncation() {
        assertEquals(0, MethodACodec.int16ToUInt8(Short.MIN_VALUE))
        assertEquals(127, MethodACodec.int16ToUInt8(0))
        assertEquals(255, MethodACodec.int16ToUInt8(Short.MAX_VALUE))
    }

    @Test
    fun uint8ToInt16MatchesPythonTruncation() {
        assertEquals(Short.MIN_VALUE, MethodACodec.uint8ToInt16(0))
        assertEquals((-129).toShort(), MethodACodec.uint8ToInt16(127))
        assertEquals(Short.MAX_VALUE, MethodACodec.uint8ToInt16(255))
    }

    @Test
    fun encodeSplitsStreamIntoSequentialRgbSegments() {
        val image = MethodACodec.encode(
            shortArrayOf(
                Short.MIN_VALUE,
                0,
                Short.MAX_VALUE,
                Short.MIN_VALUE,
                0,
                Short.MAX_VALUE,
            ),
        )

        assertEquals(2, image.width)
        assertEquals(2, image.height)

        assertEquals(0, image.pixels[0].toInt() and 0xFF)
        assertEquals(255, image.pixels[1].toInt() and 0xFF)
        assertEquals(127, image.pixels[2].toInt() and 0xFF)

        assertEquals(127, image.pixels[3].toInt() and 0xFF)
        assertEquals(0, image.pixels[4].toInt() and 0xFF)
        assertEquals(255, image.pixels[5].toInt() and 0xFF)
    }

    @Test
    fun decodeFlattensRedThenGreenThenBlue() {
        val image = RgbImage(
            width = 2,
            height = 1,
            pixels = byteArrayOf(
                0, 10, 20,
                30, 40, 50,
            ),
        )

        val decoded = MethodACodec.decode(image)

        assertEquals(MethodACodec.uint8ToInt16(0), decoded[0])
        assertEquals(MethodACodec.uint8ToInt16(30), decoded[1])
        assertEquals(MethodACodec.uint8ToInt16(10), decoded[2])
        assertEquals(MethodACodec.uint8ToInt16(40), decoded[3])
        assertEquals(MethodACodec.uint8ToInt16(20), decoded[4])
        assertEquals(MethodACodec.uint8ToInt16(50), decoded[5])
    }
}
