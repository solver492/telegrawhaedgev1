package com.example.domain.baileys

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AgentEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.WhatsAppInstanceEntity
import com.example.data.local.entity.WhatsAppMessageEntity
import com.example.domain.engine.AiEdgeQuantizerEngine
import com.example.domain.engine.EdgeNeuralReasoningEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BaileysEvent(
    val instanceId: String,
    val eventType: String, // connection.update, messages.upsert, qr.update
    val payload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class BaileysService(
    private val database: AppDatabase,
    private val supabaseSyncService: com.example.domain.supabase.SupabaseSyncService? = null
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _eventsFlow = MutableSharedFlow<BaileysEvent>(replay = 10)
    val eventsFlow: SharedFlow<BaileysEvent> = _eventsFlow.asSharedFlow()

    /**
     * Start connection for an instance (emulating Baileys makeWASocket lifecycle)
     */
    fun startInstance(instance: WhatsAppInstanceEntity) {
        scope.launch {
            val waDao = database.whatsAppDao()

            // Update to CONNECTING
            waDao.updateStatus(instance.id, "CONNECTING", "", "")
            _eventsFlow.emit(BaileysEvent(instance.id, "connection.update", "Connecting to Baileys multi-device socket..."))
            delay(800)

            if (instance.pairingMethod == "PAIRING_CODE") {
                val generatedCode = generatePairingCode()
                waDao.updateStatus(instance.id, "PAIRING_CODE", "", generatedCode)
                _eventsFlow.emit(BaileysEvent(instance.id, "pairing.code", "Pairing code generated: $generatedCode"))
            } else {
                val fakeQr = "2@${UUID.randomUUID().toString().take(12)}...baileys_qr_auth"
                waDao.updateStatus(instance.id, "QR_READY", fakeQr, "")
                _eventsFlow.emit(BaileysEvent(instance.id, "qr.update", "QR Code ready for scanning"))
            }
        }
    }

    /**
     * Simulates scanning QR code or completing Pairing Code verification
     */
    fun confirmConnection(instanceId: String) {
        scope.launch {
            val waDao = database.whatsAppDao()
            waDao.updateStatus(instanceId, "CONNECTED", "", "")
            _eventsFlow.emit(BaileysEvent(instanceId, "connection.update", "WhatsApp Web Session Authenticated. Status: Open"))
        }
    }

    /**
     * Disconnects an instance
     */
    fun disconnectInstance(instanceId: String) {
        scope.launch {
            val waDao = database.whatsAppDao()
            waDao.updateStatus(instanceId, "DISCONNECTED", "", "")
            _eventsFlow.emit(BaileysEvent(instanceId, "connection.update", "Session closed by user"))
        }
    }

    /**
     * Handles an incoming WhatsApp message received on a specific instance,
     * routes it to the correct AI Agent, executes RAG & Edge AI inference,
     * and replies automatically.
     */
    suspend fun handleIncomingMessage(
        instanceId: String,
        senderJid: String,
        senderName: String,
        messageText: String
    ): WhatsAppMessageEntity? {
        val msgDao = database.whatsAppMessageDao()
        val agentDao = database.agentDao()
        val knowDao = database.knowledgeDao()
        val mcpDao = database.mcpDao()

        // 1. Record incoming customer message
        val incomingMsg = WhatsAppMessageEntity(
            id = UUID.randomUUID().toString(),
            instanceId = instanceId,
            remoteJid = senderJid,
            senderName = senderName,
            content = messageText,
            isFromCustomer = true,
            timestamp = System.currentTimeMillis()
        )
        msgDao.insertMessage(incomingMsg)
        _eventsFlow.emit(BaileysEvent(instanceId, "messages.upsert", "Incoming message from $senderName: $messageText"))

        // 1.5. Check conversation-level AI override (Toggle / Agent specific per conversation)
        val cleanJid = senderJid.trim()
        val rawNumber = cleanJid.substringBefore("@").replace("+", "").replace(" ", "").trim()
        val withPlus = "+$rawNumber"
        val withWhatsappSuffix = if (cleanJid.contains("@")) cleanJid else "$rawNumber@s.whatsapp.net"
        val conversationOverride = agentDao.getConversationOverride(cleanJid)
            ?: agentDao.getConversationOverride(withWhatsappSuffix)
            ?: agentDao.getConversationOverride(rawNumber)
            ?: agentDao.getConversationOverride(withPlus)
            ?: agentDao.getConversationOverride(senderJid)
            ?: agentDao.getAllConversationOverridesList().firstOrNull { override ->
                val overrideRaw = override.remoteJid.substringBefore("@").replace("+", "").replace(" ", "").trim()
                overrideRaw == rawNumber || override.remoteJid.equals(cleanJid, ignoreCase = true) || override.remoteJid.equals(senderJid, ignoreCase = true)
            }

        if (conversationOverride != null && !conversationOverride.isAiEnabled) {
            // AI is explicitly disabled by user for this contact/thread (Human takeover mode)
            _eventsFlow.emit(BaileysEvent(instanceId, "messages.skip", "IA désactivée pour la discussion $senderJid (Mode Humain - Aucun message envoyé)"))
            return null
        }

        // 1.5 Retrieve active conversation session if any
        val existingSession = agentDao.getActiveConversationSession(senderJid)

        // 2. Resolve target AI Agent via smart routing engine (Override, Category-first, then Instance, Keywords, Schedule, Fallback)
        val activeAgents = agentDao.getActiveAgents()
        val allCategories = database.commerceDao().getAllCategoriesList()
        val allProducts = database.commerceDao().getAllProductsList()

        val selectedAgent: AgentEntity?
        val routingReason: String
        var matchedCategory: CategoryEntity? = null

        if (conversationOverride?.forcedAgentId != null) {
            val forced = activeAgents.firstOrNull { it.id == conversationOverride.forcedAgentId }
                ?: agentDao.getAgentById(conversationOverride.forcedAgentId)
            if (forced != null) {
                selectedAgent = forced
                routingReason = "Agent assigné manuellement à cette discussion (${forced.name})"
            } else {
                val routingDecision = selectBestAgentForMessage(
                    agents = activeAgents,
                    categories = allCategories,
                    products = allProducts,
                    instanceId = instanceId,
                    messageText = messageText,
                    activeSession = existingSession
                )
                selectedAgent = routingDecision.agent
                routingReason = routingDecision.reason
                matchedCategory = routingDecision.matchedCategory
            }
        } else {
            val routingDecision = selectBestAgentForMessage(
                agents = activeAgents,
                categories = allCategories,
                products = allProducts,
                instanceId = instanceId,
                messageText = messageText,
                activeSession = existingSession
            )
            selectedAgent = routingDecision.agent
            routingReason = routingDecision.reason
            matchedCategory = routingDecision.matchedCategory
        }

        if (selectedAgent == null) {
            // No active agent configured or all inactive - Do NOT send auto-reply, respect silence
            _eventsFlow.emit(
                BaileysEvent(
                    instanceId,
                    "messages.skip",
                    "Aucun agent IA actif pour $senderJid. Aucun message automatique envoyé."
                )
            )
            return null
        }

        // 2.5. Check if the selected agent is specifically disabled for this conversation
        if (conversationOverride != null && !conversationOverride.isAgentActive(selectedAgent.id)) {
            _eventsFlow.emit(
                BaileysEvent(
                    instanceId,
                    "messages.skip",
                    "Agent ${selectedAgent.name} désactivé pour la conversation $senderJid (Reprise manuelle - Aucun message envoyé)"
                )
            )
            return null
        }

        // 3. Retrieve relevant RAG knowledge sources for this agent (strictly e-commerce scoped)
        val rawKnowledgeSources = knowDao.getSourcesForAgent(selectedAgent.id)
        val knowledgeSources = rawKnowledgeSources.filter { source ->
            val content = (source.title + " " + source.contentData).lowercase(Locale.getDefault())
            !content.contains("pack starter") &&
            !content.contains("pack pro") &&
            !content.contains("pack entreprise") &&
            !content.contains("29€") &&
            !content.contains("79€") &&
            !content.contains("249€") &&
            !content.contains("tarifs & services") &&
            !content.contains("page web tarifs")
        }

        // 4. Retrieve enabled MCP tools
        val mcpTools = mcpDao.getEnabledTools()

        // 5. Retrieve dynamic product catalog (RAG) strictly filtered by category
        var products = if (matchedCategory != null) {
            allProducts.filter { it.categoryId == matchedCategory.id }
        } else {
            val agentCats = allCategories.filter { it.assignedAgentId == selectedAgent.id }
            if (agentCats.isNotEmpty()) {
                val catIds = agentCats.map { it.id }.toSet()
                allProducts.filter { it.categoryId in catIds }
            } else {
                allProducts
            }
        }

        // 5.5 DYNAMIC KNOWLEDGE BASE & SUPABASE RAG INJECTION
        // Lorsqu'un client arrive depuis la vitrine web ou interroge un produit/service,
        // interroger dynamiquement Supabase pour charger la fiche produit en direct (Titre, Description, Specs, Prix, Stock, FAQ)
        var dynamicProductSnippet: String? = null
        try {
            if (supabaseSyncService != null) {
                val dynamicKnowledgeList = supabaseSyncService.fetchDynamicProductKnowledge(query = messageText)
                if (dynamicKnowledgeList.isNotEmpty()) {
                    val primaryDynamic = dynamicKnowledgeList.first()
                    dynamicProductSnippet = primaryDynamic.toSystemPromptSnippet()

                    // Injecter les fiches produits dynamiques en tête de liste pour l'inférence
                    val dynamicEntities = dynamicKnowledgeList.map { it.toProductEntity() }
                    products = dynamicEntities + products
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BaileysService", "Erreur RAG dynamique Supabase: ${e.message}")
        }

        // 6. Execute Local AI Edge Inference with category-specific persona and directives if matched
        val dynamicCategoryList = allCategories.map { it.name }
        val strictCategoryPrompt = if (matchedCategory != null) {
            com.example.domain.commerce.DefaultCategoriesCatalog.buildStrictCommerceAgentPrompt(
                categoryName = matchedCategory.name,
                categoryDescription = matchedCategory.description ?: "",
                availableCategories = dynamicCategoryList
            )
        } else {
            ""
        }

        val baseSystemPrompt = buildString {
            if (!dynamicProductSnippet.isNullOrBlank()) {
                append(dynamicProductSnippet)
                append("\n\n")
            }
            if (strictCategoryPrompt.isNotBlank()) {
                append(strictCategoryPrompt)
                append("\n\n")
            } else if (matchedCategory != null && matchedCategory.aiAgentPrompt.isNotBlank()) {
                append(matchedCategory.aiAgentPrompt)
                append("\n\n")
            }
            append(selectedAgent.systemPrompt)
        }

        val effectiveAgent = selectedAgent.copy(
            name = if (matchedCategory != null && matchedCategory.aiAgentName.isNotBlank()) matchedCategory.aiAgentName else selectedAgent.name,
            systemPrompt = baseSystemPrompt,
            temperature = if (matchedCategory != null) matchedCategory.aiAgentTemperature.toFloat() else selectedAgent.temperature
        )

        val customerPhoneNum = senderJid.replace("@s.whatsapp.net", "").replace("@c.us", "")
        val allOrders = try {
            database.commerceDao().getAllOrdersList()
        } catch (e: Exception) {
            emptyList<com.example.data.local.entity.OrderEntity>()
        }

        val inferenceResult = AiEdgeQuantizerEngine.runAgentInference(
            agent = effectiveAgent,
            customerQuery = messageText,
            knowledgeSources = knowledgeSources,
            mcpTools = mcpTools,
            products = products,
            orders = allOrders,
            customerPhone = customerPhoneNum
        )

        // 6.5 GESTION DES COMMANDES & CONTINUITÉ DE SESSION WHATSAPP
        try {
            val isExplicitTracking = EdgeNeuralReasoningEngine.isExplicitOrderTrackingQuery(messageText)
            val isCoords = com.example.domain.commerce.OrderCustomerInfoParser.isDeliveryCoordinatesMessage(messageText)
            val isAck = com.example.domain.commerce.OrderCustomerInfoParser.isFollowUpAcknowledgment(messageText)
            val isStoreOrder = !isExplicitTracking && !isCoords && !isAck && EdgeNeuralReasoningEngine.isStorefrontOrderOrPurchase(messageText)

            var activeOrderId = existingSession?.orderId

            if (isStoreOrder) {
                // Création ou réactualisation de la pré-commande
                val existingPending = database.commerceDao().getLatestOrderForRemoteJid(senderJid)
                    ?.takeIf { it.status == "PENDING_CONFIRMATION" && !it.isCoordinatesCaptured }

                val orderId = existingPending?.id ?: "ord-${UUID.randomUUID().toString().take(8)}"
                val orderNum = existingPending?.orderNumber ?: "#CMD-${(1000..9999).random()}"

                val targetProduct = products.firstOrNull() ?: allProducts.firstOrNull { prod ->
                    prod.title.length >= 3 && messageText.lowercase(Locale.getDefault()).contains(prod.title.lowercase(Locale.getDefault()))
                }
                val orderProdName = targetProduct?.title ?: (Regex("""(?i)produit\s*:\s*([^\n\r,]+)""").find(messageText)?.groupValues?.get(1)?.trim() ?: "Produit Vitrine")
                val orderPrice = targetProduct?.sellingPrice ?: (Regex("""(?i)prix\s*:\s*([0-9.]+)""").find(messageText)?.groupValues?.get(1)?.toDoubleOrNull() ?: 110.0)

                val newTranscript = "Client: $messageText\nAgent: ${inferenceResult.replyText}"

                val newOrder = com.example.data.local.entity.OrderEntity(
                    id = orderId,
                    orderNumber = orderNum,
                    customerName = if (senderJid.contains("@")) "Client WhatsApp ($customerPhoneNum)" else "Client Vitrine",
                    customerPhone = customerPhoneNum,
                    deliveryAddress = "À confirmer lors du rappel téléphonique",
                    deliveryZone = "Maroc Standard",
                    productId = targetProduct?.id,
                    productName = orderProdName,
                    quantity = 1,
                    totalAmount = orderPrice,
                    currency = targetProduct?.currency ?: "MAD",
                    status = "PENDING_CONFIRMATION",
                    customerCallNotes = "Pré-commande issue de WhatsApp. En attente des coordonnées de livraison (nom, ville, adresse, tél).",
                    createdAt = existingPending?.createdAt ?: System.currentTimeMillis(),
                    customerCity = null,
                    conversationTranscript = newTranscript,
                    isCoordinatesCaptured = false,
                    remoteJid = senderJid
                )
                database.commerceDao().insertOrder(newOrder)
                activeOrderId = orderId

                // Enregistrer la session active
                agentDao.insertOrUpdateActiveSession(
                    com.example.data.local.entity.ActiveConversationSessionEntity(
                        remoteJid = senderJid,
                        agentId = selectedAgent.id,
                        categoryId = matchedCategory?.id,
                        orderId = orderId,
                        lastActivityTimestamp = System.currentTimeMillis(),
                        isCompleted = false
                    )
                )
            } else if (isCoords) {
                // Extraction et enregistrement des coordonnées réelles (Nom, Ville, Adresse, Téléphone)
                val coords = com.example.domain.commerce.OrderCustomerInfoParser.extractCoordinates(messageText)

                val pendingOrder = (activeOrderId?.let { database.commerceDao().findOrderByNumber(it) }
                    ?: database.commerceDao().getLatestOrderForRemoteJid(senderJid)?.takeIf { it.status == "PENDING_CONFIRMATION" }
                    ?: database.commerceDao().getLatestOrderForPhone(customerPhoneNum)?.takeIf { it.status == "PENDING_CONFIRMATION" })

                if (pendingOrder != null && coords != null) {
                    val prevTranscript = pendingOrder.conversationTranscript ?: ""
                    val updatedTranscript = if (prevTranscript.isNotBlank()) {
                        "$prevTranscript\nClient: $messageText\nAgent: ${inferenceResult.replyText}"
                    } else {
                        "Client: $messageText\nAgent: ${inferenceResult.replyText}"
                    }

                    val updatedOrder = pendingOrder.copy(
                        customerName = coords.name,
                        customerCity = coords.city,
                        customerPhone = coords.phone.ifBlank { pendingOrder.customerPhone },
                        deliveryAddress = "${coords.address}, ${coords.city}",
                        customerCallNotes = "Coordonnées complètes reçues : ${coords.name} | Ville : ${coords.city} | Adresse : ${coords.address} | Tél : ${coords.phone}. Rappel de confirmation à effectuer.",
                        isCoordinatesCaptured = true,
                        conversationTranscript = updatedTranscript
                    )
                    database.commerceDao().insertOrder(updatedOrder)

                    // Marquer la session de conversation comme terminée
                    agentDao.insertOrUpdateActiveSession(
                        com.example.data.local.entity.ActiveConversationSessionEntity(
                            remoteJid = senderJid,
                            agentId = selectedAgent.id,
                            categoryId = matchedCategory?.id ?: existingSession?.categoryId,
                            orderId = pendingOrder.id,
                            lastActivityTimestamp = System.currentTimeMillis(),
                            isCompleted = true
                        )
                    )
                }
            } else if (isAck) {
                // Simple remerciement ou accusé de réception : mettre à jour le transcript sans créer de nouvelle commande
                val latestOrder = database.commerceDao().getLatestOrderForRemoteJid(senderJid)
                if (latestOrder != null) {
                    val prevTranscript = latestOrder.conversationTranscript ?: ""
                    val updatedTranscript = "$prevTranscript\nClient: $messageText\nAgent: ${inferenceResult.replyText}"
                    database.commerceDao().insertOrder(latestOrder.copy(conversationTranscript = updatedTranscript))
                }
            } else if (existingSession != null && !existingSession.isCompleted) {
                // Conversation en cours : mettre à jour le timestamp d'activité et le transcript
                val orderToUpdate = activeOrderId?.let { database.commerceDao().findOrderByNumber(it) }
                    ?: database.commerceDao().getLatestOrderForRemoteJid(senderJid)
                if (orderToUpdate != null) {
                    val prevTranscript = orderToUpdate.conversationTranscript ?: ""
                    val updatedTranscript = "$prevTranscript\nClient: $messageText\nAgent: ${inferenceResult.replyText}"
                    database.commerceDao().insertOrder(orderToUpdate.copy(conversationTranscript = updatedTranscript))
                }
                agentDao.insertOrUpdateActiveSession(
                    existingSession.copy(lastActivityTimestamp = System.currentTimeMillis())
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("BaileysService", "Erreur gestion commande et session: ${e.message}")
        }

        // 6. Record agent response
        val toolsExecutedText = if (inferenceResult.toolCalls.isNotEmpty()) {
            inferenceResult.toolCalls.joinToString(" | ")
        } else null

        val responseMsg = WhatsAppMessageEntity(
            id = UUID.randomUUID().toString(),
            instanceId = instanceId,
            remoteJid = senderJid,
            senderName = selectedAgent.name,
            content = inferenceResult.replyText,
            isFromCustomer = false,
            timestamp = System.currentTimeMillis(),
            handledByAgentId = selectedAgent.id,
            handledByAgentName = selectedAgent.name,
            routingReason = routingReason,
            toolCallsExecuted = toolsExecutedText,
            latencyMs = inferenceResult.latencyMs
        )
        msgDao.insertMessage(responseMsg)
        agentDao.recordAgentResponse(selectedAgent.id, inferenceResult.latencyMs)

        _eventsFlow.emit(
            BaileysEvent(
                instanceId = instanceId,
                eventType = "messages.sent",
                payload = "Replied via ${selectedAgent.name} in ${inferenceResult.latencyMs}ms"
            )
        )

        return responseMsg
    }

    data class RoutingDecision(
        val agent: AgentEntity?,
        val reason: String,
        val matchedCategory: CategoryEntity? = null
    )

    /**
     * Determines which agent should handle the message according to:
     * 0. Category-based dedicated Agent (via product match, explicit header or category keywords)
     * 1. Assigned instance filter
     * 2. Keyword trigger matching (agent specific)
     * 3. Operational schedule (e.g. 08:00 - 19:00 vs night guard)
     * 4. Fallback agent
     */
    fun selectBestAgentForMessage(
        agents: List<AgentEntity>,
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        instanceId: String,
        messageText: String,
        activeSession: com.example.data.local.entity.ActiveConversationSessionEntity? = null
    ): RoutingDecision {
        val textLower = messageText.lowercase(Locale.getDefault())

        // -1. ACTIVE CONVERSATION SESSION CONTINUITY (Section 1, point 2 & Section 5)
        // If an active session is ongoing for this thread and client hasn't explicitly changed topic
        if (activeSession != null && activeSession.isSessionActive()) {
            val sessionAgent = agents.firstOrNull { it.id == activeSession.agentId && it.isActive }
                ?: agents.firstOrNull { it.id == activeSession.agentId }

            if (sessionAgent != null) {
                val isTopicChange = isClearTopicChange(messageText, categories, products, activeSession.categoryId)
                if (!isTopicChange) {
                    val sessionCategory = categories.firstOrNull { it.id == activeSession.categoryId }
                    return RoutingDecision(
                        agent = sessionAgent,
                        reason = "Maintien de l'agent de session (${sessionAgent.name}) pour la conversation en cours",
                        matchedCategory = sessionCategory
                    )
                }
            }
        }

        // 0. TOP PRIORITY: Category-based Smart Routing
        // Check if message corresponds to a specific Product or Category
        val matchedProduct = products.firstOrNull { prod ->
            val pTitle = prod.title.lowercase(Locale.getDefault())
            if (pTitle.length >= 3 && textLower.contains(pTitle)) true
            else {
                val words = pTitle.split(" ").map { it.trim() }.filter { it.length >= 4 }
                words.isNotEmpty() && words.any { textLower.contains(it) }
            }
        }

        var candidateCategory: CategoryEntity? = null
        if (matchedProduct != null && !matchedProduct.categoryId.isNullOrBlank()) {
            candidateCategory = categories.firstOrNull { it.id == matchedProduct.categoryId }
        }

        if (candidateCategory == null) {
            val router = com.example.domain.ai.CategoryAgentRouter(database)
            candidateCategory = router.detectCategoryFromMessage(messageText, categories)
        }

        if (candidateCategory == null) {
            candidateCategory = categories.firstOrNull { cat ->
                val catName = cat.name.lowercase(Locale.getDefault())
                val catSlug = cat.slug.lowercase(Locale.getDefault())
                val catDesc = cat.description.lowercase(Locale.getDefault())
                textLower.contains(catName) || textLower.contains(catSlug) ||
                        (catName.split(" ", "&", "-").any { w -> w.length >= 4 && textLower.contains(w) }) ||
                        (catDesc.isNotBlank() && catDesc.split(" ", ",", ";").any { w -> w.length >= 4 && textLower.contains(w) })
            }
        }

        if (candidateCategory != null && !candidateCategory.assignedAgentId.isNullOrBlank()) {
            val assignedAgent = agents.firstOrNull { it.id == candidateCategory.assignedAgentId && it.isActive }
                ?: agents.firstOrNull { it.id == candidateCategory.assignedAgentId }
            if (assignedAgent != null) {
                val reasonDetail = if (matchedProduct != null) {
                    "Agent dédié à la catégorie '${candidateCategory.name}' (détecté via produit '${matchedProduct.title}')"
                } else {
                    "Agent dédié à la catégorie '${candidateCategory.name}'"
                }
                return RoutingDecision(assignedAgent, reasonDetail, candidateCategory)
            }
        }

        // 1. Explicitly assigned to instance
        val explicitlyAssigned = agents.filter { it.isActive }.firstOrNull { agent ->
            val assignedList = agent.assignedInstanceIdsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            assignedList.contains(instanceId) && agent.assignedInstanceIdsCsv != "*"
        }
        if (explicitlyAssigned != null) {
            return RoutingDecision(explicitlyAssigned, "Agent assigné à cette instance (${explicitlyAssigned.name})", candidateCategory)
        }

        val eligibleAgents = agents.filter { agent ->
            agent.assignedInstanceIdsCsv == "*" || agent.assignedInstanceIdsCsv.contains(instanceId)
        }

        val candidateAgents = if (eligibleAgents.isNotEmpty()) {
            eligibleAgents
        } else {
            agents.filter { it.isActive }.ifEmpty { agents }
        }

        if (candidateAgents.isEmpty()) {
            return RoutingDecision(null, "No agent configured in application", candidateCategory)
        }

        // Si une catégorie a été détectée mais que son agent dédié n'est pas trouvé,
        // relayer vers l'agent commercial de secours sans laisser d'autres mots-clés écraser la catégorie
        if (candidateCategory != null) {
            val fallbackCategoryAgent = candidateAgents.firstOrNull { it.id == "agent-sales-02" }
                ?: candidateAgents.firstOrNull { it.role.equals("Commercial", ignoreCase = true) }
                ?: candidateAgents.firstOrNull { it.isFallback }
                ?: candidateAgents.firstOrNull()
            return RoutingDecision(
                fallbackCategoryAgent,
                "Agent commercial de secours pour catégorie '${candidateCategory.name}'",
                candidateCategory
            )
        }

        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // 2. Keyword-based matching priority (spécifique à chaque agent)
        for (agent in candidateAgents) {
            if (agent.activationMode == "KEYWORDS" || agent.keywordsCsv.isNotBlank()) {
                val keywords = agent.keywordsCsv.split(",").map { it.trim().lowercase(Locale.getDefault()) }.filter { it.isNotBlank() && it != "*" }
                val matchedKeyword = keywords.firstOrNull { kw -> textLower.contains(kw) }
                if (matchedKeyword != null) {
                    return RoutingDecision(agent, "Keyword trigger match: '$matchedKeyword'", candidateCategory)
                }
            }
        }

        // 3. Schedule-based matching
        for (agent in candidateAgents) {
            if (agent.activationMode == "SCHEDULE") {
                if (isTimeInRange(currentTimeStr, agent.scheduleStart, agent.scheduleEnd)) {
                    return RoutingDecision(agent, "Scheduled active slot (${agent.scheduleStart} - ${agent.scheduleEnd})", candidateCategory)
                }
            }
        }

        // 4. "ALWAYS" active agent
        val alwaysActive = candidateAgents.firstOrNull { it.activationMode == "ALWAYS" }
        if (alwaysActive != null) {
            return RoutingDecision(alwaysActive, "Default always-active responder", candidateCategory)
        }

        // 5. Fallback agent
        val fallback = candidateAgents.firstOrNull { it.isFallback } ?: candidateAgents.firstOrNull()
        return RoutingDecision(fallback, "General fallback agent", candidateCategory)
    }

    private fun isTimeInRange(current: String, start: String, end: String): Boolean {
        return try {
            if (start <= end) {
                current >= start && current <= end
            } else {
                // Crosses midnight (e.g. 20:00 to 08:00)
                current >= start || current <= end
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun isClearTopicChange(
        messageText: String,
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        currentCategoryId: String?
    ): Boolean {
        val textLower = messageText.lowercase(Locale.getDefault())

        // Delivery coordinates, acknowledgments, or very short messages are NEVER a topic change
        if (com.example.domain.commerce.OrderCustomerInfoParser.isDeliveryCoordinatesMessage(messageText) ||
            com.example.domain.commerce.OrderCustomerInfoParser.isFollowUpAcknowledgment(messageText) ||
            textLower.length < 8
        ) {
            return false
        }

        // Explicit mention of another category
        val otherCategory = categories.firstOrNull { cat ->
            cat.id != currentCategoryId && (
                textLower.contains(cat.name.lowercase(Locale.getDefault())) ||
                textLower.contains(cat.slug.lowercase(Locale.getDefault()))
            )
        }
        if (otherCategory != null) return true

        // Explicit mention of a product belonging to another category
        val otherProduct = products.firstOrNull { prod ->
            prod.categoryId != null && prod.categoryId != currentCategoryId &&
            prod.title.length >= 4 && textLower.contains(prod.title.lowercase(Locale.getDefault()))
        }
        return otherProduct != null
    }

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val p1 = (1..4).map { chars.random() }.joinToString("")
        val p2 = (1..4).map { chars.random() }.joinToString("")
        return "$p1-$p2"
    }
}
