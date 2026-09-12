package com.example.domain.commerce

import com.example.data.local.entity.CategoryEntity

/**
 * Catalogue standard des 12 catégories e-commerce type Jumia Maroc
 * avec agents IA spécialisés et bilinguisme (Français / Arabe).
 */
object DefaultCategoriesCatalog {

    /**
     * Génère le prompt système strict officiel pour les agents de catégories e-commerce.
     * Respecte rigoureusement la procédure en 3 étapes (accueil court, prise d'infos, clôture avec appel),
     * intègre la liste dynamique des catégories réelles et interdit formellement toute mention SaaS/logicielle.
     */
    fun buildStrictCommerceAgentPrompt(
        categoryName: String,
        categoryDescription: String = "",
        availableCategories: List<String> = emptyList()
    ): String {
        val categoriesListFormatted = if (availableCategories.isNotEmpty()) {
            availableCategories.joinToString(", ")
        } else {
            "Mode & Vêtements, Électronique & High-Tech, Maison & Cuisine, Beauté & Santé, Sports & Loisirs, Bébé & Enfants, Chaussures & Sacs, Accessoires de Mode, Alimentation & Épicerie, Livres & Papeterie, Automobile & Moto, Autres Produits & Cadeaux"
        }

        val descPart = if (categoryDescription.isNotBlank()) " ($categoryDescription)" else ""

        return """Tu es l'assistant commercial virtuel officiel de la boutique e-commerce, spécialisé dans le rayon $categoryName$descPart. Ton rôle principal est de traiter les demandes d'achat reçues via WhatsApp, de rassurer les clients et de collecter leurs informations pour valider la pré-commande.

---
### 1. RÔLE & TON DE VOIX
- Professionnel, chaleureux, dynamique et toujours très court/concis.
- Pas de pavés de texte : maximum 2 à 3 phrases courtes par message.
- Langue : Français (ou Darija/Arabe si le client s'exprime en arabe).

---
### 2. TRAITEMENT D'UNE NOUVELLE COMMANDE
Lorsqu'un client clique sur "Commander sur WhatsApp" depuis la boutique, un message pré-formaté arrive contenant :
- Nom du produit (ex: "Produit: ...")
- Catégorie (ex: "Catégorie: $categoryName")
- Prix et Stock disponible

**Procédure à suivre immédiatement :**
1. **Accueil court & confirmation :** Salue le client brièvement et confirme que le produit est bien disponible.
2. **Prise d'informations :** Demande les informations nécessaires pour la livraison :
   • Nom complet
   • Ville de livraison
   • Adresse exacte
   • Numéro de téléphone de contact
3. **Clôture :** Dès que les infos sont données (ou en cours de confirmation), informe le client :
   "Un agent commercial va vous appeler sous peu pour finaliser et confirmer votre commande avec vous. Merci de votre confiance et bonne journée !"

---
### 3. GESTION DES HÉSITATIONS ET CLIENTS SCEPTIQUES
Si le client hésite, pose des questions sur la fiabilité ou demande des détails :
- Rentre dans une posture rassurante sans jamais forcer la vente.
- Rappelle les arguments clés : "Paiement à la livraison", "Produit conforme et contrôlé", "Service client disponible".
- Garde des réponses extrêmement courtes (2 lignes max) pour ne pas bombarder le client d'informations.

---
### 4. RECOMMANDATIONS ET ALTERNATIVES
Si le produit est en rupture ou si le client demande des alternatives / d'autres articles :
- Propose des suggestions uniquement parmi les catégories officielles de la boutique : $categoriesListFormatted.
- Demande quel type de produit ou budget il recherche pour lui recommander le produit adapté de la base Supabase/catalogue.

---
### 5. RÈGLES STRICTES
- Ne jamais inventer des prix ou des caractéristiques non spécifiés dans la fiche produit.
- Ne jamais mentionner les offres logicielles, abonnements ou tarifs de la plateforme elle-même — ce sujet n'existe pas pour ce rôle.
- Toujours mentionner qu'un appel de confirmation par un agent physique aura lieu avant l'expédition finale.""".trimIndent()
    }

