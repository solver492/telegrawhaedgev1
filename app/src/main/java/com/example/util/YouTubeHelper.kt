package com.example.util

import java.util.regex.Pattern

/**
 * Utilitaire pour la détection, l'extraction et la conversion des liens YouTube
 * au format Embed iFrame (supporte watch?v=, youtu.be, shorts, embed, etc.)
 */
object YouTubeHelper {

    /**
     * Détecte si l'URL fournie correspond à une vidéo YouTube
     */
    fun isYouTubeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase().trim()
        val isYtDomain = lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("youtube-nocookie.com")
        return isYtDomain && extractVideoId(url) != null
    }

    /**
     * Extrait l'identifiant unique (11 caractères) d'une vidéo YouTube à partir de divers formats d'URL :
     * - https://www.youtube.com/watch?v=dQw4w9WgXcQ
     * - https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=10s
     * - https://youtu.be/dQw4w9WgXcQ?si=abcdef
     * - https://www.youtube.com/shorts/dQw4w9WgXcQ
     * - https://www.youtube.com/embed/dQw4w9WgXcQ
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()

        // 1. Format youtu.be/{videoId}
        val youtuBeRegex = Regex("""youtu\.be/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        youtuBeRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        // 2. Format youtube.com/shorts/{videoId}
        val shortsRegex = Regex("""youtube\.com/shorts/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        shortsRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        // 3. Format youtube.com/embed/{videoId}
        val embedRegex = Regex("""youtube(?:-nocookie)?\.com/embed/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        embedRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        // 4. Format standard youtube.com/watch?v={videoId}
        val watchRegex = Regex("""[?&]v=([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        watchRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        // 5. Format youtube.com/v/{videoId}
        val vRegex = Regex("""youtube\.com/v/([a-zA-Z0-9_-]{11})""", RegexOption.IGNORE_CASE)
        vRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }

        // 6. Extraction générale de l'ID à 11 caractères si le domaine est bien YouTube
        if (trimmed.contains("youtube", ignoreCase = true) || trimmed.contains("youtu.be", ignoreCase = true)) {
            val generalPattern = Regex("""(?<![a-zA-Z0-9_-])([a-zA-Z0-9_-]{11})(?![a-zA-Z0-9_-])""")
            val matches = generalPattern.findAll(trimmed).toList()
            for (match in matches) {
                val candidate = match.groupValues.getOrNull(1)
                if (candidate != null && candidate != "watch_popup" && !candidate.contains("channel") && !candidate.contains("playlist")) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Convertit n'importe quelle URL YouTube en URL d'intégration iFrame :
     * https://www.youtube.com/embed/{videoId}
     */
    fun toEmbedUrl(url: String?, autoPlay: Boolean = true): String? {
        val videoId = extractVideoId(url) ?: return null
        val autoPlayParam = if (autoPlay) "autoplay=1&playsinline=1" else "autoplay=0&playsinline=1"
        return "https://www.youtube.com/embed/$videoId?$autoPlayParam&rel=0&modestbranding=1&enablejsapi=1"
    }

    /**
     * Renvoie l'URL de la miniature haute qualité YouTube
     */
    fun getThumbnailUrl(url: String?): String? {
        val videoId = extractVideoId(url) ?: return null
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    /**
     * Génère le code HTML complet intégrant l'iframe responsive pour WebView
     */
    fun buildIFrameHtml(videoId: String, autoPlay: Boolean = true): String {
        val autoPlayVal = if (autoPlay) "1" else "0"
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: 100%;
                        height: 100%;
                        background-color: #000000;
                        overflow: hidden;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .video-wrapper {
                        position: relative;
                        width: 100%;
                        height: 100%;
                    }
                    iframe {
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        border: none;
                    }
                </style>
            </head>
            <body>
                <div class="video-wrapper">
                    <iframe
                        src="https://www.youtube.com/embed/$videoId?autoplay=$autoPlayVal&playsinline=1&rel=0&modestbranding=1&enablejsapi=1"
                        frameborder="0"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen>
                    </iframe>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
