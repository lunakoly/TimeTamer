package org.lunakoly.timetamer.test

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lunakoly.timetamer.api.BotApi
import org.lunakoly.timetamer.db.userExists
import org.lunakoly.timetamer.db.userTimezone
import org.lunakoly.timetamer.test.api.TestChat
import org.lunakoly.timetamer.test.api.TestMessageContext
import org.lunakoly.timetamer.test.api.addUser
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestChats {
    private fun prepareTempDb(): Database {
        val tempDir = createTempDirectory().toFile()
        val tempDbFile = tempDir.resolve("temp-time-tamer.db")

        return Database.connect(
            url = "jdbc:sqlite:${tempDbFile.absolutePath}",
            user = "dummy",
            password = "none",
        )
    }

    private inline fun testBot(crossinline block: suspend (BotApi) -> Unit) {
        runBlocking { block(BotApi(prepareTempDb())) }
    }

    private inline fun testSingleUser(crossinline block: suspend (BotApi, TestMessageContext) -> Unit) {
        val privateChat = TestChat(100L)
        val user = privateChat.addUser(1L)

        testBot { bot -> block(bot, user) }
    }

    @Test
    fun testDeleteMe() = testSingleUser { bot, user ->
        bot.start(user)
        bot.onPrivateChatMessage("Europe/Nicosia", user)

        transaction(bot.database) {
            assertTrue(userExists(user.senderId))
        }

        bot.deleteMe(user)

        transaction(bot.database) {
            assertFalse(userExists(user.senderId))
        }
    }

    @Test
    fun testTimeZoneSetting() = testSingleUser { bot, user ->
        bot.onPrivateChatMessage("Ha-ha", user)

        transaction(bot.database) {
            assertFalse(userExists(user.senderId))
        }

        bot.onPrivateChatMessage("Europe/Nicosia", user)

        transaction(bot.database) {
            val timeZone = userTimezone(user.senderId)
            assertEquals("Europe/Nicosia", timeZone)
        }

        bot.onPrivateChatMessage("Ha-ha", user)

        transaction(bot.database) {
            val timeZone = userTimezone(user.senderId)
            assertEquals("Europe/Nicosia", timeZone)
        }

        bot.onPrivateChatMessage("Asia/Tokyo", user)

        transaction(bot.database) {
            val timeZone = userTimezone(user.senderId)
            assertEquals("Asia/Tokyo", timeZone)
        }
    }

    @Test
    fun testSubscription() {
        val groupChat = TestChat(100L)
        val user1GroupContext = groupChat.addUser(1L)
        val user2GroupContext = groupChat.addUser(2L)
        val user1PrivateContext = TestChat(101L).addUser(user1GroupContext.senderId)
        val user2PrivateContext = TestChat(102L).addUser(user2GroupContext.senderId)

        testBot { bot ->
            bot.onPrivateChatMessage("Europe/Nicosia", user1PrivateContext)
            bot.onPrivateChatMessage("Asia/Tokyo", user2PrivateContext)

            bot.onGroupChatMessage("at 4", user1GroupContext)
            assertEquals("at 4", groupChat.messages.last().quote)
            assertEquals("Europe/Nicosia: 4:00 pm", groupChat.messages.last().text)

            bot.translateTimeForMe(user2GroupContext)

            bot.onGroupChatMessage("at 10:32 am", user1GroupContext)
            assertEquals("at 10:32 am", groupChat.messages.last().quote)
            assertEquals("Asia/Tokyo: 4:32 pm\nEurope/Nicosia: 10:32 am", groupChat.messages.last().text)

            bot.stopTranslatingTimeForMe(user2GroupContext)

            bot.onGroupChatMessage("at 3:00", user1GroupContext)
            assertEquals("at 3:00", groupChat.messages.last().quote)
            assertEquals("Europe/Nicosia: 3:00 pm", groupChat.messages.last().text)

            bot.translateTimeForMe(user1GroupContext)
            bot.translateTimeForMe(user2GroupContext)

            bot.onGroupChatMessage("at 1:00", user1GroupContext)
            assertEquals("at 1:00", groupChat.messages.last().quote)
            assertEquals("Europe/Nicosia: 1:00 pm\nAsia/Tokyo: 7:00 pm", groupChat.messages.last().text)

            bot.onGroupChatMessage("at 1:00", user2GroupContext)
            assertEquals("at 1:00", groupChat.messages.last().quote)
            assertEquals("Europe/Nicosia: 7:00 am\nAsia/Tokyo: 1:00 pm", groupChat.messages.last().text)
        }
    }

    @Test
    fun testOperationsOnMissingUser() = testSingleUser { bot, user ->
        val groupChat = TestChat(100L)
        val userPrivate = groupChat.addUser(user.senderId)

        bot.deleteMe(userPrivate)

        bot.translateTimeForMe(user)
        bot.stopTranslatingTimeForMe(user)
    }
}
