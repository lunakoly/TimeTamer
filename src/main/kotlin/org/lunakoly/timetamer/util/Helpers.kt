package org.lunakoly.timetamer.util

inline fun <T> wrapExceptions(message: String, block: () -> T): T =
    try {
        block()
    } catch (e: Exception) {
        throw Exception(message, e)
    }

val String.isCommand: Boolean get() = trimStart().startsWith("/")
