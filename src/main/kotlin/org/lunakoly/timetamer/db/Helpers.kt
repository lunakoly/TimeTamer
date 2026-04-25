package org.lunakoly.timetamer.db

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

fun userExists(userId: Long): Boolean = UserTable.selectAll()
    .where { UserTable.telegramUserId eq userId }
    .singleOrNull() != null

fun userTimezone(userId: Long): String? = UserTable.select(UserTable.timezone)
    .where { UserTable.telegramUserId eq userId }
    .singleOrNull()
    ?.get(UserTable.timezone)

fun chatEntryExists(chatId: Long, userId: Long): Boolean = ChatMemberTable.selectAll()
    .where { (ChatMemberTable.chatId eq chatId) and (ChatMemberTable.notifiableMemberId eq userId) }
    .singleOrNull() != null
