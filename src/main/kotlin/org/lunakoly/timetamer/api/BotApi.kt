package org.lunakoly.timetamer.api

import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.lunakoly.timetamer.db.ChatMemberTable
import org.lunakoly.timetamer.db.UserTable
import org.lunakoly.timetamer.db.chatEntryExists
import org.lunakoly.timetamer.db.userExists
import org.lunakoly.timetamer.parsing.CompositeTimerFinder
import org.lunakoly.timetamer.parsing.formatTime
import org.lunakoly.timetamer.parsing.toGlobal
import org.lunakoly.timetamer.parsing.toKotlinTimeZone

class BotApi(val database: Database) {
    init {
        transaction(database) {
            SchemaUtils.create(UserTable)
            SchemaUtils.create(ChatMemberTable)
        }
    }

    suspend fun start(context: MessageContext) {
        context.reply(
            "Hey! What's your timezone?\n" +
                    "Use the *TZ identifier* column from " +
                    "[this Wikipedia table](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones#List) " +
                    "as the reference",
            parseMode = MarkdownParseMode,
        )
    }

    suspend fun deleteMe(context: MessageContext) {
        val userExists = transaction(database) {
            val userExists = userExists(context.senderId)
            if (userExists) {
                UserTable.deleteWhere { UserTable.telegramUserId eq context.senderId }
            }
            userExists
        }

        val response = when (userExists) {
            true -> "Done! Send your timezone when you're ready!"
            false -> "I don't even know you anyway :)"
        }
        context.reply(response)
    }

    suspend fun onPrivateChatMessage(text: String, context: MessageContext) {
        if (text.toKotlinTimeZone() == null) {
            context.reply("Unknown timezone, try again. Example: Europe/Nicosia")
            return
        }

        transaction(database) {
            UserTable.upsert(UserTable.telegramUserId) {
                it[UserTable.telegramUserId] = context.senderId
                it[UserTable.timezone] = text
            }
        }

        context.reply("Done! Send another one to update or /delete_me to remove your data.")
    }

    enum class TranslateForMeRequestResult {
        SUCCESS,
        ALREADY_LISTENING,
        UNKNOWN_USER,
    }

    suspend fun translateTimeForMe(context: MessageContext) {
        val result = transaction(database) {
            val relatedChatId = context.chatId
            val notifiableUserId = context.senderId

            val userExists = userExists(notifiableUserId)
            val chatEntryExists = chatEntryExists(relatedChatId, notifiableUserId)

            when {
                chatEntryExists -> return@transaction TranslateForMeRequestResult.ALREADY_LISTENING
                !userExists -> return@transaction TranslateForMeRequestResult.UNKNOWN_USER
            }

            ChatMemberTable.insert {
                it[ChatMemberTable.chatId] = relatedChatId
                it[ChatMemberTable.notifiableMemberId] = notifiableUserId
            }

            TranslateForMeRequestResult.SUCCESS
        }

        val response = when (result) {
            TranslateForMeRequestResult.SUCCESS -> "Got it!"
            TranslateForMeRequestResult.ALREADY_LISTENING -> "Yep, you're already subscribed :)"
            TranslateForMeRequestResult.UNKNOWN_USER -> "First, send me your timezone in DM, then repeat the request :)"
        }
        context.reply(response)
    }

    enum class StopTranslatingForMeRequestResult {
        SUCCESS,
        NOT_LISTENING,
        UNKNOWN_USER,
    }

    suspend fun stopTranslatingTimeForMe(context: MessageContext) {
        val result = transaction(database) {
            val relatedChatId = context.chatId
            val notifiableUserId = context.senderId

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
        context.reply(response)
    }

    suspend fun onGroupChatMessage(text: String, context: MessageContext) {
        val senderTimeZone = transaction(database) {
            UserTable
                .select(UserTable.timezone)
                .where { UserTable.telegramUserId eq context.senderId }
                .singleOrNull()
                ?.get(UserTable.timezone)
        }
            ?.toKotlinTimeZone()
            ?: return

        val participantTimeZones = transaction(database) {
            ChatMemberTable
                .join(
                    otherTable = UserTable,
                    joinType = JoinType.INNER,
                    onColumn = ChatMemberTable.notifiableMemberId,
                    otherColumn = UserTable.telegramUserId,
                )
                .select(ChatMemberTable.notifiableMemberId, UserTable.timezone)
                .where { ChatMemberTable.chatId eq context.chatId }
                .map { it[ChatMemberTable.notifiableMemberId] to it[UserTable.timezone] }
        }
            .filter { (userIdLong, _) ->
                context.isChatMember(userIdLong)
            }
            .mapNotNullTo(mutableSetOf()) { (_, timezone) -> timezone.toKotlinTimeZone() }

        CompositeTimerFinder.WITH_ALL_LANGUAGES.findTime(text).forEach { (time, _, match) ->
            val rendered = (participantTimeZones + senderTimeZone).joinToString("\n") {
                val senderTime = time.toGlobal(senderTimeZone).toInstant(senderTimeZone)
                val participantTime = senderTime.toLocalDateTime(it)
                "${it.id}: ${participantTime.time.formatTime(it)}"
            }
            context.reply(rendered, quote = match.value)
        }
    }
}
