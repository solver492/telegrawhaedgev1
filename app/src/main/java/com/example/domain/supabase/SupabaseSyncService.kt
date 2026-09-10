package com.example.domain.supabase

import android.content.Context
import android.util.Log
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ProductMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed class SupabaseSyncResult {
    data class Success(val message: String, val remoteUrl: String? = null) : SupabaseSyncResult()
    data class Error(val error: String) : SupabaseSyncResult()
}

/**
 * Service d'intégration réelle avec Supabase (PostgreSQL REST + Storage API).
 * Permet de synchroniser les fiches produits vers la table `products`
 * et d'uploader les médias locaux vers le bucket `product-media`.
 */
class SupabaseSyncService(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SupabaseSync"
        private const val STORAGE_BUCKET = "product-media"
        const val DEFAULT_SUPABASE_URL = "https://nfoefhwmgjatbqyibclp.supabase.co"
        const val DEFAULT_SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5mb2VmaHdtZ2phdGJxeWliY2xwIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODM4NDAxNywiZXhwIjoyMTAzOTYwMDE3fQ.y3ycIHJDwu1FuJH5FE17wX-zOuVZUUBIztLEaNmVLhg"
    }

    /**
     * Publie ou met à jour un produit sur Supabase (REST API upsert)
     * et upload les médias locaux vers Supabase Storage si nécessaire.
     */
    suspend fun publishProduct(
        product: ProductEntity,
        mediaList: List<ProductMediaEntity>,
        categoryName: String? = null,
        supplierName: String? = null,
        supabaseUrl: String,
        supabaseAnonKey: String
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        val cleanUrl = (if (supabaseUrl.isNotBlank()) supabaseUrl else DEFAULT_SUPABASE_URL).trimEnd('/')
        val activeKey = (if (supabaseAnonKey.isNotBlank()) supabaseAnonKey else DEFAULT_SERVICE_ROLE_KEY).trim()

        Log.d("SupabasePublish", "=== DÉBUT PUBLICATION ===")
        Log.d("SupabasePublish", "Produit: ${product.title}")
        Log.d("SupabasePublish", "ID local: ${product.id}")
        Log.d("SupabasePublish", "category_id brut: ${product.categoryId}")
        Log.d("SupabasePublish", "categoryName: $categoryName")

        try {
            // 1. Upload des médias locaux vers Supabase Storage si nécessaire
            var primaryRemoteImageUrl = product.primaryImageUrl
            val uploadedMediaUrls = mutableListOf<String>()

            for (media in mediaList) {
                val candidateFile = findLocalFile(media.localPath ?: media.mediaUrl)
                if (candidateFile != null && candidateFile.exists() && candidateFile.length() > 0) {
                    val isVideo = com.example.util.ProductMediaManager.isVideoUrlOrPath(candidateFile.name) || media.mediaType == "video"
                    if (isVideo) {
                        // 1. Génération et téléversement de la miniature vidéo JPEG
                        val thumbFile = com.example.util.ProductMediaManager.generateVideoThumbnail(candidateFile)
                        var thumbUrl: String? = null
                        if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0) {
                            thumbUrl = uploadFileToStorage(
                                file = thumbFile,
                                cleanUrl = cleanUrl,
                                anonKey = activeKey,
                                productId = product.id
                            )
                            try { thumbFile.delete() } catch (_: Exception) {}
                        }

                        // 2. Téléversement de la vidéo
                        val videoUploadResult = uploadFileToStorage(
                            file = candidateFile,
                            cleanUrl = cleanUrl,
                            anonKey = activeKey,
                            productId = product.id
                        )

                        // 3. Mettre la miniature en premier pour l'affichage de vitrine web
                        if (thumbUrl != null && !uploadedMediaUrls.contains(thumbUrl)) {
                            uploadedMediaUrls.add(thumbUrl)
                            if (primaryRemoteImageUrl.isNullOrBlank() || com.example.util.ProductMediaManager.isVideoUrlOrPath(primaryRemoteImageUrl)) {
                                primaryRemoteImageUrl = thumbUrl
                            }
                        }
                        if (videoUploadResult != null && !uploadedMediaUrls.contains(videoUploadResult)) {
                            uploadedMediaUrls.add(videoUploadResult)
                        }
                    } else {
                        val uploadResult = uploadFileToStorage(
                            file = candidateFile,
                            cleanUrl = cleanUrl,
                            anonKey = activeKey,
                            productId = product.id
                        )
                        if (uploadResult != null) {
                            uploadedMediaUrls.add(uploadResult)
                            if (primaryRemoteImageUrl.isNullOrBlank() || primaryRemoteImageUrl?.startsWith("/") == true || primaryRemoteImageUrl?.startsWith("file://") == true) {
                                primaryRemoteImageUrl = uploadResult
                            }
                        } else if (!media.mediaUrl.startsWith("/") && !media.mediaUrl.startsWith("file://")) {
                            uploadedMediaUrls.add(media.mediaUrl)
                        }
                    }
                } else if (!media.mediaUrl.startsWith("/") && !media.mediaUrl.startsWith("file://")) {
                    uploadedMediaUrls.add(media.mediaUrl)
                }
            }

            // 2. Si l'image principale elle-même est locale
            val primaryFile = findLocalFile(primaryRemoteImageUrl)
            if (primaryFile != null && primaryFile.exists() && primaryFile.length() > 0) {
                val isVideo = com.example.util.ProductMediaManager.isVideoUrlOrPath(primaryFile.name)
                if (isVideo) {
                    val thumbFile = com.example.util.ProductMediaManager.generateVideoThumbnail(primaryFile)
                    var thumbUrl: String? = null
                    if (thumbFile != null && thumbFile.exists() && thumbFile.length() > 0) {
                        thumbUrl = uploadFileToStorage(
                            file = thumbFile,
                            cleanUrl = cleanUrl,
                            anonKey = activeKey,
                            productId = product.id
                        )
                        try { thumbFile.delete() } catch (_: Exception) {}
                    }
                    val uploadedVid = uploadFileToStorage(
                        file = primaryFile,
                        cleanUrl = cleanUrl,
                        anonKey = activeKey,
                        productId = product.id
                    )
                    if (thumbUrl != null) {
                        primaryRemoteImageUrl = thumbUrl
                        if (!uploadedMediaUrls.contains(thumbUrl)) {
                            uploadedMediaUrls.add(0, thumbUrl)
                        }
                    }
                    if (uploadedVid != null && !uploadedMediaUrls.contains(uploadedVid)) {
                        uploadedMediaUrls.add(uploadedVid)
                    }
                } else {
                    val uploaded = uploadFileToStorage(
                        file = primaryFile,
                        cleanUrl = cleanUrl,
                        anonKey = activeKey,
                        productId = product.id
                    )
                    if (uploaded != null) {
                        primaryRemoteImageUrl = uploaded
                        if (!uploadedMediaUrls.contains(uploaded)) {
                            uploadedMediaUrls.add(0, uploaded)
                        }
                    }
                }
            } else if (!primaryRemoteImageUrl.isNullOrBlank() && !primaryRemoteImageUrl.startsWith("/") && !primaryRemoteImageUrl.startsWith("file://")) {
                if (!uploadedMediaUrls.contains(primaryRemoteImageUrl)) {
                    uploadedMediaUrls.add(0, primaryRemoteImageUrl)
                }
            }

            // 3. Inspection des colonnes réelles de la table 'products' pour éviter tout rejet PostgREST 400
            val tableProps = getProductsTableProperties(cleanUrl, activeKey)
            val cols = tableProps?.keys ?: emptySet()
            if (cols.isNotEmpty()) {
                Log.d("SupabasePublish", "Colonnes Supabase détectées: $cols")
            }

            // Validation de category_id: uniquement envoyer si c'est un UUID valide pour ne pas déclencher 22P02
            val isUuid = product.categoryId != null && product.categoryId!!.matches(
                Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            )
            if (!isUuid && !product.categoryId.isNullOrBlank()) {
                Log.w("SupabasePublish", "⚠️ category_id '${product.categoryId}' n'est pas un UUID standard: non inclus dans category_id pour éviter 22P02")
            }

            val cleanName = product.title.trim().ifEmpty { "Article" }
            val wholesalePrice = (product.purchasePrice ?: 0.0).coerceAtLeast(0.0)
            val suggestedPrice = (product.sellingPrice ?: product.purchasePrice ?: 0.0).coerceAtLeast(0.0)
            val stockUnits = if (product.stockQuantity > 0) product.stockQuantity else 100

            // Préparer les images au format JSONB
            val imagesJsonArray = org.json.JSONArray().apply {
                uploadedMediaUrls.filter { it.isNotBlank() }.forEach { put(it) }
            }
            // Sélection intelligente de image_url : priorité absolue à une image fixe / miniature JPG (jamais un MP4)
            val mainImageUrl = uploadedMediaUrls.firstOrNull { it.isNotBlank() && !com.example.util.ProductMediaManager.isVideoUrlOrPath(it) }
                ?: (if (!primaryRemoteImageUrl.isNullOrBlank() && !com.example.util.ProductMediaManager.isVideoUrlOrPath(primaryRemoteImageUrl)) primaryRemoteImageUrl else null)
                ?: uploadedMediaUrls.firstOrNull { it.isNotBlank() }
                ?: primaryRemoteImageUrl

            val payload = JSONObject().apply {
                // CHAMPS FONDAMENTAUX OBLIGATOIRES
                put("name", cleanName)
                put("description", product.description ?: "")
                put("wholesale_price", wholesalePrice)
                put("suggested_sale_price", suggestedPrice)
                put("delivery_cost", 0.0)
                put("active", true)
                put("is_published_to_website", true)
                put("status", "PUBLISHED")
                put("stock", stockUnits)

                // MÉDIAS
                if (!mainImageUrl.isNullOrBlank()) {
                    put("image_url", mainImageUrl)
                }
                put("images", imagesJsonArray)

                // GESTION INTELLIGENTE DES CATÉGORIES (Compatible avec 'category' et 'category_name')
                val resolvedCategory = categoryName ?: "Non catégorisé"
                if (cols.isEmpty() || cols.contains("category_name")) {
                    put("category_name", resolvedCategory)
                }
                if (cols.isEmpty() || cols.contains("category")) {
                    put("category", resolvedCategory)
                }
                if (isUuid && (cols.isEmpty() || cols.contains("category_id"))) {
                    put("category_id", product.categoryId)
                }

                // CHAMPS TELEGRAM & TRAÇABILITÉ
                put("source_message_id", product.id)
                if (!product.sourceChannelTitle.isNullOrBlank()) {
                    if (cols.isEmpty() || cols.contains("source_channel_id")) {
                        put("source_channel_id", product.sourceChannelTitle)
                    }
                }

                // COLONNES OPTIONNELLES: ajoutées SEULEMENT si elles existent dans le schéma Supabase
                if (cols.contains("supplier_name")) {
                    put("supplier_name", supplierName ?: (product.sourceChannelTitle ?: "Akkipi"))
                }
                if (cols.contains("hasVideo")) {
                    put("hasVideo", product.hasVideo || uploadedMediaUrls.any { com.example.util.ProductMediaManager.isVideoUrlOrPath(it) })
                }
                if (cols.contains("lot_quantity")) {
                    put("lot_quantity", product.lotQuantity ?: 1)
                }
                if (cols.contains("lot_label")) {
                    put("lot_label", product.lotLabel?.ifBlank { "pièce" } ?: "pièce")
                }
                if (cols.contains("is_lot")) {
                    put("is_lot", product.isLotOrPackPrice)
                }
                if (cols.contains("lot_total_price") && product.lotTotalPrice != null) {
                    put("lot_total_price", product.lotTotalPrice)
                }
                if (cols.contains("lot_unit_price") && product.lotUnitPriceEstimate != null) {
                    put("lot_unit_price", product.lotUnitPriceEstimate)
                }
            }

            Log.d("SupabasePublish", "📦 Payload JSON: $payload")

            var responseCode = 0
            var responseBody = ""

            // Vérification si le produit existe déjà via source_message_id
            val checkRequest = Request.Builder()
                .url("$cleanUrl/rest/v1/products?source_message_id=eq.${product.id}&select=id")
                .addHeader("apikey", activeKey)
                .addHeader("Authorization", "Bearer $activeKey")
                .get()
                .build()

            val checkResp = okHttpClient.newCall(checkRequest).execute()
            val checkBody = checkResp.body?.string() ?: ""
            val existingList = try { org.json.JSONArray(checkBody) } catch (_: Exception) { org.json.JSONArray() }

            if (existingList.length() > 0) {
                // Mise à jour (PATCH)
                Log.d("SupabasePublish", "🔄 Mise à jour du produit existant (source_message_id = ${product.id})...")
                val patchRequest = Request.Builder()
                    .url("$cleanUrl/rest/v1/products?source_message_id=eq.${product.id}")
                    .addHeader("apikey", activeKey)
                    .addHeader("Authorization", "Bearer $activeKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val patchResp = okHttpClient.newCall(patchRequest).execute()
                responseCode = patchResp.code
                responseBody = patchResp.body?.string() ?: ""
            } else {
                // Insertion (POST)
                Log.d("SupabasePublish", "🔄 Insertion d'un nouveau produit sur Supabase...")
                val postRequest = Request.Builder()
                    .url("$cleanUrl/rest/v1/products")
                    .addHeader("apikey", activeKey)
                    .addHeader("Authorization", "Bearer $activeKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val postResp = okHttpClient.newCall(postRequest).execute()
                responseCode = postResp.code
                responseBody = postResp.body?.string() ?: ""

                // Si conflit d'unicité, tenter un PATCH de secours
                if (responseCode == 409) {
                    Log.w("SupabasePublish", "⚠️ Conflit 409, bascule vers mise à jour PATCH...")
                    val fallbackPatch = Request.Builder()
                        .url("$cleanUrl/rest/v1/products?source_message_id=eq.${product.id}")
                        .addHeader("apikey", activeKey)
                        .addHeader("Authorization", "Bearer $activeKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    val fallbackResp = okHttpClient.newCall(fallbackPatch).execute()
                    responseCode = fallbackResp.code
                    responseBody = fallbackResp.body?.string() ?: ""
                }
            }

            if (responseCode in 200..299) {
                Log.d("SupabasePublish", "✅ PUBLICATION RÉUSSIE! ($responseCode) : $responseBody")
                Log.d("SupabasePublish", "=== FIN PUBLICATION ===")
                return@withContext SupabaseSyncResult.Success(
                    message = "Produit « ${product.title} » publié avec succès sur Supabase !",
                    remoteUrl = primaryRemoteImageUrl
                )
            } else if (responseCode == 401) {
                Log.e("SupabasePublish", "❌ ERREUR 401 Non autorisé")
                val hint = "Clé API non autorisée ou expirée. Utilisez la service_role key pour contourner les restrictions RLS."
                return@withContext SupabaseSyncResult.Error("Supabase (401 Non autorisé) : $hint")
            } else if (responseCode == 404 || responseBody.contains("relation \"public.products\" does not exist")) {
                Log.e("SupabasePublish", "❌ ERREUR 404 Table introuvable")
                return@withContext SupabaseSyncResult.Error("Table 'products' introuvable dans Supabase. Veuillez exécuter le script SQL dans la console Supabase.")
            } else {
                Log.e("SupabasePublish", "❌ ERREUR API Supabase ($responseCode): $responseBody")
                Log.e("SupabasePublish", "=== FIN PUBLICATION (ÉCHEC) ===")
                return@withContext SupabaseSyncResult.Error(
                    "Supabase API ($responseCode) : ${responseBody.take(150)}"
                )
            }
        } catch (e: Exception) {
            Log.e("SupabasePublish", "❌ EXCEPTION LORS DE LA PUBLICATION", e)
            Log.e("SupabasePublish", "=== FIN PUBLICATION (ÉCHEC) ===")
            return@withContext SupabaseSyncResult.Error(
                "Échec connexion Supabase : ${e.localizedMessage ?: e.message}"
            )
        }
    }

    /**
     * Teste la connexion à l'API Supabase et vérifie l'accès à la table 'products'.
     */
    suspend fun testConnection(
        supabaseUrl: String,
        supabaseAnonKey: String
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return@withContext SupabaseSyncResult.Error("URL ou Clé API Supabase non renseignée")
        }

        val cleanUrl = supabaseUrl.trimEnd('/')
        try {
            val request = Request.Builder()
                .url("$cleanUrl/rest/v1/")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $supabaseAnonKey")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            when (code) {
                in 200..299 -> {
                    // Vérifier si la table products existe
                    val tableRequest = Request.Builder()
                        .url("$cleanUrl/rest/v1/products?limit=1")
                        .addHeader("apikey", supabaseAnonKey)
                        .addHeader("Authorization", "Bearer $supabaseAnonKey")
                        .get()
                        .build()
                    val tableResponse = okHttpClient.newCall(tableRequest).execute()
                    val tableCode = tableResponse.code
                    val tableBody = tableResponse.body?.string() ?: ""

                    if (tableCode in 200..299) {
                        SupabaseSyncResult.Success("✅ Connexion Supabase réussie ! Table 'products' prête et accessible.")
                    } else if (tableCode == 404 || tableBody.contains("does not exist") || tableBody.contains("PGRST204") || tableBody.contains("PGRST200")) {
                        SupabaseSyncResult.Success("✅ Connexion API Supabase valide ! Note : la table 'products' doit être créée via l'éditeur SQL.")
                    } else {
                        SupabaseSyncResult.Success("✅ Connexion API Supabase validée.")
                    }
                }
                401 -> {
                    val isJwtSecret = supabaseAnonKey.length in 80..95 && supabaseAnonKey.endsWith("==")
                    val hint = if (isJwtSecret) {
                        "La clé entrée est le JWT Secret (88 caractères). Pour la connexion client, copiez la clé 'anon public' (commençant par eyJ... ou sb_publishable_) depuis Dashboard Supabase > Settings > API > Project API keys."
                    } else {
                        "Clé API Supabase invalide (401). Double-vérifiez votre clé anon public dans Supabase."
                    }
                    SupabaseSyncResult.Error("Erreur 401 : $hint")
                }
                404 -> SupabaseSyncResult.Error("Erreur 404 : Projet Supabase introuvable à cette URL ($cleanUrl)")
                else -> SupabaseSyncResult.Error("Supabase ($code) : ${body.take(120)}")
            }
        } catch (e: Exception) {
            SupabaseSyncResult.Error("Échec connexion : ${e.localizedMessage ?: e.message}")
        }
    }

    /**
     * Script SQL prêt à l'emploi pour initialiser ou mettre à niveau Supabase pour la vitrine
     */
    fun getSupabaseSchemaSql(): String {
        return """
            -- 1. Table des produits pour publication vitrine e-commerce
            CREATE TABLE IF NOT EXISTS public.products (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT,
                selling_price NUMERIC DEFAULT 0,
                purchase_price NUMERIC DEFAULT 0,
                currency TEXT DEFAULT 'MAD',
                stock_quantity INTEGER DEFAULT 0,
                primary_image_url TEXT,
                images JSONB DEFAULT '[]'::jsonb,
                category_name TEXT,
                category_id TEXT,
                supplier_name TEXT,
                supplier_id TEXT,
                source_channel_title TEXT,
                is_lot BOOLEAN DEFAULT FALSE,
                lot_quantity INTEGER,
                lot_total_price NUMERIC,
                lot_unit_price NUMERIC,
                lot_label TEXT,
                status TEXT DEFAULT 'PUBLISHED',
                is_published_to_website BOOLEAN DEFAULT TRUE,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                updated_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
            );

            -- 2. Mise à niveau des colonnes si la table existait déjà
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS images JSONB DEFAULT '[]'::jsonb;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS category_name TEXT;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS supplier_name TEXT;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS is_lot BOOLEAN DEFAULT FALSE;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS lot_quantity INTEGER;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS lot_total_price NUMERIC;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS lot_unit_price NUMERIC;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS lot_label TEXT;
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS status TEXT DEFAULT 'PUBLISHED';
            ALTER TABLE public.products ADD COLUMN IF NOT EXISTS is_published_to_website BOOLEAN DEFAULT TRUE;

            -- 3. Sécurité RLS et accès
            ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

            DROP POLICY IF EXISTS "Lecture publique des produits" ON public.products;
            DROP POLICY IF EXISTS "Gestion des produits" ON public.products;
            DROP POLICY IF EXISTS "products_select_policy" ON public.products;
            DROP POLICY IF EXISTS "products_insert_policy" ON public.products;
            DROP POLICY IF EXISTS "products_update_policy" ON public.products;
            DROP POLICY IF EXISTS "products_delete_policy" ON public.products;

            CREATE POLICY "products_select_policy" ON public.products FOR SELECT USING (true);
            CREATE POLICY "products_insert_policy" ON public.products FOR INSERT WITH CHECK (true);
            CREATE POLICY "products_update_policy" ON public.products FOR UPDATE USING (true) WITH CHECK (true);
            CREATE POLICY "products_delete_policy" ON public.products FOR DELETE USING (true);

            -- 4. Bucket de stockage pour les photos et vidéos
            INSERT INTO storage.buckets (id, name, public)
            VALUES ('product-media', 'product-media', true)
            ON CONFLICT (id) DO NOTHING;

            DROP POLICY IF EXISTS "Accès public médias" ON storage.objects;
            CREATE POLICY "Accès public médias" ON storage.objects
                FOR SELECT TO public USING (bucket_id = 'product-media');

            DROP POLICY IF EXISTS "Upload médias" ON storage.objects;
            CREATE POLICY "Upload médias" ON storage.objects
                FOR INSERT TO anon, authenticated WITH CHECK (bucket_id = 'product-media');

            DROP POLICY IF EXISTS "Modification médias" ON storage.objects;
            CREATE POLICY "Modification médias" ON storage.objects
                FOR UPDATE TO anon, authenticated USING (bucket_id = 'product-media');

            DROP POLICY IF EXISTS "Suppression médias" ON storage.objects;
            CREATE POLICY "Suppression médias" ON storage.objects
                FOR DELETE TO anon, authenticated USING (bucket_id = 'product-media');
        """.trimIndent()
    }

    /**
     * Inspecte dynamiquement les définitions OpenAPI de Supabase pour la table 'products'
     */
    private fun getProductsTableProperties(cleanUrl: String, apiKey: String): Map<String, JSONObject>? {
        return try {
            val req = Request.Builder()
                .url("$cleanUrl/rest/v1/")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                val root = JSONObject(body)
                val defs = root.optJSONObject("definitions")
                val prodDef = defs?.optJSONObject("products")
                val props = prodDef?.optJSONObject("properties") ?: return null
                val result = mutableMapOf<String, JSONObject>()
                val keys = props.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = props.getJSONObject(key)
                }
                result
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur lecture schéma OpenAPI: ${e.message}")
            null
        }
    }

    /**
     * Retire un produit du site en mettant à jour son statut sur Supabase
     */
    suspend fun unpublishProduct(
        product: ProductEntity,
        supabaseUrl: String,
        supabaseAnonKey: String
    ): SupabaseSyncResult = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || supabaseAnonKey.isBlank()) {
            return@withContext SupabaseSyncResult.Error(
                "Configuration Supabase manquante dans Paramètres"
            )
        }

        val cleanUrl = supabaseUrl.trimEnd('/')

        try {
            val props = getProductsTableProperties(cleanUrl, supabaseAnonKey)
            val cols = props?.keys ?: emptySet()

            val updateJson = JSONObject().apply {
                if (cols.isEmpty() || cols.contains("status")) put("status", "VALIDATED")
                if (cols.isEmpty() || cols.contains("is_published_to_website")) put("is_published_to_website", false)
                if (cols.contains("active")) put("active", false)
                if (cols.contains("updated_at")) put("updated_at", System.currentTimeMillis())
            }

            val postgrestUrl = if (cols.contains("source_message_id")) {
                "$cleanUrl/rest/v1/products?source_message_id=eq.${product.id}"
            } else {
                "$cleanUrl/rest/v1/products?id=eq.${product.id}"
            }
            val requestBody = updateJson.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(postgrestUrl)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $supabaseAnonKey")
                .addHeader("Content-Type", "application/json")
                .patch(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code in 200..299) {
                Log.d(TAG, "Produit ${product.id} retiré du site sur Supabase")
                return@withContext SupabaseSyncResult.Success("Produit retiré du site avec succès")
            } else {
                return@withContext SupabaseSyncResult.Error("Supabase API ($code) : ${body.take(150)}")
            }
        } catch (e: Exception) {
            return@withContext SupabaseSyncResult.Error("Échec connexion : ${e.localizedMessage}")
        }
    }

    /**
     * Upload d'un fichier local vers Supabase Storage
     * Renvoie l'URL publique ou signée du fichier, ou null si échec.
     */
    private fun uploadFileToStorage(
        file: File,
        cleanUrl: String,
        anonKey: String,
        productId: String
    ): String? {
        try {
            val fileName = "${productId}_${file.name.replace(" ", "_")}"
            val storageUrl = "$cleanUrl/storage/v1/object/$STORAGE_BUCKET/$fileName"

            val contentType = when {
                file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) -> "image/jpeg"
                file.name.endsWith(".png", true) -> "image/png"
                file.name.endsWith(".webp", true) -> "image/webp"
                file.name.endsWith(".gif", true) -> "image/gif"
                file.name.endsWith(".mp4", true) -> "video/mp4"
                file.name.endsWith(".webm", true) -> "video/webm"
                file.name.endsWith(".mov", true) -> "video/quicktime"
                file.name.endsWith(".avi", true) -> "video/x-msvideo"
                file.name.endsWith(".mkv", true) -> "video/x-matroska"
                file.name.endsWith(".ogg", true) -> "video/ogg"
                file.name.endsWith(".3gp", true) -> "video/3gpp"
                else -> "application/octet-stream"
            }

            val requestBody = file.asRequestBody(contentType.toMediaTypeOrNull())

            val request = Request.Builder()
                .url(storageUrl)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Content-Type", contentType)
                .addHeader("x-upsert", "true")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.code in 200..299) {
                // Public URL
                val publicUrl = "$cleanUrl/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
                Log.d(TAG, "Fichier uploadé avec succès sur Supabase Storage: $publicUrl")
                return publicUrl
            } else {
                Log.w(TAG, "Échec upload storage (${response.code}): ${response.body?.string()?.take(100)}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur upload média: ${e.message}")
        }
        return null
    }

    private fun findLocalFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val clean = if (path.startsWith("file://")) path.removePrefix("file://") else path
        val file = File(clean)
        return if (file.exists()) file else null
    }

    /**
     * Synchronise les catégories avec la table Supabase `categories` (ou `public.categories`).
     * Récupère les catégories distantes, ou injecte le catalogue par défaut (12 catégories) si la table est vide.
     */
    suspend fun syncCategoriesWithSupabase(
        supabaseUrl: String,
        supabaseAnonKey: String
    ): Result<List<com.example.data.local.entity.CategoryEntity>> = withContext(Dispatchers.IO) {
        val cleanUrl = supabaseUrl.trim().removeSuffix("/")
        val anonKey = supabaseAnonKey.trim()
        if (cleanUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.failure(Exception("URL Supabase ou clé API manquante"))
        }

        try {
            val endpoint = "$cleanUrl/rest/v1/categories?select=*&order=display_order.asc"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.code in 200..299 && body.isNotBlank()) {
                val jsonArray = org.json.JSONArray(body)
                val list = mutableListOf<com.example.data.local.entity.CategoryEntity>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val name = obj.optString("name", "Catégorie")
                    val nameAr = obj.optString("name_ar", null)
                    val slug = obj.optString("slug", name.lowercase().replace(" ", "-"))
                    val parentId = obj.optString("parent_id", null)
                    val desc = obj.optString("description", "")
                    val icon = obj.optString("icon", "📦")
                    val displayOrder = obj.optInt("display_order", i + 1)
                    val agentName = obj.optString("ai_agent_name", "Agent $name")
                    val agentPrompt = obj.optString("ai_agent_prompt", "")
                    val agentTemp = obj.optDouble("ai_agent_temperature", 0.7)

                    list.add(
                        com.example.data.local.entity.CategoryEntity(
                            id = id,
                            name = name,
                            nameAr = if (nameAr == "null") null else nameAr,
                            slug = slug,
                            parentId = if (parentId == "null") null else parentId,
                            description = desc,
                            icon = icon,
                            displayOrder = displayOrder,
                            aiAgentName = agentName,
                            aiAgentPrompt = agentPrompt,
                            aiAgentTemperature = agentTemp,
                            isActive = true
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    return@withContext Result.success(list)
                }
            }

            // Si table vide ou introuvable, injecter le catalogue par défaut (12 catégories e-commerce)
            val defaultList = com.example.domain.commerce.DefaultCategoriesCatalog.ALL_DEFAULT_CATEGORIES
            seedCategoriesToSupabase(cleanUrl, anonKey, defaultList)
            Result.success(defaultList)
        } catch (e: Exception) {
            Log.w(TAG, "Erreur sync categories Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Publie une liste de catégories vers Supabase
     */
    suspend fun seedCategoriesToSupabase(
        cleanUrl: String,
        anonKey: String,
        categories: List<com.example.data.local.entity.CategoryEntity>
    ) = withContext(Dispatchers.IO) {
        try {
            val endpoint = "$cleanUrl/rest/v1/categories"
            val array = org.json.JSONArray()
            for (cat in categories) {
                val obj = JSONObject().apply {
                    put("id", cat.id)
                    put("name", cat.name)
                    if (cat.nameAr != null) put("name_ar", cat.nameAr)
                    put("slug", cat.slug)
                    if (cat.parentId != null) put("parent_id", cat.parentId)
                    put("description", cat.description)
                    if (cat.icon != null) put("icon", cat.icon)
                    put("display_order", cat.displayOrder)
                    put("ai_agent_name", cat.aiAgentName)
                    put("ai_agent_prompt", cat.aiAgentPrompt)
                    put("ai_agent_temperature", cat.aiAgentTemperature)
                    put("is_active", cat.isActive)
                }
                array.put(obj)
            }

            val body = array.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build()

            val res = okHttpClient.newCall(request).execute()
            Log.d(TAG, "Seeding categories Supabase status: ${res.code}")
        } catch (e: Exception) {
            Log.w(TAG, "Erreur seeding categories Supabase: ${e.message}")
        }
    }
}
