package com.example.domain.commerce

import java.util.Locale

data class ParsedCustomerCoordinates(
    val name: String,
    val city: String,
    val address: String,
    val phone: String
)

object OrderCustomerInfoParser {

    private val MOROCCAN_CITIES = listOf(
        "casablanca", "rabat", "fès", "fes", "marrakech", "marrakesh", "tanger", "tangier", "agadir",
        "meknès", "meknes", "oujda", "kenitra", "kénitra", "tétouan", "tetouan", "safi", "temara",
        "témara", "mohammedia", "salé", "sale", "nador", "beni mellal", "el jadida", "taza", "settat",
        "berrechid", "khemisset", "taourirt", "taroudant", "dakhla", "laayoune", "essaouira", "guelmim",
        "larache", "ksar el kebir", "berkane", "ouarzazate", "taounate", "alhoceima", "al hoceima"
    )

    private val PHONE_REGEX = Regex("""(?:\+212|00212|0)?([5-7]\d{8})\b|\b(0[5-7]\d{8})\b|\b(\d{9,12})\b""")

    /**
     * Vérifie si le message contient un acquittement poli de clôture (ex: "merci", "ok", "parfait")
     */
    fun isFollowUpAcknowledgment(message: String): Boolean {
        val clean = message.trim().lowercase(Locale.getDefault())
        val tokens = clean.split(" ", "!", ".", ",", ";").filter { it.isNotBlank() }
        val ackWords = setOf(
            "merci", "d'accord", "daccord", "ok", "okay", "parfait", "c'est bon", "cest bon",
            "c'est noté", "cest note", "super", "bien reçu", "bien recu", "choukrane", "chokran",
            "merci beaucoup", "merci bien", "inshallah", "inchallah"
        )
        return tokens.size <= 5 && (ackWords.contains(clean) || tokens.any { ackWords.contains(it) })
    }

    /**
     * Détecte si un message client contient les coordonnées de livraison (nom, ville, adresse, téléphone)
     */
    fun isDeliveryCoordinatesMessage(message: String): Boolean {
        return extractCoordinates(message) != null
    }

    /**
     * Parse et extrait les coordonnées client à partir de formats divers :
     * 1. Délimité par des slashs : "Henry stateman / Tanger / Tanger rue Espagne / 6633995634"
     * 2. Délimité par des retours à la ligne
     * 3. Avec labels explicites : "Nom: ..., Ville: ..., Adresse: ..., Tél: ..."
     * 4. Texte libre contenant une ville marocaine et un numéro de téléphone
     */
    fun extractCoordinates(message: String): ParsedCustomerCoordinates? {
        val raw = message.trim()
        if (raw.length < 8) return null

        val lower = raw.lowercase(Locale.getDefault())
        // Storefront product order messages or product detail inquiries are not customer coordinates
        if ((lower.contains("je souhaite") && (lower.contains("commander") || lower.contains("finaliser"))) ||
            lower.contains("finaliser ma commande") ||
            (lower.contains("produit:") && (lower.contains("prix:") || lower.contains("catégorie:") || lower.contains("stock:"))) ||
            (lower.contains("produit :") && (lower.contains("prix :") || lower.contains("catégorie :") || lower.contains("stock :")))
        ) {
            return null
        }

        // 1. Format délimité par des slashs : Nom / Ville / Adresse / Tel
        // On exclut les URLs (http / https) qui contiennent des slashs
        val textWithoutUrls = raw.replace(Regex("""https?://\S+"""), "").trim()
        if (textWithoutUrls.contains("/")) {
            val parts = textWithoutUrls.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 3) {
                var phone = parts.firstNotNullOfOrNull { p -> PHONE_REGEX.find(p)?.value } ?: ""
                var city = parts.firstNotNullOfOrNull { p ->
                    findCityInText(p)
                } ?: ""
                val remainingParts = parts.filter { it != phone && it != city }.toMutableList()

                val name = if (remainingParts.isNotEmpty()) remainingParts.removeAt(0) else "Client WhatsApp"
                val address = if (remainingParts.isNotEmpty()) remainingParts.joinToString(", ") else if (city.isNotBlank()) city else "Adresse fournie sur WhatsApp"

                // Un format délimité par slashs de coordonnées doit comporter un numéro de tél OU une ville marocaine
                if (phone.isNotBlank() || city.isNotBlank()) {
                    if (phone.isBlank()) {
                        phone = PHONE_REGEX.find(textWithoutUrls)?.value ?: ""
                    }
                    return ParsedCustomerCoordinates(
                        name = cleanName(name),
                        city = if (city.isNotBlank()) formatCity(city) else "Maroc",
                        address = address,
                        phone = cleanPhone(phone)
                    )
                }
            }
        }

