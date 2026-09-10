# Architecture & Structure Complète des Bases de Données (Locale & Supabase)

Ce document détaille l'architecture intégrale des bases de données de l'application **AI Edge WhatsApp & Commerce Engine**, les modèles de données, ainsi que la manière dont l'application communique, synchronise et traite les informations entre le stockage local sur l'appareil et le cloud Supabase.

---

## 1. Vue d'Ensemble de l'Architecture Hybride (Local-First + Cloud Vitrine)

L'application repose sur un paradigme **Offline-First / Edge-First** :

```
┌────────────────────────────────────────────────────────────────────────┐
│                        APPAREIL ANDROID (EDGE)                         │
│                                                                        │
│   ┌─────────────────────┐             ┌────────────────────────────┐   │
│   │ Telethon (Telegram) │             │     Moteur IA & NPU        │   │
│   │   Passerelle :8088  │             │   Agents LLM / RAG local   │   │
│   └──────────┬──────────┘             └─────────────┬──────────────┘   │
│              │                                      │                  │
│              ▼                                      ▼                  │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                 MainViewModel & UseCases                       │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
│                                   │                                    │
│                 CRUD Local        ▼                                    │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │             Android Room Database (SQLite local)               │   │
│   │  Table: ecommerce_products, telegram_messages, ai_agents,     │   │
│   │         orders, whatsapp_messages, settings, etc.              │   │
│   └───────────────────────────────┬────────────────────────────────┘   │
└───────────────────────────────────┼────────────────────────────────────┘
                                    │
                         REST API   │  Upload Médias / Multi-part
                      (PostgREST)   │  Storage Bucket 'product-media'
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         SUPABASE CLOUD                                 │
│                                                                        │
│   ┌────────────────────────┐            ┌──────────────────────────┐   │
│   │  PostgreSQL Database   │            │     Storage Buckets      │   │
│   │  Table: public.products│            │   Bucket: product-media  │   │
│   │  RLS: policies actives │            │   (Photos / Catalogues)  │   │
│   └────────────────────────┘            └──────────────────────────┘   │
│                                                                        │
│              ▲                                                         │
│              │ Accès Vitrine Public / Clients                          │
│   ┌──────────┴──────────────────────────┐                              │
│   │  Site E-Commerce / Vitrine Web      │                              │
│   └─────────────────────────────────────┘                              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Base de Données Locale : Android Room (SQLite)

La base locale s'intitule **`ai_edge_whatsapp_db`**. Elle est gérée via **Jetpack Room** (`AppDatabase.kt`) et contient 20 entités réparties en 4 grands domaines.

### A. Domaine E-Commerce & Catalogue

#### 1. `ecommerce_products` (Produits)
Stocke tous les articles détectés depuis Telegram ou saisis manuellement.
- `id` (String, PK) : Identifiant unique du produit (ex: `prod_1725884123`).
- `sourceTelegramMessageId` (Long?) : ID du message Telegram source.
- `sourceChannelTitle` (String?) : Nom du canal Telegram d'origine (ex: "Akkipi").
- `title` (String) : Nom ou titre de l'article.
- `description` (String) : Description complète de l'article.
- `categoryId` (String?) : Clé étrangère vers la catégorie locale.
- `supplierId` (String?) : Clé étrangère vers le fournisseur local.
- `purchasePrice` (Double?) : Prix d'achat / prix fournisseur en gros.
- `sellingPrice` (Double?) : Prix de vente au détail conseillé.
- `currency` (String) : Devise (défaut: `MAD`).
- `stockQuantity` (Int) : Stock disponible (défaut: `10`).
- `primaryImageUrl` (String?) : URL de l'image principale (locale ou Supabase Storage).
- `status` (String) : `DRAFT`, `VALIDATED`, `PUBLISHED`, `ARCHIVED`, `NEEDS_PRICE_REVIEW`.
- `isPublishedToWebsite` (Boolean) : Indique si le produit est en ligne sur Supabase.
- `isLotOrPackPrice` (Boolean) : Détecte si le prix correspond à un pack/lot (ex: pack de 6).
- `lotQuantity` (Int?), `lotTotalPrice` (Double?), `lotUnitPriceEstimate` (Double?), `lotLabel` (String?) : Détails du lot.
- `needsPriceReview` (Boolean) : Drapeau levé par l'IA si un doute existe sur le prix.
- `createdAt` (Long), `updatedAt` (Long) : Timestamps UNIX.

#### 2. `ecommerce_product_media` (Médias Produits)
- `id` (String, PK) : UUID.
- `productId` (String) : Référence au produit.
- `mediaUrl` (String) : URL en ligne (Supabase Storage ou Telegram).
- `localPath` (String?) : Chemin absolu sur le stockage local du téléphone.
- `mediaType` (String) : `photo`, `video`, `document`.
- `sortOrder` (Int) : Ordre d'affichage dans la galerie.

#### 3. `ecommerce_categories` (Catégories)
- `id` (String, PK), `name` (String), `slug` (String), `description` (String), `iconName` (String).
- `assignedAgentId` (String?) : Agent IA dédié pour router les conversations WhatsApp selon la catégorie.
- `productCount` (Int), `isActive` (Boolean).

#### 4. `ecommerce_suppliers` & `ecommerce_price_contacts` (Fournisseurs & Contacts)
- Gestion des contacts d'usines et grossistes, canaux Telegram rattachés, délais, remises négociées et notes.

#### 5. `ecommerce_shipping_agencies` (Agences de Livraison)
- Zones couvertes (ex: Casablanca, Rabat, Marrakech, Dakar...), tarification de base, téléphone, délais moyens.

#### 6. `ecommerce_affiliates` (Affiliés & Vendeurs Partenaires)
- Code de parrainage, taux de commission, cumul des gains et ventes.

#### 7. `ecommerce_orders` (Commandes & Prospects à rappeler)
- `id`, `orderNumber`, `customerName`, `customerPhone`, `deliveryAddress`, `deliveryZone`.
- `productId`, `productName`, `quantity`, `totalAmount`, `currency`.
- `status` : `PENDING_CONFIRMATION`, `CONFIRMED_CALL`, `IN_DELIVERY`, `DELIVERED`, `CANCELLED`.
- `assignedShippingAgencyId`, `callAttemptsCount`, `customerCallNotes`.

---

### B. Domaine Telegram & Scraping Téléthon

#### 1. `telegram_accounts`
- Configuration Telethon (`apiId`, `apiHash`, numéro, statut de session, port de passerelle `8088`).

#### 2. `telegram_channels`
- Liste des canaux grossistes surveillés (Akkipi, etc.), état de surveillance active (`isMonitored`).

#### 3. `telegram_messages`
- Historique brut des messages de catalogue reçus : texte, type de média, chemins locaux des images téléchargées, JSON brut des pièces jointes, drapeau `isProcessed`.

---

### C. Domaine Intelligence Artificielle & WhatsApp

#### 1. `whatsapp_instances`
- Instances Baileys connectées (QR code ou code d'appairage, port `8080`).

#### 2. `ai_agents`
- Définition des rôles IA (Commercial, VIP, Support, etc.), prompts système, modèles (Gemma, Llama, Gemini Flash), seuils de température, RAG activé.

#### 3. `knowledge_sources` (Base RAG)
- Sources de connaissances injectées : textes, documents ou tables distantes Supabase (`documents`).

#### 4. `whatsapp_messages`
- Traçabilité complète des conversations clients avec routage par agent IA et temps de latence NPU.

---

### D. Domaine Paramètres Système (`app_settings`)
- Configuration globale de l'application : devise (`MAD`), indicatif (`+212`), marges bénéficiaires automatiques (40%), clés d'API (Gemini, Supabase URL, Supabase Anon/Service Key, Telegram API ID/Hash).

---

## 3. Base de Données Cloud : Supabase (PostgreSQL & Storage)

Supabase sert de **backend d'exposition vitrine** pour rendre les produits accessibles aux clients finaux.

### Schéma de la table `public.products` (Supabase)

| Colonne Supabase | Type PostgreSQL | Obligatoire | Rôle / Équivalence Locale |
|---|---|---|---|
| `id` | `bigint` (Identity/PK) | Auto | Identifiant unique généré par la séquence PostgreSQL. |
| `name` | `text` | ✅ OUI | Nom du produit (provient de `ProductEntity.title.trim()`). |
| `description` | `text` | ✅ OUI | Description commerciale complète (défaut: `''`). |
| `wholesale_price` | `numeric(12, 2)` | ✅ OUI | Prix d'achat / grossiste (`ProductEntity.purchasePrice ?: 0.0`). |
| `suggested_sale_price` | `numeric(12, 2)` | ⭐ Site Web | Prix de vente client (`ProductEntity.sellingPrice`). |
| `stock` | `integer` | ⭐ Site Web | Stock disponible (défaut: `100` ou `stockQuantity`). |
| `images` | `jsonb` | ⭐ Site Web | Tableau JSON des URLs pour le carrousel : `["url1", "url2"]`. |
| `image_url` | `text` | Optionnel | URL publique de la 1ère image (fallback). |
| `category_name` | `text` | Optionnel | Nom de la catégorie (ex: "Mode & Chaussures"). |
| `supplier_name` | `text` | Optionnel | Nom du fournisseur (ex: "Akkipi"). |
| `delivery_cost` | `numeric(12, 2)` | Optionnel | Coût de livraison (défaut: `0.0`). |
| `is_lot` | `boolean` | Optionnel | Indique si c'est un lot ou pack (`isLotOrPackPrice`). |
| `lot_quantity` | `integer` | Optionnel | Quantité par lot/pack (défaut: `1`). |
| `lot_total_price` | `numeric` | Optionnel | Prix total du pack. |
| `lot_unit_price` | `numeric` | Optionnel | Prix unitaire estimé du pack. |
| `lot_label` | `text` | Optionnel | Unité de vente (défaut: `"pièce"`). |
| `source_message_id` | `text` | Traçabilité | **Clé de réconciliation locale** (`ProductEntity.id`). Permet les syncs idempotentes. |
| `source_channel_id` | `text` | Optionnel | Titre ou ID du canal Telegram source. |
| `active` | `boolean` | Système | Visibilité active sur le site Web (`true`). |
| `is_published_to_website` | `boolean` | Système | Indicateur de parution vitrine (`true`). |
| `status` | `text` | Système | Statut de publication (`'PUBLISHED'`). |
| `category_id` / `supplier_id` | `uuid` | ⚠️ Laisser NULL | Laisser `null` pour éviter l'erreur PostgreSQL `22P02` (syntaxe UUID invalide). Utiliser `category_name` et `supplier_name`. |
| `created_at`, `updated_at` | `timestamptz` | Auto | Date de création et mise à jour (gérées par PostgreSQL). |

### Bucket de Stockage : `product-media`
- Bucket public dédié au stockage des photos de haute qualité.
- L'URL finale publique d'un média est construite comme suit :  
  `https://<projet>.supabase.co/storage/v1/object/public/product-media/products/<product_id>_<timestamp>.jpg`

