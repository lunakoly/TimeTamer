package org.lunakoly.timetamer

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.ReplyParameters
import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import io.github.cdimascio.dotenv.dotenv
import org.lunakoly.timetamer.env.parseEnvironment
import org.lunakoly.timetamer.parsing.CompositeTimerFinder

context(bot: TelegramBot)
suspend fun quote(message: AccessibleMessage, text: String, quote: String) {
    val request = SendTextMessage(
        chatId = message.chat.id,
        text = text,
        replyParameters = ReplyParameters(
            chatIdentifier = message.chat.id,
            messageId = message.messageId,
            quote = quote,
            quoteParseMode = MarkdownParseMode,
        )
    )

    bot.execute(request)
}

suspend fun main() {
    val environment = dotenv().parseEnvironment()
    val bot = telegramBot(environment.BOT_TOKEN)

    bot.buildBehaviourWithLongPolling {
        onContentMessage { message ->
            val text = ((message as? ContentMessage<MessageContent>)?.content as? TextContent)?.text
                ?: return@onContentMessage

            CompositeTimerFinder.WITH_ALL_LANGUAGES.findTime(text).forEach { (_, reply, match) ->
                quote(message, reply, match.value)
            }
        }
    }.join()
}
