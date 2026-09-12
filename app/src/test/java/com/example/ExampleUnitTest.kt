package com.example

import com.example.domain.telegram.TelegramBridgeScript
import com.example.util.ProductMediaManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun telegramInstallCommand_hasNoParasiticPrefix() {
    val actual = TelegramBridgeScript.INSTALL_COMMAND.trim()

    // Assert no parasitic prefix like 'ps-ox' or leading space/newline
    assertFalse("Must not start with ps-ox", actual.startsWith("ps-ox"))
    assertTrue("Must contain pkg update", actual.contains("pkg update"))
    assertFalse("Must not contain unneeded carriage returns", actual.contains("\r"))
  }

  @Test
  fun videoDetection_identifiesFormatsCorrectly() {
    // Tests vidéos en ligne
    assertTrue(ProductMediaManager.isVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertTrue(ProductMediaManager.isVideoUrl("https://youtu.be/dQw4w9WgXcQ"))
    assertTrue(ProductMediaManager.isVideoUrl("https://vimeo.com/76979871"))
    
    // Tests formats fichiers vidéo
    assertTrue(ProductMediaManager.isVideoUrl("https://storage.supabase.co/v1/object/public/product-media/clip.mp4"))
    assertTrue(ProductMediaManager.isVideoUrl("/data/data/com.example/files/demo.webm"))
    assertTrue(ProductMediaManager.isVideoUrl("file:///storage/emulated/0/video.mov"))

    // Tests images standards (ne doivent pas être détectées comme vidéos)
    assertFalse(ProductMediaManager.isVideoUrl("https://storage.supabase.co/v1/object/public/product-media/photo.jpg"))
    assertFalse(ProductMediaManager.isVideoUrl("https://example.com/image.png"))
    assertFalse(ProductMediaManager.isVideoUrl("/storage/emulated/0/picture.webp"))
  }

  @Test
  fun ecommerceAgent_storefrontOrderHandling_strictProtocolAndNoSaaS() {
    val agent = com.example.data.local.entity.AgentEntity(
      id = "agent-sales-01",
      name = "Yasmine - Mode & Style",
      role = "Commercial",
      systemPrompt = com.example.domain.commerce.DefaultCategoriesCatalog.buildStrictCommerceAgentPrompt(
        categoryName = "Mode & Vêtements",
        categoryDescription = "Prêt-à-porter, tenues traditionnelles et modernes, sacs et accessoires de mode.",
        availableCategories = listOf("Mode & Vêtements", "Électronique & High-Tech", "Maison & Cuisine")
      ),
      temperature = 0.4f,
      isActive = true,
      modelId = "qwen2.5-0.5b-instruct"
    )

    val products = listOf(
      com.example.data.local.entity.ProductEntity(
        id = "prod-akkipi",
        title = "ensemble AKKIPI",
        description = "Ensemble élégant deux pièces pour femme, tissu respirant de haute qualité.",
        purchasePrice = 60.0,
        sellingPrice = 110.0,
        currency = "MAD",
        stockQuantity = 15,
        status = "PUBLISHED",
        categoryId = "cat-mode-01"
      )
    )

    val customerQuery = """
      Commande Tawes Store
      Produit: ensemble AKKIPI
      Catégorie: Mode & Vêtements
      Prix: 110 dh
      Stock: 15
      Description: Ensemble élégant deux pièces pour femme...
      Lien: https://example.com/p/akkipi
      Je souhaite finaliser ma commande pour ce produit
    """.trimIndent()

    val result = kotlinx.coroutines.runBlocking {
      com.example.domain.engine.AiEdgeQuantizerEngine.runAgentInference(
        agent = agent,
        customerQuery = customerQuery,
        knowledgeSources = emptyList<com.example.data.local.entity.KnowledgeSourceEntity>(),
        mcpTools = emptyList<com.example.data.local.entity.McpToolEntity>(),
        products = products
      )
    }

    val reply = result.replyText
    assertTrue("Reply must not be empty", reply.isNotBlank())
    
    // 1. Accueil & confirmation disponibilité
    assertTrue("Must confirm product or availability", reply.contains("ensemble AKKIPI", ignoreCase = true) || reply.contains("disponible", ignoreCase = true))
    
    // 2. Prise d'informations de livraison
    assertTrue("Must request delivery info (nom / ville / adresse / téléphone)", 
      (reply.contains("nom", ignoreCase = true) && reply.contains("ville", ignoreCase = true)) ||
      reply.contains("adresse", ignoreCase = true) ||
      reply.contains("téléphone", ignoreCase = true)
    )

    // 3. Clôture immédiate avec appel de confirmation
    assertTrue("Must inform customer that a commercial agent will call to confirm",
      reply.contains("agent commercial va vous appeler", ignoreCase = true) ||
      reply.contains("va vous appeler", ignoreCase = true)
    )

    // 4. Strict absence of SaaS packages & subscription prices
    assertFalse("Must not mention Pack Starter", reply.contains("Pack Starter", ignoreCase = true))
    assertFalse("Must not mention Pack Pro", reply.contains("Pack Pro", ignoreCase = true))
    assertFalse("Must not mention Pack Entreprise", reply.contains("Pack Entreprise", ignoreCase = true))
    assertFalse("Must not mention 29€", reply.contains("29€") || reply.contains("29 €"))
    assertFalse("Must not mention 79€", reply.contains("79€") || reply.contains("79 €"))
    assertFalse("Must not mention 249€", reply.contains("249€") || reply.contains("249 €"))
  }

  @Test
  fun strictCommercePromptFactory_generatesConsistentDirectives() {
    val prompt = com.example.domain.commerce.DefaultCategoriesCatalog.buildStrictCommerceAgentPrompt(
      categoryName = "Électronique & High-Tech",
      categoryDescription = "Smartphones, écouteurs sans fil, chargeurs rapides et gadgets connectés.",
      availableCategories = listOf("Mode & Vêtements", "Électronique & High-Tech", "Maison & Cuisine")
    )

    assertTrue(prompt.contains("spécialisé dans le rayon Électronique & High-Tech"))
    assertTrue(prompt.contains("Un agent commercial va vous appeler sous peu pour finaliser et confirmer votre commande avec vous"))
    assertTrue(prompt.contains("Paiement à la livraison"))
    assertFalse(prompt.contains("Pack Starter"))
    assertFalse(prompt.contains("Pack Pro"))
  }

  @Test
  fun categoryRouter_detectsCategoryFromExplicitPrefixAndKeywords() {
    val router = com.example.domain.ai.CategoryAgentRouter()
    val categories = com.example.domain.commerce.DefaultCategoriesCatalog.ALL_DEFAULT_CATEGORIES

    // 1. Détection via préfixe explicite
    val messageWithPrefix = "Commande Tawes Store\nCatégorie: Électronique & High-Tech\nProduit: Écouteurs Bluetooth"
    val catFromPrefix = router.detectCategoryFromMessage(messageWithPrefix, categories)
    assertNotNull("Should detect category from explicit prefix", catFromPrefix)
    assertEquals("cat-electronique", catFromPrefix?.id)

    // 2. Détection via mots-clés Mode
    val messageClothing = "Bonjour, je cherche une robe élégante et un pantalon en lin"
    val catClothing = router.detectCategoryFromMessage(messageClothing, categories)
    assertNotNull("Should detect Mode & Vêtements from keywords", catClothing)
    assertEquals("cat-mode-vetements", catClothing?.id)

    // 3. Détection via mots-clés Tech
    val messageTech = "Avez-vous des écouteurs sans fil ou un chargeur rapide smartphone ?"
    val catTech = router.detectCategoryFromMessage(messageTech, categories)
    assertNotNull("Should detect Électronique from keywords", catTech)
    assertEquals("cat-electronique", catTech?.id)
  }

  @Test
  fun categoryCatalog_assignedAgentsAreHarmonized() {
    val categories = com.example.domain.commerce.DefaultCategoriesCatalog.ALL_DEFAULT_CATEGORIES
    
    // Tous les agents assignés doivent être "agent-tech-01" (pour la tech) ou "agent-sales-02"
    for (cat in categories) {
      assertNotNull("Assigned agent id must not be null for ${cat.name}", cat.assignedAgentId)
      if (cat.id == "cat-electronique" || cat.id == "sub-smartphones") {
        assertEquals("Tech category must map to agent-tech-01", "agent-tech-01", cat.assignedAgentId)
      } else {
        assertEquals("Commerce category ${cat.name} must map to agent-sales-02", "agent-sales-02", cat.assignedAgentId)
      }
    }
  }

  @Test
  fun telegramMedia_inaccessiblePathResolvesToLocalBridgeHttp() {
    val testMessage = com.example.data.local.entity.TelegramMessageEntity(
      id = "msg-999",
      channelId = -100123456789L,
      channelTitle = "Canal Grossiste",
      messageId = 42L,
      text = "Nouveau produit avec photo",
      mediaType = "photo",
      localMediaPath = "/data/data/com.termux/files/home/telegram_media/-100123456789/42/photo_42.jpg"
    )

    val urls = testMessage.getMediaUrls()
    assertEquals(1, urls.size)
    val resolvedUrl = urls.first()
    assertTrue("Should resolve to 127.0.0.1:8088 media endpoint", resolvedUrl.startsWith("http://127.0.0.1:8088/media/"))
    assertTrue("Should contain channel id", resolvedUrl.contains("-100123456789"))
    assertTrue("Should contain message id", resolvedUrl.contains("42"))
    assertTrue("Should contain file name", resolvedUrl.contains("photo_42.jpg"))

    val items = testMessage.getMediaItems()
    assertEquals(1, items.size)
    val item = items.first()
    assertEquals("http://127.0.0.1:8088/media/-100123456789/42/photo_42.jpg", item.url)
    assertNull("localPath must be null when file is not accessible", item.localPath)
    assertEquals("http://127.0.0.1:8088/media/-100123456789/42/photo_42.jpg", item.getDisplayModel())
  }

  @Test
  fun initialOrderCheckoutMessage_withEnabledTools_returnsSalesScenario_andNeverDirectTracking() {
    val agent = com.example.data.local.entity.AgentEntity(
      id = "agent-sales-02",
      name = "Conseiller Vente Tawes Store",
      role = "Commercial e-commerce",
      systemPrompt = "Tu es un vendeur bienveillant pour la boutique en ligne Tawes Store.",
      temperature = 0.5f,
      modelId = "llama-3.2-1b-int4",
      ragEnabled = true,
      isActive = true
    )

    val allTools = listOf(
      com.example.data.local.entity.McpToolEntity(
        id = "tool-1",
        name = "check_order_status",
        description = "Vérifier le statut d'une commande",
        isEnabled = true
      ),
      com.example.data.local.entity.McpToolEntity(
        id = "tool-2",
        name = "get_product_price",
        description = "Consulter le prix d'un produit",
        isEnabled = true
      )
    )

    val products = listOf(
      com.example.data.local.entity.ProductEntity(
        id = "prod-akkipi",
        title = "Ensemble AKKIPI",
        sellingPrice = 110.0,
        currency = "MAD",
        stockQuantity = 15,
        status = "PUBLISHED"
      )
    )

    val checkoutMessage = """
      Produit: Ensemble AKKIPI
      Catégorie: Mode & Vêtements
      Prix: 110 dh
      Stock: 15
      Description: Ensemble élégant deux pièces pour femme...
      Lien: https://example.com/p/akkipi
      Je souhaite finaliser ma commande pour ce produit
    """.trimIndent()

    val result = kotlinx.coroutines.runBlocking {
      com.example.domain.engine.AiEdgeQuantizerEngine.runAgentInference(
        agent = agent,
        customerQuery = checkoutMessage,
        knowledgeSources = emptyList(),
        mcpTools = allTools,
        products = products,
        orders = emptyList()
      )
    }

    val reply = result.replyText
    assertTrue("Reply must not be empty", reply.isNotBlank())

    // 1. Tool check_order_status ne doit JAMAIS s'exécuter sur un premier message de commande
    assertFalse("check_order_status must NOT be triggered on initial order checkout",
      result.toolCalls.any { it.contains("check_order_status") })

    // 2. Doit répondre selon le scénario de vente (accueil + demande coordonnées)
    assertTrue("Must confirm product availability", reply.contains("Ensemble AKKIPI", ignoreCase = true) || reply.contains("disponible", ignoreCase = true))
    assertTrue("Must ask for delivery info", reply.contains("Nom complet", ignoreCase = true) || reply.contains("Ville", ignoreCase = true))
    assertTrue("Must promise sales call", reply.contains("agent commercial va vous appeler", ignoreCase = true))

    // 3. Ne doit JAMAIS renvoyer le texte générique de suivi direct ni le faux code #CMD-9201
    assertFalse("Must NOT return Suivi de votre commande en direct", reply.contains("Suivi de votre commande en direct", ignoreCase = true))
    assertFalse("Must NOT return fake #CMD-9201", reply.contains("#CMD-9201"))
  }

  @Test
  fun subsequentTrackingMessage_withExistingOrder_executesTool_andReturnsRealOrderData() {
    val agent = com.example.data.local.entity.AgentEntity(
      id = "agent-sales-02",
      name = "Conseiller Vente Tawes Store",
      role = "Commercial e-commerce",
      systemPrompt = "Tu es un vendeur pour Tawes Store.",
      temperature = 0.5f,
      modelId = "llama-3.2-1b-int4",
      ragEnabled = true,
      isActive = true
    )

    val tools = listOf(
      com.example.data.local.entity.McpToolEntity(
        id = "tool-1",
        name = "check_order_status",
        description = "Vérifier le statut d'une commande",
        isEnabled = true
      )
    )

    val existingOrders = listOf(
      com.example.data.local.entity.OrderEntity(
        id = "ord-real-01",
        orderNumber = "#CMD-4412",
        customerName = "Karim Benani",
        customerPhone = "0612345678",
        deliveryAddress = "Casablanca, Bd Zerktouni Imm 14",
        deliveryZone = "Grand Casablanca",
        productId = "prod-akkipi",
        productName = "Ensemble AKKIPI Chic",
        quantity = 1,
        totalAmount = 110.0,
        currency = "MAD",
        status = "IN_DELIVERY",
        customerCallNotes = "Confirmé",
        createdAt = System.currentTimeMillis()
      )
    )

    val trackingQuery = "Bonjour, où en est ma commande #CMD-4412 svp ?"

    val result = kotlinx.coroutines.runBlocking {
      com.example.domain.engine.AiEdgeQuantizerEngine.runAgentInference(
        agent = agent,
        customerQuery = trackingQuery,
        knowledgeSources = emptyList(),
        mcpTools = tools,
        products = emptyList(),
        orders = existingOrders,
        customerPhone = "0612345678"
      )
    }

    // 1. Tool check_order_status DOIT s'exécuter
    assertTrue("check_order_status MUST be executed on explicit tracking query",
      result.toolCalls.any { it.contains("check_order_status") })

    val reply = result.replyText
    // 2. Doit afficher les VRAIES données de la commande
    assertTrue("Reply must mention real order number #CMD-4412", reply.contains("#CMD-4412"))
    assertTrue("Reply must mention real product Ensemble AKKIPI Chic", reply.contains("Ensemble AKKIPI Chic"))
    assertTrue("Reply must mention real total 110 MAD", reply.contains("110 MAD"))
    assertTrue("Reply must mention in delivery status", reply.contains("En cours d'acheminement", ignoreCase = true))
    assertTrue("Reply must mention destination Casablanca", reply.contains("Casablanca"))
    assertFalse("Reply must NOT contain hardcoded #CMD-9201", reply.contains("#CMD-9201"))
  }

  @Test
  fun subsequentTrackingMessage_withNonExistentOrder_informsCustomerHonestly() {
    val agent = com.example.data.local.entity.AgentEntity(
      id = "agent-sales-02",
      name = "Conseiller Vente",
      role = "Commercial",
      systemPrompt = "Tu es un vendeur.",
      temperature = 0.5f,
      modelId = "llama-3.2-1b-int4",
      isActive = true
    )

    val tools = listOf(
      com.example.data.local.entity.McpToolEntity(
        id = "tool-1",
        name = "check_order_status",
        description = "Vérifier le statut d'une commande",
        isEnabled = true
      )
    )

    val trackingQuery = "Bonjour, où en est mon colis #CMD-9999 ?"

    val result = kotlinx.coroutines.runBlocking {
      com.example.domain.engine.AiEdgeQuantizerEngine.runAgentInference(
        agent = agent,
        customerQuery = trackingQuery,
        knowledgeSources = emptyList(),
        mcpTools = tools,
        products = emptyList(),
        orders = emptyList() // aucune commande
      )
    }

    assertTrue("Tool should be executed", result.toolCalls.any { it.contains("check_order_status") })
    val reply = result.replyText
    assertTrue("Must state that no order was found", reply.contains("aucune commande", ignoreCase = true))
    assertFalse("Must NOT invent fake #CMD-9201", reply.contains("#CMD-9201"))
    assertFalse("Must NOT invent fake tomorrow arrival", reply.contains("demain 14h", ignoreCase = true))
  }

  @Test
  fun categoryAgents_differBetweenTechAndFashion() {
    val router = com.example.domain.ai.CategoryAgentRouter()
    val categories = com.example.domain.commerce.DefaultCategoriesCatalog.ALL_DEFAULT_CATEGORIES

    val techMsg = "Je cherche des écouteurs sans fil bluetooth avec réduction de bruit"
    val techCat = router.detectCategoryFromMessage(techMsg, categories)
    assertNotNull(techCat)
    assertEquals("cat-electronique", techCat?.id)
    assertEquals("agent-tech-01", techCat?.assignedAgentId)

    val techPrompt = com.example.domain.commerce.DefaultCategoriesCatalog.buildStrictCommerceAgentPrompt(
      categoryName = techCat!!.name,
      categoryDescription = techCat.description ?: ""
    )
    assertTrue("Tech prompt must focus on tech", techPrompt.contains("Électronique & High-Tech"))

    val fashionMsg = "Je cherche un ensemble robe élégante pour une soirée"
    val fashionCat = router.detectCategoryFromMessage(fashionMsg, categories)
    assertNotNull(fashionCat)
    assertEquals("cat-mode-vetements", fashionCat?.id)
    assertEquals("agent-sales-02", fashionCat?.assignedAgentId)

    val fashionPrompt = com.example.domain.commerce.DefaultCategoriesCatalog.buildStrictCommerceAgentPrompt(
      categoryName = fashionCat!!.name,
      categoryDescription = fashionCat.description ?: ""
    )
    assertTrue("Fashion prompt must focus on fashion", fashionPrompt.contains("Mode & Vêtements"))
  }
}