    val MAIN_CATEGORIES: List<CategoryEntity> = listOf(
        CategoryEntity(
            id = "cat-mode-vetements",
            name = "Mode & Vêtements",
            nameAr = "الموضة والملابس",
            slug = "mode-vetements",
            parentId = null,
            description = "Vêtements pour hommes, femmes, enfants, tenues traditionnelles et sportswear",
            icon = "👗",
            iconName = "Checkroom",
            displayOrder = 1,
            aiAgentName = "Agent Mode",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Mode & Vêtements", "Vêtements pour hommes, femmes, enfants, tenues traditionnelles"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-electronique",
            name = "Électronique & High-Tech",
            nameAr = "الإلكترونيات والتكنولوجيا",
            slug = "electronique",
            parentId = null,
            description = "Smartphones, ordinateurs, écouteurs sans fil, chargeurs et accessoires connectés",
            icon = "📱",
            iconName = "Devices",
            displayOrder = 2,
            aiAgentName = "Agent Tech",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Électronique & High-Tech", "Smartphones, ordinateurs, écouteurs sans fil, chargeurs"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-tech-01"
        ),
        CategoryEntity(
            id = "cat-maison-cuisine",
            name = "Maison & Cuisine",
            nameAr = "المنزل والمطبخ",
            slug = "maison-cuisine",
            parentId = null,
            description = "Électroménager, ustensiles de cuisine, décoration, literie et aménagement",
            icon = "🏠",
            iconName = "Home",
            displayOrder = 3,
            aiAgentName = "Agent Maison",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Maison & Cuisine", "Électroménager, ustensiles de cuisine, décoration, aménagement"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-beaute-sante",
            name = "Beauté & Santé",
            nameAr = "الجمال والصحة",
            slug = "beaute-sante",
            parentId = null,
            description = "Cosmétiques, soins du visage, parfums authentiques, maquillage et soins personnels",
            icon = "💄",
            iconName = "Spa",
            displayOrder = 4,
            aiAgentName = "Agent Beauté",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Beauté & Santé", "Cosmétiques, soins du visage, parfums, maquillage"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-sports-loisirs",
            name = "Sports & Loisirs",
            nameAr = "الرياضة والترفيه",
            slug = "sports-loisirs",
            parentId = null,
            description = "Équipements de fitness, ballons, vêtements techniques, randonnée et camping",
            icon = "⚽",
            iconName = "SportsSoccer",
            displayOrder = 5,
            aiAgentName = "Agent Sports",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Sports & Loisirs", "Équipements de fitness, ballons, vêtements techniques, plein air"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-bebe-enfants",
            name = "Bébé & Enfants",
            nameAr = "الأطفال والرضع",
            slug = "bebe-enfants",
            parentId = null,
            description = "Vêtements bébé et enfant, puériculture, biberons, poussettes et jouets d'éveil",
            icon = "👶",
            iconName = "ChildCare",
            displayOrder = 6,
            aiAgentName = "Agent Enfants",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Bébé & Enfants", "Vêtements bébé/enfant, puériculture et jouets"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-chaussures-sacs",
            name = "Chaussures & Sacs",
            nameAr = "الأحذية والحقائب",
            slug = "chaussures-sacs",
            parentId = null,
            description = "Sneakers, chaussures de ville, sandales, sacs à main, sacs à dos et valises",
            icon = "👟",
            iconName = "ShoppingBag",
            displayOrder = 7,
            aiAgentName = "Agent Chaussures",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Chaussures & Sacs", "Sneakers, chaussures de ville, sacs à main et valises"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-accessoires-mode",
            name = "Accessoires de Mode",
            nameAr = "إكسسوارات الموضة",
            slug = "accessoires-mode",
            parentId = null,
            description = "Montres de marque, bijoux, ceintures, lunettes de soleil et portefeuilles",
            icon = "⌚",
            iconName = "Watch",
            displayOrder = 8,
            aiAgentName = "Agent Accessoires",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Accessoires de Mode", "Montres, bijoux, ceintures, lunettes de soleil"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-alimentation-epicerie",
            name = "Alimentation & Épicerie",
            nameAr = "الأغذية والبقالة",
            slug = "alimentation-epicerie",
            parentId = null,
            description = "Épicerie salée et sucrée, thés & cafés, épices, produits du terroir marocain",
            icon = "🍎",
            iconName = "Restaurant",
            displayOrder = 9,
            aiAgentName = "Agent Alimentation",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Alimentation & Épicerie", "Épicerie salée/sucrée, thés, cafés et épices"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-livres-papeterie",
            name = "Livres & Papeterie",
            nameAr = "الكتب والقرطاسية",
            slug = "livres-papeterie",
            parentId = null,
            description = "Fournitures scolaires, bureautique, livres, cahiers, stylos et agendas",
            icon = "📚",
            iconName = "MenuBook",
            displayOrder = 10,
            aiAgentName = "Agent Culture",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Livres & Papeterie", "Fournitures scolaires, bureautique et livres"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-automobile-moto",
            name = "Automobile & Moto",
            nameAr = "السيارات والدراجات",
            slug = "automobile-moto",
            parentId = null,
            description = "Accessoires auto, supports smartphone, entretien, ampoules, casques et outillage",
            icon = "🚗",
            iconName = "DirectionsCar",
            displayOrder = 11,
            aiAgentName = "Agent Auto",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Automobile & Moto", "Accessoires auto, supports tech, entretien et casques"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "cat-autres-produits",
            name = "Autres Produits & Cadeaux",
            nameAr = "منتجات أخرى وهدايا",
            slug = "autres-produits",
            parentId = null,
            description = "Produits divers, coffrets cadeaux, animaux de compagnie, papeterie et nouveautés",
            icon = "📦",
            iconName = "Category",
            displayOrder = 12,
            aiAgentName = "Agent Général",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Autres Produits & Cadeaux", "Produits divers, coffrets cadeaux et nouveautés"),
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-02"
        )
    )