        // 2. Format avec labels explicites (Nom:, Ville:, etc.)
        val hasExplicitLabels = Regex("""(?i)(nom|ville|adresse|t[eé]l|phone|num[eé]ro)\s*[:=]""").containsMatchIn(raw)
        if (hasExplicitLabels) {
            val nameMatch = Regex("""(?i)(?:nom|client|nom complet)\s*[:=]\s*([^\n\r,]+)""").find(raw)?.groupValues?.get(1)?.trim()
            val cityMatch = Regex("""(?i)(?:ville|city)\s*[:=]\s*([^\n\r,]+)""").find(raw)?.groupValues?.get(1)?.trim()
            val addrMatch = Regex("""(?i)(?:adresse|quartier|rue)\s*[:=]\s*([^\n\r]+)""").find(raw)?.groupValues?.get(1)?.trim()
            val phoneMatch = Regex("""(?i)(?:t[eé]l|phone|gsm|num[eé]ro)\s*[:=]\s*([^\n\r,]+)""").find(raw)?.groupValues?.get(1)?.trim()
                ?: PHONE_REGEX.find(raw)?.value

            val detectedCity = cityMatch ?: findCityInText(raw) ?: "Maroc"
            val detectedPhone = phoneMatch ?: PHONE_REGEX.find(raw)?.value ?: ""
            val detectedName = nameMatch ?: "Client WhatsApp"
            val detectedAddr = addrMatch ?: raw

            if (detectedPhone.isNotBlank() || detectedCity != "Maroc") {
                return ParsedCustomerCoordinates(
                    name = cleanName(detectedName),
                    city = formatCity(detectedCity),
                    address = detectedAddr,
                    phone = cleanPhone(detectedPhone)
                )
            }
        }

        // 3. Format multi-lignes
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size in 2..6) {
            val phoneLine = lines.firstOrNull { PHONE_REGEX.matches(it.replace(" ", "")) }
                ?: lines.firstNotNullOfOrNull { PHONE_REGEX.find(it)?.value }
            val cityLine = lines.firstNotNullOfOrNull { findCityInText(it) }

            if (phoneLine != null || cityLine != null) {
                val nonPhoneLines = lines.filter { it != phoneLine }
                val detectedName = nonPhoneLines.firstOrNull { it != cityLine } ?: "Client WhatsApp"
                val addressLines = nonPhoneLines.filter { it != detectedName }
                val detectedAddress = if (addressLines.isNotEmpty()) addressLines.joinToString(", ") else cityLine ?: raw

                return ParsedCustomerCoordinates(
                    name = cleanName(detectedName),
                    city = formatCity(cityLine ?: "Maroc"),
                    address = detectedAddress,
                    phone = cleanPhone(phoneLine ?: "")
                )
            }
        }

        // 4. Détection heuristique générale (présence d'un téléphone marocain valide + texte)
        val generalPhone = PHONE_REGEX.find(raw)?.value
        val generalCity = findCityInText(raw)
        if (generalPhone != null && (generalCity != null || raw.length >= 15)) {
            val textWithoutPhone = raw.replace(generalPhone, "").trim()
            val tokens = textWithoutPhone.split(",", "-", "\n").map { it.trim() }.filter { it.isNotBlank() }
            val detectedName = tokens.firstOrNull() ?: "Client WhatsApp"
            val detectedAddr = if (tokens.size > 1) tokens.drop(1).joinToString(", ") else textWithoutPhone

            return ParsedCustomerCoordinates(
                name = cleanName(detectedName),
                city = formatCity(generalCity ?: "Maroc"),
                address = if (detectedAddr.isNotBlank()) detectedAddr else (generalCity ?: "Maroc"),
                phone = cleanPhone(generalPhone)
            )
        }

        return null
    }

    private fun findCityInText(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        return MOROCCAN_CITIES.firstOrNull { city ->
            Regex("""\b$city\b""").containsMatchIn(lower)
        }
    }

    private fun formatCity(city: String): String {
        return city.trim().split(" ", "-").joinToString(" ") { word ->
            word.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun cleanName(name: String): String {
        return name.replace(Regex("""(?i)(?:nom|client|pr[eé]nom)\s*[:=]"""), "")
            .trim()
            .ifBlank { "Client WhatsApp" }
    }

    private fun cleanPhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9+]"), "")
        return if (digits.startsWith("00212")) {
            "0" + digits.substring(5)
        } else if (digits.startsWith("+212")) {
            "0" + digits.substring(4)
        } else {
            digits
        }
    }
}
