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

fun BehaviourContext.configurePrivateChats() {
    onCommand("start", initialFilter = { it.chat is PrivateChat }) {
        reply(it, "Hey! What's your timezone?")
    }

    onCommand("delete-me", initialFilter = { it.chat is PrivateChat }) {
        @OptIn(RiskFeature::class)
        val userId = it.from ?: return@onCommand

        transaction {
            UserTable.deleteWhere { UserTable.id eq userId.id.chatId.long }
        }

        reply(it, "Done! Send your timezone when you're ready!")
    }

    onContentMessage(initialFilter = { it.chat is PrivateChat }) { message ->
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

        reply(message, "Done! Send another one to update or `/delete-me` to remove your data.")
    }
}

fun BehaviourContext.configurePublicChats() {
    onCommand("translate_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        @OptIn(RiskFeature::class)
        val userId = message.from ?: return@onCommand
        val chatId = message.chat.id

        transaction {
            ChatMemberTable.insert {
                it[ChatMemberTable.chatId] = chatId.chatId.long
                it[ChatMemberTable.notifiableMemberId] = userId.id.chatId.long
            }
        }

        reply(message, "Got it!")
    }

    onCommand("stop_translating_time_for_me", initialFilter = { it.chat !is PrivateChat }) { message ->
        @OptIn(RiskFeature::class)
        val userId = message.from ?: return@onCommand
        val chatId = message.chat.id

        transaction {
            ChatMemberTable.deleteWhere {
                (ChatMemberTable.chatId eq chatId.chatId.long) and (ChatMemberTable.notifiableMemberId eq userId.id.chatId.long)
            }
        }

        reply(message, "Done!")
    }

    onContentMessage(initialFilter = { it.chat !is PrivateChat }) { message ->
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
