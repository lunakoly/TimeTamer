package org.lunakoly.timetamer.test.api

import dev.inmo.tgbotapi.types.message.ParseMode
import org.lunakoly.timetamer.api.MessageContext

data class TestMessageContext(
    override val senderId: Long,
    val chat: TestChat,
    override val authorId: Long = senderId,
) : MessageContext() {
    override val chatId: Long get() = chat.chatId

    override suspend fun reply(text: String, parseMode: ParseMode?, quote: String?) {
        chat.messages += TestChat.MessageRecord(text, quote)
    }

    override suspend fun isChatMember(userId: Long): Boolean = userId in chat.members
}

fun TestMessageContext.asForwardedFrom(authorId: Long) = copy(authorId = authorId)
