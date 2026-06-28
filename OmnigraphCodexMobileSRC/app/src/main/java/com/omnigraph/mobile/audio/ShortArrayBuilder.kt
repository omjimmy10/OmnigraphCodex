package com.omnigraph.mobile.audio

class ShortArrayBuilder(initialCapacity: Int = 16_384) {
    private var values = ShortArray(initialCapacity)
    var size: Int = 0
        private set

    fun add(value: Short) {
        ensureCapacity(size + 1)
        values[size] = value
        size += 1
    }

    fun toShortArray(): ShortArray = values.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= values.size) return
        var nextSize = values.size * 2
        while (nextSize < required) nextSize *= 2
        values = values.copyOf(nextSize)
    }
}
