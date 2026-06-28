package com.omnigraph.mobile.codec

import kotlin.math.ceil
import kotlin.math.sqrt

object MethodACodec {
    const val SAMPLE_RATE = 44_100
    const val CHANNEL_COUNT = 1
    const val BITS_PER_SAMPLE = 16

    fun encode(samples: ShortArray): RgbImage {
        require(samples.isNotEmpty()) { "No PCM samples were provided." }

        val values = ByteArray(samples.size)
        samples.forEachIndexed { index, sample ->
            values[index] = int16ToUInt8(sample).toByte()
        }

        val firstSplit = values.size / 3
        val secondSplit = (values.size * 2) / 3
        val redLength = firstSplit
        val greenLength = secondSplit - firstSplit
        val blueLength = values.size - secondSplit
        val segmentCapacity = maxOf(redLength, greenLength, blueLength, 1)
        val side = ceil(sqrt(segmentCapacity.toDouble())).toInt().coerceAtLeast(1)
        val pixelCount = side * side
        val pixels = ByteArray(pixelCount * 3)

        for (index in 0 until pixelCount) {
            val pixelOffset = index * 3
            pixels[pixelOffset] = if (index < redLength) values[index] else 0
            pixels[pixelOffset + 1] = if (index < greenLength) values[firstSplit + index] else 0
            pixels[pixelOffset + 2] = if (index < blueLength) values[secondSplit + index] else 0
        }

        return RgbImage(side, side, pixels)
    }

    fun decode(image: RgbImage): ShortArray {
        val pixelCount = image.width * image.height
        val samples = ShortArray(pixelCount * 3)

        for (index in 0 until pixelCount) {
            samples[index] = uint8ToInt16(image.pixels[index * 3].toInt() and 0xFF)
        }
        for (index in 0 until pixelCount) {
            samples[pixelCount + index] = uint8ToInt16(image.pixels[index * 3 + 1].toInt() and 0xFF)
        }
        for (index in 0 until pixelCount) {
            samples[pixelCount * 2 + index] = uint8ToInt16(image.pixels[index * 3 + 2].toInt() and 0xFF)
        }

        return samples
    }

    fun int16ToUInt8(sample: Short): Int {
        val shifted = sample.toInt() + 32_768
        return ((shifted.toFloat() / 65_535f) * 255f).toInt().coerceIn(0, 255)
    }

    fun uint8ToInt16(value: Int): Short {
        val clamped = value.coerceIn(0, 255)
        val sample = ((clamped.toFloat() / 255f) * 65_535f - 32_768f).toInt()
        return sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
