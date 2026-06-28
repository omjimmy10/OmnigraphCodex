package com.omnigraph.mobile.codec

data class RgbImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {
    init {
        require(width > 0) { "Image width must be positive." }
        require(height > 0) { "Image height must be positive." }
        require(pixels.size == width * height * 3) {
            "RGB pixel buffer must contain width * height * 3 bytes."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RgbImage) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}
