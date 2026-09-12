package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité de session de conversation active par client WhatsApp (remoteJid).
 * Assure la continuité d'agent et de catégorie entre les messages successifs
 * d'une même commande ou discussion, évitant les ruptures de persona.
 */
@Entity(tableName = "active_conversation_sessions")
data class ActiveConversationSessionEntity(
    @PrimaryKey val remoteJid: String, // ex: "33712345678@s.whatsapp.net"
    val agentId: String,
    val categoryId: String? = null,
    val orderId: String? = null,
    val lastActivityTimestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
) {
    fun isSessionActive(now: Long = System.currentTimeMillis(), timeoutMs: Long = 45 * 60 * 1000L): Boolean {
        return !isCompleted && (now - lastActivityTimestamp <= timeoutMs)
    }
}
