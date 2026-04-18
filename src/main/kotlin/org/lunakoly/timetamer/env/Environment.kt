@file:Suppress("PropertyName")

package org.lunakoly.timetamer.env

import io.github.cdimascio.dotenv.Dotenv
import kotlinx.serialization.Serializable
import org.lunakoly.timetamer.util.wrapExceptions

@Serializable
data class Environment(
    val BOT_TOKEN: String,
    val DB_URL: String,
    val DB_USER: String,
    val DB_PASSWORD: String,
)

fun Dotenv.parseEnvironment(): Environment =
    wrapExceptions("Error when parsing `.env`") {
        decodeFromMap<Environment>(entries().associate { it.key to it.value })
    }
