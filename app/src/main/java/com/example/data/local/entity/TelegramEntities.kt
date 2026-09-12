package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_accounts")
data class TelegramAccountEntity(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val apiId: String = "",
    val apiHash: String = "",
    val status: String = "DISCONNECTED", // DISCONNECTED, CODE_SENT, CONNECTING, CONNECTED, ERROR
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val userId: Long = 0L,
    val bridgePort: Int = 8082,
    val isMonitored: Boolean = true,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

@Entity(tableName = "telegram_channels")
data class TelegramChannelEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val channelId: Long,
    val title: String,
    val username: String = "",
    val isChannel: Boolean = true,
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val isMonitored: Boolean = true,
    val unreadCount: Int = 0,
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "telegram_messages")
data class TelegramMessageEntity(
    @PrimaryKey val id: String, // e.g. "${channelId}_${messageId}"
    val channelId: Long,
    val channelTitle: String,
    val channelUsername: String = "",
    val messageId: Long,
    val senderId: Long = 0L,
    val senderName: String = "",
    val text: String = "",
    val mediaType: String = "none", // none, photo, document, album, video
    val mediaUrl: String? = null,
    val localMediaPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false, // True once converted or analyzed into Product
    val rawJson: String? = null
) {
    fun getMediaUrls(): List<String> {
        return getMediaItems().mapNotNull { it.url ?: it.localPath }
    }

    private fun resolveMediaItem(u: String?, p: String?): Pair<String?, String?> {
        val path = p?.trim()
        val url = u?.trim()

        if (!path.isNullOrBlank()) {
            val file = java.io.File(path)
            if (file.exists() && file.canRead() && file.length() > 0) {
                // Fichier accessible localement par le processus Android
                return Pair(url ?: "file://$path", path)
            }

            // Si le fichier est inaccessible (sandbox Termux ou non lisible), basculer sur la passerelle HTTP locale
            val resolvedUrl = if (path.contains("telegram_media/")) {
                val parts = path.substringAfter("telegram_media/").trimStart('/').split("/")
                if (parts.size >= 3) {
                    val ch = parts[0]
                    val mid = parts[1]
                    val fn = parts.drop(2).joinToString("/")
                    "http://127.0.0.1:8088/media/$ch/$mid/$fn"
                } else {
                    val fileName = file.name
                    "http://127.0.0.1:8088/media/$channelId/$messageId/$fileName"
                }
            } else if (file.name.isNotBlank()) {
                "http://127.0.0.1:8088/media/$channelId/$messageId/${file.name}"
            } else {
                url
            }
            return Pair(resolvedUrl, null)
        }

        return Pair(url, null)
    }

    fun getMediaItems(): List<ParsedMediaItem> {
        val items = mutableListOf<ParsedMediaItem>()
        if (!rawJson.isNullOrBlank()) {
            try {
                val obj = org.json.JSONObject(rawJson)
                val urlsArr = obj.optJSONArray("media_urls")
                val pathsArr = obj.optJSONArray("local_media_paths")
                val len = maxOf(urlsArr?.length() ?: 0, pathsArr?.length() ?: 0)
                for (i in 0 until len) {
                    val u = if (urlsArr != null && i < urlsArr.length()) urlsArr.getString(i) else null
                    val p = if (pathsArr != null && i < pathsArr.length()) pathsArr.getString(i) else null
                    val (resolvedUrl, resolvedPath) = resolveMediaItem(u, p)
                    val isVid = (resolvedUrl?.let { isVideoUrlOrPath(it) } == true) ||
                            (resolvedPath?.let { isVideoUrlOrPath(it) } == true) ||
                            (mediaType == "video" && len == 1)
                    if (!resolvedUrl.isNullOrBlank() || !resolvedPath.isNullOrBlank()) {
                        items.add(ParsedMediaItem(url = resolvedUrl, localPath = resolvedPath, isVideo = isVid))
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        if (items.isEmpty()) {
            if (!mediaUrl.isNullOrBlank() || !localMediaPath.isNullOrBlank()) {
                val (resolvedUrl, resolvedPath) = resolveMediaItem(mediaUrl, localMediaPath)
                val isVid = (mediaType == "video") ||
                        (resolvedUrl?.let { isVideoUrlOrPath(it) } == true) ||
                        (resolvedPath?.let { isVideoUrlOrPath(it) } == true)
                items.add(ParsedMediaItem(url = resolvedUrl, localPath = resolvedPath, isVideo = isVid))
            }
        }

        // Collecte des URLs vidéo directement présentes dans le texte du message (YouTube, Vimeo, liens MP4/WEBM)
        if (!text.isNullOrBlank()) {
            val urlRegex = Regex("https?://[^\\s]+")
            urlRegex.findAll(text).forEach { match ->
                val extractedUrl = match.value.trimEnd('.', ',', ')', ']', ';', '!')
                if (isVideoUrlOrPath(extractedUrl)) {
                    if (items.none { it.url == extractedUrl }) {
                        items.add(ParsedMediaItem(url = extractedUrl, localPath = null, isVideo = true))
                    }
                }
            }
        }

        return items
    }

    private fun isVideoUrlOrPath(pathOrUrl: String): Boolean {
        return com.example.util.ProductMediaManager.isVideoUrlOrPath(pathOrUrl)
    }
}

data class ParsedMediaItem(
    val url: String? = null,
    val localPath: String? = null,
    val isVideo: Boolean = false
) {
    fun getDisplayModel(): Any? {
        // Si c'est un lien YouTube, renvoyer la miniature pour affichage d'image dans Coil
        if (!url.isNullOrBlank() && (url.contains("youtube.com") || url.contains("youtu.be"))) {
            val thumb = com.example.util.ProductMediaManager.getYouTubeThumbnailUrl(url)
            if (thumb != null) return thumb
        }
        if (!localPath.isNullOrBlank()) {
            val file = java.io.File(localPath)
            if (file.exists() && file.canRead() && file.length() > 0) {
                return file
            }
        }
        if (!url.isNullOrBlank()) {
            if (url.startsWith("http://") || url.startsWith("https://")) return url
            if (url.startsWith("content://")) return android.net.Uri.parse(url)
        }
        val target = url ?: localPath
        if (!target.isNullOrBlank()) {
            if (target.startsWith("content://")) return android.net.Uri.parse(target)
            if (target.startsWith("file://")) {
                val f = java.io.File(target.removePrefix("file://"))
                if (f.exists() && f.canRead() && f.length() > 0) return f
            }
            if (target.startsWith("http://") || target.startsWith("https://")) return target
            if (target.startsWith("/")) {
                val f = java.io.File(target)
                if (f.exists() && f.canRead() && f.length() > 0) return f
            }
            
            if (target.contains("telegram_media/")) {
                val parts = target.substringAfter("telegram_media/").trimStart('/').split("/")
                if (parts.size >= 3) {
                    val ch = parts[0]
                    val mid = parts[1]
                    val fn = parts.drop(2).joinToString("/")
                    return "http://127.0.0.1:8088/media/$ch/$mid/$fn"
                }
            }
            val f = java.io.File(target)
            if (f.exists() && f.canRead() && f.length() > 0) return f
            return target
        }
        return null
    }
}

@Entity(tableName = "telegram_logs")
data class TelegramLogEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, SUCCESS, INCOMING, ERROR, WARN
    val source: String = "Telethon", // Telethon, Bridge, Listener, Android
    val message: String
)
