package org.lunakoly.timetamer

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.chat.members.getChatMember
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onContentMessage
import dev.inmo.tgbotapi.extensions.utils.extensions.isLeftOrKicked
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.from
import dev.inmo.tgbotapi.extensions.utils.extensions.raw.text
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.ReplyParameters
import dev.inmo.tgbotapi.types.chat.PrivateChat
import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lunakoly.timetamer.db.ChatMemberTable
import org.lunakoly.timetamer.db.UserTable
import org.lunakoly.timetamer.env.parseEnvironment
import org.lunakoly.timetamer.parsing.CompositeTimerFinder
import org.lunakoly.timetamer.parsing.formatTime
import org.lunakoly.timetamer.parsing.toGlobal
import org.lunakoly.timetamer.parsing.toKotlinTimeZone

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

val String.isCommand: Boolean get() = trimStart().startsWith("/")

fun BehaviourContext.configurePrivateChats() {
    onCommand("start", initialFilter = { it.chat is PrivateChat }) {
        reply(
            it,
            "Hey! What's your timezone?\n" +
                    "Use the *TZ identifier* column from " +
                    "[this Wikipedia table](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones#List) " +
                    "as the reference",
            parseMode = MarkdownParseMode,
        )
    }

    onCommand("delete_me", initialFilter = { it.chat is PrivateChat }) {
        @OptIn(RiskFeature::class)
        val userId = it.from ?: return@onCommand

        val userExists = transaction {
            val userIdLong = userId.id.chatId.long
            val userExists = userExists(userIdLong)
            if (userExists) {
                UserTable.deleteWhere { UserTable.telegramUserId eq userIdLong }
            }
            userExists
        }

        val response = when (userExists) {
            true -> "Done! Send your timezone when you're ready!"
            false -> "I don't even know you anyway :)"
        }
        reply(it, response)
    }

    @OptIn(RiskFeature::class)
    onContentMessage(initialFilter = { it.chat is PrivateChat && it.text?.isCommand != true }) { message ->
        @OptIn(RiskFeature::class)
        val text = message.text ?: return@onContentMessage

        if (text.toKotlinTimeZone() == null) {
            reply(message, "Unknown timezone, try again. Example: Europe/Nicosia")
            return@onContentMessage
        }

        @OptIn(RiskFeature::class)
        val userId = message.from ?: return@onContentMessage

        transaction {
            UserTable.upsert(UserTable.telegramUserId) {
                it[UserTable.telegramUserId] = userId.id.chatId.long
                it[UserTable.timezone] = text
            }
        }

        reply(message, "Done! Send another one to update or /delete_me to remove your data.")
    }
}

fun userExists(userId: Long): Boolean = UserTable.selectAll()
     .where { UserTable.telegramUserId eq userId }
     .singleOrNull() != null

fun chatEntryExists(chatId: Long, userId: Long): Boolean = ChatMemberTable.selectAll()
    .where { (ChatMemberTable.chatId eq chatId) and (ChatMemberTable.notifiableMemberId eq userId) }
    .singleOrNull() != null

enum class TranslateForMeRequestResult {
    SUCCESS,
    ALREADY_LISTENING,
    UNKNOWN_USER,
}

enum class StopTranslatingForMeRequestResult {
    SUCCESS,
    NOT_LISTENING,
    UNKNOWN_USER,
}

