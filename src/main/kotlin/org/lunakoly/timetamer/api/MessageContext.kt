package org.lunakoly.timetamer.api

import dev.inmo.tgbotapi.types.message.ParseMode

abstract class MessageContext {
    abstract val senderId: Long
    abstract val chatId: Long

    abstract suspend fun reply(text: String, parseMode: ParseMode? = null, quote: String? = null)
    abstract suspend fun isChatMember(userId: Long): Boolean
}
