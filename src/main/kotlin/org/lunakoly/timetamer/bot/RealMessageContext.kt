package org.lunakoly.timetamer.bot

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.chat.members.getChatMember
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.utils.extensions.isLeftOrKicked
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.forward_from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.IdChatIdentifier
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.chat.User
import dev.inmo.tgbotapi.types.message.ParseMode
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.utils.RiskFeature
import org.lunakoly.timetamer.api.MessageContext
import org.lunakoly.timetamer.quote

class RealMessageContext(
    val message: CommonMessage<*>,
    val sender: User,
    val author: User,
    val telegramBot: TelegramBot,
) : MessageContext() {
    val chat: IdChatIdentifier get() = message.chat.id

    override val senderId: Long get() = sender.id.chatId.long
    override val authorId: Long get() = author.id.chatId.long
    override val chatId: Long get() = chat.chatId.long

    override suspend fun reply(
        text: String,
        parseMode: ParseMode?,
        quote: String?,
    ) {
        with(telegramBot) {
            when (quote) {
                null -> reply(message, text, parseMode = parseMode)
                else -> quote(message, text, quote)
            }
        }
    }

    override suspend fun isChatMember(userId: Long): Boolean {
        val wrappedUserId = ChatId(RawChatId(userId))
        return !telegramBot.getChatMember(chat, wrappedUserId).isLeftOrKicked
    }
}

fun BehaviourContext.createContextFor(message: CommonMessage<*>): RealMessageContext? {
    @OptIn(RiskFeature::class)
    val sender = message.from ?: return null

    @OptIn(RiskFeature::class)
    return RealMessageContext(
        message = message,
        sender = sender,
        author = message.forward_from ?: sender,
        telegramBot = this,
    )
}