fun BehaviourContext.configurePublicChats() {
    onCommand("translate_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        @OptIn(RiskFeature::class)
        val userId = message.from ?: return@onCommand
        val chatId = message.chat.id

        val result = transaction {
            val relatedChatId = chatId.chatId.long
            val notifiableUserId = userId.id.chatId.long

            val userExists = userExists(notifiableUserId)
            val chatEntryExists = chatEntryExists(relatedChatId, notifiableUserId)

            when {
                chatEntryExists -> return@transaction TranslateForMeRequestResult.ALREADY_LISTENING
                !userExists -> return@transaction TranslateForMeRequestResult.UNKNOWN_USER
            }

            ChatMemberTable.insert {
                it[ChatMemberTable.chatId] = chatId.chatId.long
                it[ChatMemberTable.notifiableMemberId] = notifiableUserId
            }

            TranslateForMeRequestResult.SUCCESS
        }

        val response = when (result) {
            TranslateForMeRequestResult.SUCCESS -> "Got it!"
            TranslateForMeRequestResult.ALREADY_LISTENING -> "Yep, you're already subscribed :)"
            TranslateForMeRequestResult.UNKNOWN_USER -> "First, send me your timezone in DM, then repeat the request :)"
        }
        reply(message, response)
    }

    onCommand("stop_translating_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        @OptIn(RiskFeature::class)
        val userId = message.from ?: return@onCommand
        val chatId = message.chat.id

        val result = transaction {
            val relatedChatId = chatId.chatId.long
            val notifiableUserId = userId.id.chatId.long

            val userExists = userExists(notifiableUserId)
            val chatEntryExists = chatEntryExists(relatedChatId, notifiableUserId)

            when {
                !userExists -> return@transaction StopTranslatingForMeRequestResult.UNKNOWN_USER
                !chatEntryExists -> return@transaction StopTranslatingForMeRequestResult.NOT_LISTENING
            }

            ChatMemberTable.deleteWhere {
                (ChatMemberTable.chatId eq relatedChatId) and (ChatMemberTable.notifiableMemberId eq notifiableUserId)
            }

            StopTranslatingForMeRequestResult.SUCCESS
        }

        val response = when (result) {
            StopTranslatingForMeRequestResult.SUCCESS -> "Done!"
            StopTranslatingForMeRequestResult.UNKNOWN_USER -> "I don't even know you :)"
            StopTranslatingForMeRequestResult.NOT_LISTENING -> "You aren't subscribed to anything :)"
        }
        reply(message, response)
    }

    @OptIn(RiskFeature::class)
    onContentMessage(initialFilter = { it.chat !is PrivateChat && it.text?.isCommand != true }) { message ->
        @OptIn(RiskFeature::class)
        val text = message.text ?: return@onContentMessage
        @OptIn(RiskFeature::class)
        val sender = message.from ?: return@onContentMessage

        val senderTimeZone = transaction {
            UserTable
                .select(UserTable.timezone)
                .where { UserTable.telegramUserId eq sender.id.chatId.long }
                .singleOrNull()
                ?.get(UserTable.timezone)
        }
            ?.toKotlinTimeZone()
            ?: return@onContentMessage

        val chatId = message.chat.id
        val participantTimeZones = transaction {
            ChatMemberTable
                .join(
                    otherTable = UserTable,
                    joinType = JoinType.INNER,
                    onColumn = ChatMemberTable.notifiableMemberId,
                    otherColumn = UserTable.telegramUserId,
                )
                .select(ChatMemberTable.notifiableMemberId, UserTable.timezone)
                .where { ChatMemberTable.chatId eq chatId.chatId.long }
                .map { it[ChatMemberTable.notifiableMemberId] to it[UserTable.timezone] }
        }
            .filter { (userIdLong, _) ->
                val userId = ChatId(RawChatId(userIdLong))
                !getChatMember(chatId, userId).isLeftOrKicked
            }
            .mapNotNullTo(mutableSetOf()) { (_, timezone) -> timezone.toKotlinTimeZone() }

        CompositeTimerFinder.WITH_ALL_LANGUAGES.findTime(text).forEach { (time, _, match) ->
            val rendered = (participantTimeZones + senderTimeZone).joinToString("\n") {
                val senderTime = time.toGlobal(senderTimeZone).toInstant(senderTimeZone)
                val participantTime = senderTime.toLocalDateTime(it)
                "${it.id}: ${participantTime.time.formatTime(it)}"
            }
            quote(message, rendered, match.value)
        }
    }
}

suspend fun main() {
    val environment = parseEnvironment()
    val bot = telegramBot(environment.BOT_TOKEN)

    Database.connect(
        url = environment.DB_URL,
        user = environment.DB_USER,
        password = environment.DB_PASSWORD,
    )

    transaction {
        SchemaUtils.create(UserTable)
        SchemaUtils.create(ChatMemberTable)
    }

    bot.buildBehaviourWithLongPolling {
        configurePrivateChats()
        configurePublicChats()
    }.join()
}
