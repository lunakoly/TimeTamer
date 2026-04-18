package org.lunakoly.timetamer.db

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object UserTable : LongIdTable("user") {
    val telegramUserId = long("telegram_user_id").uniqueIndex()
    val timezone = varchar("timezone", 64)
}

object ChatMemberTable : LongIdTable("chat_member") {
    val chatId = long("telegram_chat_id")
    val notifiableMemberId = long("notifiable_member_id").references(UserTable.telegramUserId)
}