---

## 4. Comment l'Application Interagit avec la Base de Données

Le flux complet se déroule en plusieurs étapes orchestrées par le `MainViewModel` et `SupabaseSyncService` :

```
[Canal Grossiste Telegram]
           │
           ▼ (Téléthon :8088)
   Extraction Médias + Texte
           │
           ▼
[App Room: telegram_messages]
           │
           ▼ (Parsing IA / RegEx / NPU)
[App Room: ecommerce_products] (status = DRAFT, isPublished = false)
           │
           │ L'utilisateur clique sur "Publier"
           ▼
[SupabaseSyncService.kt]
    1. Upload de l'image locale vers Supabase Storage (product-media)
    2. Récupération de l'URL publique CDN Supabase
    3. Inspection dynamique du schéma PostgREST (/rest/v1/)
    4. Vérification d'existence (GET /rest/v1/products?source_message_id=eq.{id})
    5. POST (création) ou PATCH (mise à jour)
           │
           ▼
[Mise à jour Room Locale] (status = PUBLISHED, isPublished = true)
           │
           ▼
[Produit en direct sur la Vitrine Web]
```

### 1. Ingestion Locale
1. Le service Telegram capte les photos et les textes envoyés par les fournisseurs grossistes dans la table locale `telegram_messages`.
2. Le moteur d'analyse extrait les prix, les lots et les descriptions pour enregistrer une entité `ecommerce_products`.

