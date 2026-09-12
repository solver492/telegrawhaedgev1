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
}
