package com.example.domain.ai

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AgentEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.domain.engine.AiEdgeQuantizerEngine
import com.example.domain.engine.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class ConversationContext(
    val categoryId: String? = null,
    val previousMessages: List<String> = emptyList()
)

data class AgentResponse(
    val message: String,
    val agentName: String,
    val categoryId: String?,
    val categoryName: String,
    val matchedProducts: List<ProductEntity> = emptyList()
)

class CategoryAgentRouter(
    private val database: AppDatabase? = null
) {
    companion object {
        private const val TAG = "CategoryAgentRouter"

        // Dictionnaire de mots-clés bilingues (Français & Arabe/Darija) pour chaque catégorie
        private val CATEGORY_KEYWORDS = mapOf(
            "mode-vetements" to listOf(
                "vêtement", "vetement", "habit", "pantalon", "chemise", "robe", "tshirt", "t-shirt", "pull", "jean",
                "abayas", "abaya", "djellaba", "caftan", "costume", "blazer", "survêtement", "survetement",
                "taille", "coton", "lin", "قميص", "بنطلون", "فستان", "ملابس", "جلابة", "قفطان", "سروال", "تيشيرت"
            ),
            "electronique" to listOf(
                "téléphone", "telephone", "smartphone", "ordinateur", "laptop", "pc", "tablette", "ipad", "écouteur", "ecouteur",
                "casque", "chargeur", "cable", "câble", "powerbank", "airpods", "samsung", "iphone", "xiaomi", "redmi",
                "oppo", "realme", "huawei", "ram", "stockage", "écran", "هاتف", "حاسوب", "سماعات", "شاحن", "طابليط"
            ),
            "maison-cuisine" to listOf(
                "cuisine", "maison", "électroménager", "electromenager", "frigo", "réfrigérateur", "four", "micro-ondes",
                "blender", "mixeur", "aspirateur", "cafetière", "salon", "canapé", "table", "lit", "matelas", "drap",
                "décoration", "deco", "مطبخ", "منزل", "ثلاجة", "فرن", "خلاط", "صالون", "مائدة", "فراش"
            ),
            "beaute-sante" to listOf(
                "beauté", "beaute", "parfum", "maquillage", "crème", "creme", "shampoing", "shampooing", "sérum", "serum",
                "rouge à lèvres", "mascara", "fond de teint", "soin", "visage", "cheveux", "peau", "bio",
                "عطر", "مكياج", "كريم", "شامبو", "سيروم", "تجميل", "بشرة", "شعر"
            ),
            "sports-loisirs" to listOf(
                "sport", "sports", "ballon", "football", "foot", "fitness", "yoga", "musculation", "haltère", "haltere",
                "vélo", "velo", "running", "course", "randonnée", "camping", "tapis", "رياضة", "كرة", "لياقة", "جيم", "تدريب"
            ),
            "bebe-enfants" to listOf(
                "bébé", "bebe", "enfant", "enfants", "poussette", "biberon", "couche", "couches", "tétine", "tetine",
                "jouet", "jouets", "doudou", "peluche", "puériculture", "puericulture", "رضيع", "أطفال", "حفاظات", "رضاعة", "لعبة"
            ),
            "chaussures-sacs" to listOf(
                "chaussure", "chaussures", "basket", "baskets", "sneakers", "sandale", "sandales", "talon", "talons",
                "sac", "sacs", "valise", "valises", "sac à dos", "sac a dos", "cuir", "حذاء", "حقيبة", "صندل", "صاك", "صباط"
            ),
            "accessoires-mode" to listOf(
                "montre", "montres", "bijou", "bijoux", "bague", "collier", "bracelet", "ceinture", "lunette", "lunettes",
                "soleil", "portefeuille", "argent", "acier", "ساعة", "مجوهرات", "نظارات", "حزام", "خاتم", "سلسلة"
            ),
            "alimentation-epicerie" to listOf(
                "alimentation", "épicerie", "epicerie", "café", "cafe", "thé", "the", "huile", "olive", "miel", "épice", "epice",
                "chocolat", "biscuit", "snack", "boisson", "bio", "أكل", "بقالة", "قهوة", "شاي", "زيت", "عسل", "توابل"
            ),
            "livres-papeterie" to listOf(
                "livre", "livres", "roman", "cahier", "stylo", "papeterie", "scolaire", "bureau", "classeur", "agenda",
                "كتاب", "دفتر", "قلم", "أدوات مدرسية", "مكتب", "رواية"
            ),
            "automobile-moto" to listOf(
                "auto", "voiture", "moto", "véhicule", "vehicule", "accessoire auto", "support téléphone", "batterie",
                "huile moteur", "casque moto", "سيارة", "دراجة", "اكسسوارات سيارات", "زيت محرك"
            ),
            "autres-produits" to listOf(
                "cadeau", "cadeaux", "divers", "box", "pack", "autre", "هدية", "هدايا", "عروض"
            )
        )
    }

    /**
     * Route un message client vers l'agent IA approprié selon le contexte et les catégories e-commerce.
     */
    suspend fun routeMessageToAgent(
        userMessage: String,
        conversationContext: ConversationContext? = null
    ): AgentResponse = withContext(Dispatchers.IO) {
        val commerceDao = database?.commerceDao() ?: error("Database required for routeMessageToAgent")
        val allCategories = commerceDao.getAllCategoriesList()
        val allProducts = commerceDao.getAllProductsList()

        // 1. Déterminer la catégorie via le contexte explicite ou la détection sémantique
        val category = conversationContext?.categoryId?.let { catId ->
            allCategories.firstOrNull { it.id == catId }
        } ?: detectCategoryFromMessage(userMessage, allCategories)

        val targetCategory = category ?: allCategories.firstOrNull { it.slug == "autres-produits" }
        ?: allCategories.firstOrNull()

        val categoryName = targetCategory?.name ?: "Boutique Tawes Store"
        val categoryId = targetCategory?.id
        val agentName = targetCategory?.aiAgentName?.ifBlank { "Agent Commercial" } ?: "Agent Commercial"

        // 2. Filtrer les produits disponibles en stock pour cette catégorie
        val categoryProducts = if (categoryId != null) {
            allProducts.filter { it.categoryId == categoryId && it.stockQuantity > 0 }
        } else {
            allProducts.filter { it.stockQuantity > 0 }
        }

        // 3. Construire le prompt enrichi avec les produits en stock et le prompt spécifique de la catégorie
        val specializedPrompt = targetCategory?.aiAgentPrompt?.ifBlank {
            "Tu es un assistant commercial expert pour Tawes Store au Maroc. Réponds aux questions avec précision et politesse, en français ou en darija."
        } ?: "Tu es un assistant commercial expert pour Tawes Store au Maroc."

        val productsSummary = if (categoryProducts.isNotEmpty()) {
            categoryProducts.take(15).joinToString("\n") { prod ->
                val price = prod.sellingPrice ?: prod.purchasePrice ?: 0.0
                "- ${prod.title}: ${price.toInt()} MAD (Stock: ${prod.stockQuantity} pcs)${if (prod.hasVideo) " [Vidéo disponible]" else ""}"
            }
        } else {
            "Aucun produit spécifique répertorié pour l'instant dans cette sous-section."
        }

        val fullSystemPrompt = """
$specializedPrompt

PRODUITS DISPONIBLES ACTUELLEMENT EN STOCK DANS LA CATÉGORIE '$categoryName':
$productsSummary

DIRECTIVES DE RÉPONSE:
1. Réponds de façon naturelle, chaleureuse et concise (maximum 150-250 mots).
2. Mentionne TOUJOURS les prix en MAD / DH.
3. Vérifie si l'article demandé est en stock dans la liste ci-dessus.
4. Si l'article existe, confirme sa disponibilité et son prix.
5. Si l'article demandé n'est pas en stock, propose 1 à 2 alternatives disponibles dans la liste.
6. Réponds en français ou en darija marocaine selon la langue utilisée par le client.
7. Termine par une invitation cordiale à commander (ex: 'Pour commander, écrivez-nous ou confirmez votre adresse de livraison').
        """.trimIndent()

        // 4. Générer la réponse via AiEdgeQuantizerEngine / Gemini
        val agentEntity = AgentEntity(
            id = targetCategory?.assignedAgentId ?: "agent-cat-${targetCategory?.slug ?: "general"}",
            name = agentName,
            role = "Expert $categoryName",
            systemPrompt = fullSystemPrompt,
            temperature = targetCategory?.aiAgentTemperature?.toFloat() ?: 0.7f,
            modelId = "gemini-3.5-flash",
            isActive = true
        )

        val replyText = try {
            val inference = AiEdgeQuantizerEngine.runAgentInference(
                agent = agentEntity,
                customerQuery = userMessage,
                knowledgeSources = emptyList(),
                mcpTools = emptyList(),
                products = categoryProducts
            )
            inference.replyText
        } catch (e: Exception) {
            Log.w(TAG, "Erreur inference locale, repli: ${e.message}")
            "Bonjour ! Nous avons bien reçu votre demande concernant la catégorie $categoryName. Nos équipes vérifient la disponibilité et vous répondent dans quelques instants."
        }

        AgentResponse(
            message = replyText,
            agentName = agentName,
            categoryId = categoryId,
            categoryName = categoryName,
            matchedProducts = categoryProducts.take(5)
        )
    }

    /**
     * Détecte la catégorie la plus probable à partir des mots-clés bilingues
     */
    fun detectCategoryFromMessage(message: String, categories: List<CategoryEntity>): CategoryEntity? {
        val lower = message.lowercase(Locale.getDefault())

        // 0. Détection explicite via préfixe structuré (ex: "Catégorie: Électronique & High-Tech", "Catégorie: Mode")
        val categoryPrefixRegex = Regex("""(?:catégorie|categorie|rayon|category)\s*:\s*([^/\n,]+)""", RegexOption.IGNORE_CASE)
        val prefixMatch = categoryPrefixRegex.find(message)
        if (prefixMatch != null) {
            val rawExtracted = prefixMatch.groupValues[1].trim().lowercase(Locale.getDefault())
            val exactCat = categories.firstOrNull { cat ->
                val cName = cat.name.lowercase(Locale.getDefault())
                val cSlug = cat.slug.lowercase(Locale.getDefault())
                cName == rawExtracted || cSlug == rawExtracted ||
                        cName.contains(rawExtracted) || rawExtracted.contains(cName) ||
                        (cName.split(" ", "&", "-").any { w -> w.length >= 4 && rawExtracted.contains(w) })
            }
            if (exactCat != null) return exactCat
        }

        // 1. Recherche par nom direct ou slug de catégorie dans le texte
        for (cat in categories) {
            val catNameLower = cat.name.lowercase(Locale.getDefault())
            val catSlugLower = cat.slug.lowercase(Locale.getDefault())
            if (lower.contains(catNameLower) || lower.contains(catSlugLower)) {
                return cat
            }
        }

        // 2. Recherche par mot-clé dans le dictionnaire sémantique
        for ((slug, words) in CATEGORY_KEYWORDS) {
            if (words.any { lower.contains(it) }) {
                val matched = categories.firstOrNull { it.slug == slug }
                if (matched != null) return matched
            }
        }

        return null
    }
}