### 2. Publication Cloud Adaptative (`SupabaseSyncService.kt`)
Lors de l'appui sur le bouton **Publier** :
1. **Upload des Photos** : Le service lit le fichier image stocké sur le téléphone et l'envoie par requête HTTP `POST` multipart/octet-stream vers :  
   `POST /storage/v1/object/product-media/products/{nom_fichier}`
2. **Auto-Découverte du Schéma Supabase** : L'application interroge dynamiquement `/rest/v1/` de Supabase pour inspecter les colonnes exactes de la table `products`. Elle s'adapte automatiquement sans planter si une colonne change de nom ou si le format est `numeric` plutôt qu'`integer`.
3. **Réconciliation par `source_message_id`** :
   - L'application vérifie d'abord si l'article existe déjà :  
     `GET /rest/v1/products?source_message_id=eq.{product.id}&select=id`
   - S'il existe : elle applique un `PATCH` pour mettre à jour prix, stock et photos.
   - S'il n'existe pas : elle applique un `POST` pour créer la nouvelle ligne dans la table.
4. **Validation Locale** : Une fois la réponse HTTP 200/201 reçue de Supabase, Room met à jour l'entité locale :
   ```kotlin
   productDao.updateProduct(product.copy(isPublishedToWebsite = true, status = "PUBLISHED"))
   ```
   L'interface Android passe immédiatement l'étiquette en vert avec l'indicateur **"En Ligne"**.

### 3. Dépublication / Retrait
Lorsque l'utilisateur décide de retirer un produit de la vitrine :
- Une requête `PATCH` est envoyée pour passer `active = false`, `status = 'DRAFT'` et `is_published_to_website = false`.
- La base Room locale repasse le statut en brouillon (`DRAFT`).

---

## 5. Résumé des Avantages de cette Architecture

1. **Résilience Maximale (Mode Hors-Ligne)** : Même sans connexion Internet, l'application fonctionne à 100% (consultation du catalogue, calcul des marges, historique WhatsApp et gestion des commandes).
2. **Synchronisation Ciblée** : Seuls les produits validés et confirmés par l'utilisateur sont envoyés sur le Cloud.
3. **Zéro Conflit de Clés** : L'utilisation de `source_message_id` garantit que les identifiants auto-incrémentés de Supabase ne rentrent jamais en conflit avec les identifiants locaux.
4. **Schéma Souple & Extensible** : Grâce à l'inspection dynamique des propriétés PostgREST, l'application est rétrocompatible avec les différentes versions de tables Supabase.