    /**
     * Sous-catégories clés pour enrichir la navigation
     */
    val SUB_CATEGORIES: List<CategoryEntity> = listOf(
        CategoryEntity(
            id = "sub-vetements-homme",
            name = "Vêtements Homme",
            nameAr = "ملابس رجالية",
            slug = "vetements-homme",
            parentId = "cat-mode-vetements",
            description = "T-shirts, polos, chemises, pantalons, jeans et costumes homme",
            icon = "👔",
            iconName = "Checkroom",
            displayOrder = 1,
            aiAgentName = "Agent Mode Homme",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Vêtements Homme", "T-shirts, polos, chemises, pantalons et costumes"),
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "sub-vetements-femme",
            name = "Vêtements Femme",
            nameAr = "ملابس نسائية",
            slug = "vetements-femme",
            parentId = "cat-mode-vetements",
            description = "Robes, jupes, chemisiers, abayas, hijabs et pantalons femme",
            icon = "👗",
            iconName = "Checkroom",
            displayOrder = 2,
            aiAgentName = "Agent Mode Femme",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Vêtements Femme", "Robes, jupes, abayas, hijabs et pantalons"),
            assignedAgentId = "agent-sales-02"
        ),
        CategoryEntity(
            id = "sub-smartphones",
            name = "Smartphones & Tablettes",
            nameAr = "الهواتف الذكية والأجهزة اللوحية",
            slug = "smartphones-tablettes",
            parentId = "cat-electronique",
            description = "Samsung, iPhone, Xiaomi, Oppo, Realme et tablettes",
            icon = "📱",
            iconName = "Devices",
            displayOrder = 1,
            aiAgentName = "Agent Smartphones",
            aiAgentPrompt = buildStrictCommerceAgentPrompt("Smartphones & Tablettes", "Smartphones, tablettes, accessoires"),
            assignedAgentId = "agent-tech-01"
        )
    )

    val ALL_DEFAULT_CATEGORIES: List<CategoryEntity> = MAIN_CATEGORIES + SUB_CATEGORIES
}
