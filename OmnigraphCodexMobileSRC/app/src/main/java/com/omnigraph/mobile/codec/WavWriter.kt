package com.omnigraph.mobile.codec

import java.io.ByteArrayOutputStream

object WavWriter {
    fun write(samples: ShortArray, sampleRate: Int = MethodACodec.SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val output = ByteArrayOutputStream(44 + dataSize)

        output.writeAscii("RIFF")
        output.writeIntLE(36 + dataSize)
        output.writeAscii("WAVE")
        output.writeAscii("fmt ")
        output.writeIntLE(16)
        output.writeShortLE(1)
        output.writeShortLE(MethodACodec.CHANNEL_COUNT)
        output.writeIntLE(sampleRate)
        output.writeIntLE(sampleRate * MethodACodec.CHANNEL_COUNT * MethodACodec.BITS_PER_SAMPLE / 8)
        output.writeShortLE(MethodACodec.CHANNEL_COUNT * MethodACodec.BITS_PER_SAMPLE / 8)
        output.writeShortLE(MethodACodec.BITS_PER_SAMPLE)
        output.writeAscii("data")
        output.writeIntLE(dataSize)

        samples.forEach { sample -> output.writeShortLE(sample.toInt()) }

        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
