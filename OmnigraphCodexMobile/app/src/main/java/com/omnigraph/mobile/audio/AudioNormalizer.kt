package com.omnigraph.mobile.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.omnigraph.mobile.codec.MethodACodec
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class NormalizedAudio(
    val samples: ShortArray,
    val sourceSampleRate: Int,
    val durationMs: Long,
)

object AudioNormalizer {
    fun normalize(context: Context, uri: Uri): NormalizedAudio {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = findAudioTrack(extractor)
            require(trackIndex >= 0) { "No audio track was found in the selected file." }

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME)) {
                "The audio track has no MIME type."
            }
            var outputSampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            var pcmEncoding = if (inputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val builder = ShortArrayBuilder()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)
                        val sampleSize = if (inputBuffer != null) {
                            extractor.readSampleData(inputBuffer, 0)
                        } else {
                            -1
                        }

                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferId,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        outputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            AudioFormat.ENCODING_PCM_16BIT
                        }
                    }
                    else -> if (outputBufferId >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            appendPcm(outputBuffer, bufferInfo, outputChannelCount, pcmEncoding, builder)
                        }

                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputBufferId, false)
                    }
                }
            }

            val monoSamples = builder.toShortArray()
            require(monoSamples.isNotEmpty()) { "The selected audio produced no decoded PCM samples." }

            val normalized = resampleToTarget(monoSamples, outputSampleRate)
            val durationMs = (normalized.size * 1_000L) / MethodACodec.SAMPLE_RATE
            return NormalizedAudio(normalized, outputSampleRate, durationMs)
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return -1
    }

    private fun appendPcm(
        outputBuffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channelCount: Int,
        pcmEncoding: Int,
        builder: ShortArrayBuilder,
    ) {
        val buffer = outputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)

        when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> appendFloatPcm(buffer, channelCount, builder)
            AudioFormat.ENCODING_PCM_8BIT -> append8BitPcm(buffer, channelCount, builder)
            else -> append16BitPcm(buffer, channelCount, builder)
        }
    }

    private fun append16BitPcm(buffer: ByteBuffer, channelCount: Int, builder: ShortArrayBuilder) {
        val frameSize = channelCount * 2
        val frames = buffer.remaining() / frameSize
        repeat(frames) {
            var sum = 0
            repeat(channelCount) { sum += buffer.short.toInt() }
            builder.add((sum / channelCount).coerceToShort())
        }
    }

    private fun appendFloatPcm(buffer: ByteBuffer, channelCount: Int, builder: ShortArrayBuilder) {
        val frameSize = channelCount * 4
        val frames = buffer.remaining() / frameSize
        repeat(frames) {
            var sum = 0f
            repeat(channelCount) { sum += buffer.float.coerceIn(-1f, 1f) }
            val mixed = (sum / channelCount) * Short.MAX_VALUE
            builder.add(mixed.roundToInt().coerceToShort())
        }
    }

    private fun append8BitPcm(buffer: ByteBuffer, channelCount: Int, builder: ShortArrayBuilder) {
        val frames = buffer.remaining() / channelCount
        repeat(frames) {
            var sum = 0
            repeat(channelCount) { sum += ((buffer.get().toInt() and 0xFF) - 128) shl 8 }
            builder.add((sum / channelCount).coerceToShort())
        }
    }

    private fun resampleToTarget(samples: ShortArray, sourceRate: Int): ShortArray {
        if (sourceRate == MethodACodec.SAMPLE_RATE) return samples
        require(sourceRate > 0) { "Invalid source sample rate: $sourceRate" }

        val outputLength = (samples.size.toDouble() * MethodACodec.SAMPLE_RATE / sourceRate)
            .roundToInt()
            .coerceAtLeast(1)
        val output = ShortArray(outputLength)
        val ratio = sourceRate.toDouble() / MethodACodec.SAMPLE_RATE.toDouble()

        for (index in output.indices) {
            val sourcePosition = index * ratio
            val base = sourcePosition.toInt().coerceIn(0, samples.lastIndex)
            val next = (base + 1).coerceAtMost(samples.lastIndex)
            val fraction = sourcePosition - base
            val interpolated = samples[base] + (samples[next] - samples[base]) * fraction
            output[index] = interpolated.roundToInt().coerceToShort()
        }

        return output
    }

    private fun Int.coerceToShort(): Short {
        return coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private const val TIMEOUT_US = 10_000L
}
