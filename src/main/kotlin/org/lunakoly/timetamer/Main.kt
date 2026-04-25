package org.lunakoly.timetamer

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.ReplyParameters
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.utils.RiskFeature
import org.jetbrains.exposed.v1.jdbc.Database
import org.lunakoly.timetamer.api.BotApi
import org.lunakoly.timetamer.bot.createContextFor
import org.lunakoly.timetamer.env.parseEnvironment
import org.lunakoly.timetamer.util.isCommand

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

fun BehaviourContext.configurePrivateChats(botApi: BotApi) {
    onCommand("start", initialFilter = { it.chat is PrivateChat }) {
        botApi.start(createContextFor(it) ?: return@onCommand)
    }

    onCommand("delete_me", initialFilter = { it.chat is PrivateChat }) {
        botApi.deleteMe(createContextFor(it) ?: return@onCommand)
    }

    @OptIn(RiskFeature::class)
    onContentMessage(initialFilter = { it.chat is PrivateChat && it.text?.isCommand != true }) { message ->
        @OptIn(RiskFeature::class)
        val text = message.text ?: return@onContentMessage
        botApi.onPrivateChatMessage(text, createContextFor(message) ?: return@onContentMessage)
    }
}

fun BehaviourContext.configurePublicChats(botApi: BotApi) {
    onCommand("translate_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        botApi.deleteMe(createContextFor(message) ?: return@onCommand)
    }

    onCommand("stop_translating_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        botApi.deleteMe(createContextFor(message) ?: return@onCommand)
    }

    @OptIn(RiskFeature::class)
    onContentMessage(initialFilter = { it.chat !is PrivateChat && it.text?.isCommand != true }) { message ->
        @OptIn(RiskFeature::class)
        val text = message.text ?: return@onContentMessage
        botApi.onGroupChatMessage(text, createContextFor(message) ?: return@onContentMessage)
    }
}

suspend fun main() {
    val environment = parseEnvironment()
    val bot = telegramBot(environment.BOT_TOKEN)

    val db = Database.connect(
        url = environment.DB_URL,
        user = environment.DB_USER,
        password = environment.DB_PASSWORD,
    )
    val botApi = BotApi(db)

    bot.buildBehaviourWithLongPolling {
        configurePrivateChats(botApi)
        configurePublicChats(botApi)
    }.join()
}
