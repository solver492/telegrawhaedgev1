package com.example.domain.commerce

import com.example.data.local.entity.CategoryEntity

/**
 * Catalogue standard des 12 catégories e-commerce type Jumia Maroc
 * avec agents IA spécialisés et bilinguisme (Français / Arabe).
 */
object DefaultCategoriesCatalog {

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
            aiAgentPrompt = "Tu es un expert en mode et vêtements au Maroc pour Tawes Store. Tu connais parfaitement les tailles (S/M/L/XL/XXL), les coupes, les matières (coton, lin, polyester, soie), les styles (casual, formel, sport, traditionnel, abayas/djellabas). Tu aides les clients à choisir la bonne taille, conseilles sur les associations de couleurs et styles. Réponds en français ou en arabe darija selon le client. Mentionne toujours les prix en MAD et la disponibilité en stock.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un expert en électronique et high-tech au Maroc pour Tawes Store. Tu connais les spécifications techniques (smartphones, RAM, stockage, processeurs, autonomie, caméras), ordinateurs, écouteurs sans fil, accessoires connectés. Tu compares les modèles, expliques les garanties et compatibilités. Réponds en français/darija avec précision, prix en MAD et stock en temps réel.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un expert en équipement pour la maison, électroménager et décoration pour Tawes Store. Tu conseilles sur les appareils électroménagers (puissance, capacité, consommation, durabilité), ustensiles de cuisine, mobilier et literie. Réponds en français/darija de façon chaleureuse et professionnelle avec prix en MAD.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un expert en produits cosmétiques, parfums et soins personnels pour Tawes Store. Tu connais les types de peau (grasse, sèche, mixte, sensible), les ingrédients, les routines beauté, les soins capillaires et parfums authentiques. Conseille des routines adaptées avec bienveillance en français/darija.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-support-02"
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
            aiAgentPrompt = "Tu es un expert en équipement sportif, vêtements techniques, fitness et loisirs outdoor pour Tawes Store. Tu conseilles selon le sport pratiqué (football, running, musculation, randonnée) et le niveau. Réponds en français/darija avec détails techniques et prix en MAD.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un spécialiste en puériculture, vêtements bébés/enfants et jouets d'éveil pour Tawes Store. Tu rassures les parents sur les normes de sécurité, les tailles par âge (0-6m, 6-12m, 2-12 ans) et les matières saines. Réponds avec douceur en français/darija.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-support-02"
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
            aiAgentPrompt = "Tu es un spécialiste en chaussures (sneakers, derbies, sandales, talons) et maroquinerie (sacs à main, sacs à dos, valises) pour Tawes Store. Tu aides pour les pointures (guide européen/US), le confort et l'entretien du cuir. Réponds en français/darija.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un conseiller en montres, bijoux, ceintures, lunettes de soleil et accessoires de mode pour Tawes Store. Tu conseilles sur les tendances, les finitions (acier inoxydable, plaqué, cuir) et les idées cadeaux. Réponds en français/darija.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un conseiller en alimentation générale, épices marocaines, thés, cafés, épicerie fine et produits bio pour Tawes Store. Tu renseignes sur la fraîcheur, conservation, origine des produits et délais de livraison rapide.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un conseiller librairie, fournitures scolaires, bureautique et loisirs créatifs pour Tawes Store. Tu aides à trouver les manuels, fournitures de bureau et recommandations de lecture pour tous âges en français/arabe.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-support-02"
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
            aiAgentPrompt = "Tu es un spécialiste en accessoires automobiles, entretien mécanique léger, équipement moto et supports tech pour Tawes Store. Tu renseignes sur la compatibilité des pièces et accessoires avec le modèle de véhicule du client.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es un assistant commercial polyvalent pour Tawes Store. Tu réponds aux demandes générales, orientes vers les bonnes catégories de notre boutique, indiques les conditions de livraison et aides les clients avec bienveillance en français et en darija.",
            aiAgentTemperature = 0.7,
            assignedAgentId = "agent-sales-01"
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
            aiAgentPrompt = "Tu es spécialisé en mode masculine chez Tawes Store. Tu conseilles sur les coupes, matières et associations chemise/pantalon."
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
            aiAgentPrompt = "Tu es spécialisée en mode féminine chez Tawes Store. Tu conseilles sur les styles modernes et traditionnels."
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
            aiAgentPrompt = "Expert smartphones et tablettes chez Tawes Store. Tu compares autonomie, puissance et rapport qualité/prix."
        )
    )

    val ALL_DEFAULT_CATEGORIES: List<CategoryEntity> = MAIN_CATEGORIES + SUB_CATEGORIES
}
