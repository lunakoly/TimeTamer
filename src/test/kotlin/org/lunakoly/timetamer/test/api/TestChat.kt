package org.lunakoly.timetamer.test.api

class TestChat(val chatId: Long, vararg members: Long) {
    val members = members.toMutableList()
    val messages = mutableListOf<MessageRecord>()

    data class MessageRecord(val text: String, val quote: String? = null)
}

fun TestChat.addUser(userId: Long): TestMessageContext =
    TestMessageContext(userId, this).also { members += userId }
