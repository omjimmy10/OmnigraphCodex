package com.omnigraph.mobile.util

fun Long.toTimeText(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun Int.toTimeText(): String = toLong().toTimeText()
