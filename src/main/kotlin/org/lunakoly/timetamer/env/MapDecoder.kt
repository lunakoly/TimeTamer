package org.lunakoly.timetamer.env

import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@OptIn(ExperimentalSerializationApi::class)
class MapDecoder(private val map: Map<String, String>) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private lateinit var descriptor: SerialDescriptor
    private var index = 0

    override fun beginStructure(descriptor: SerialDescriptor) = this.also { this.descriptor = descriptor }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = when {
        index < descriptor.elementsCount -> index++
        else -> CompositeDecoder.DECODE_DONE
    }

    private val currentName: String get() = descriptor.getElementName(index - 1)
    private val currentValue: String? get() = map[descriptor.getElementName(index - 1)]

    override fun decodeString(): String =
        currentValue ?: throw SerializationException("Missing value for $currentName")

    override fun decodeInt(): Int =
        currentValue?.toInt()
            ?: throw SerializationException("Missing or invalid Int for $currentName")

    override fun decodeBoolean(): Boolean =
        currentValue?.toBooleanStrictOrNull()
            ?: throw SerializationException("Missing or invalid Boolean for $currentName")
}

inline fun <reified T> decodeFromMap(map: Map<String, String>): T =
    serializer<T>().deserialize(MapDecoder(map))
