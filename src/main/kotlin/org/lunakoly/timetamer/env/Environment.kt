@file:Suppress("PropertyName")

package org.lunakoly.timetamer.env

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.Serializable
import org.lunakoly.timetamer.util.wrapExceptions
import java.io.File

@Serializable
data class Environment(
    val BOT_TOKEN: String,
    val DB_URL: String,
    val DB_USER: String,
    val DB_PASSWORD: String,
)

fun parseEnvironment(): Environment = when {
    File(".env").exists() -> dotenv().parseEnvironment()
    else -> decodeFromSystemProperties<Environment>()
}

fun Dotenv.parseEnvironment(): Environment =
    wrapExceptions("Error when parsing `.env`") {
        decodeFromMap<Environment>(entries().associate { it.key to it.value })
    }
