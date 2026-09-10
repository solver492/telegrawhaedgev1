package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.CategoryEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.ElegantDarkBg
import com.example.ui.theme.ElegantDarkBorder
import com.example.ui.theme.ElegantDarkSurface
import com.example.ui.theme.ElegantDarkSurfaceVariant
import com.example.ui.theme.ElegantPurpleAccent
import com.example.ui.theme.ElegantTextPrimary
import com.example.ui.theme.ElegantTextSecondary
import com.example.ui.theme.WhatsAppGreen
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.commerceCategories.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val products by viewModel.commerceProducts.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<CategoryEntity?>(null) }
    var categoryForAgentConfig by remember { mutableStateOf<CategoryEntity?>(null) }
    var expandedAgentMenuCatId by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize().background(ElegantDarkBg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Catégories & Routage WhatsApp",
                                color = ElegantTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Catalogue 12 catégories e-commerce • Agents IA spécialisés (Darija / FR)",
                                color = ElegantTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Boutons d'action Supabase & Reset catalogue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.syncCategoriesWithSupabase() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantPurpleAccent),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Supabase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.seedDefaultCategories() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantDarkSurface),
                            border = BorderStroke(1.dp, ElegantDarkBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = ElegantTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("12 Catégories", color = ElegantTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (categories.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                        border = BorderStroke(1.dp, ElegantDarkBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = ElegantTextSecondary, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Aucune catégorie enregistrée", color = ElegantTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Chargez les 12 catégories par défaut pour activer le routage automatique par IA.",
                                color = ElegantTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.seedDefaultCategories() },
                                colors = ButtonDefaults.buttonColors(containerColor = ElegantPurpleAccent),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Initialiser les 12 catégories", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(categories, key = { it.id }) { cat ->
                    val assignedAgent = agents.find { it.id == cat.assignedAgentId }
                    val currentCount = products.count { it.categoryId == cat.id }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElegantDarkSurface),
                        border = BorderStroke(1.dp, ElegantDarkBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("category_card_${cat.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ElegantPurpleAccent.copy(alpha = 0.15f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (!cat.icon.isNullOrBlank()) {
                                                Text(cat.icon, fontSize = 20.sp)
                                            } else {
                                                val icon = when (cat.iconName) {
                                                    "Devices" -> Icons.Default.Devices
                                                    "Checkroom" -> Icons.Default.Checkroom
                                                    else -> Icons.Default.Category
                                                }
                                                Icon(icon, contentDescription = null, tint = ElegantPurpleAccent, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                cat.name,
                                                color = ElegantTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            if (!cat.nameAr.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "• ${cat.nameAr}",
                                                    color = ElegantPurpleAccent,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                        Text(
                                            "${currentCount.coerceAtLeast(cat.productCount)} articles référencés",
                                            color = ElegantTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row {
                                    IconButton(onClick = { categoryToEdit = cat }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = ElegantTextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.deleteCategory(cat) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (cat.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    cat.description,
                                    color = ElegantTextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            // Aperçu des produits associés
                            val catProducts = products.filter { it.categoryId == cat.id }
                            if (catProducts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    catProducts.take(2).forEach { prod ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ElegantDarkSurfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, ElegantDarkBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = prod.title.take(18) + if (prod.title.length > 18) "…" else "",
                                                    fontSize = 10.sp,
                                                    color = ElegantTextPrimary,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    if (catProducts.size > 2) {
                                        Text(
                                            text = "+${catProducts.size - 2} autres",
                                            fontSize = 10.sp,
                                            color = ElegantPurpleAccent,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Panneau Agent IA Catégorie & Routage WhatsApp
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElegantDarkBg,
                                border = BorderStroke(1.dp, if (assignedAgent != null || cat.aiAgentPrompt.isNotBlank()) WhatsAppGreen.copy(alpha = 0.5f) else ElegantDarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Psychology, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val agentDisplay = if (cat.aiAgentName.isNotBlank()) cat.aiAgentName else (assignedAgent?.name ?: "Agent IA ${cat.name}")
                                            Text(
                                                agentDisplay,
                                                color = ElegantTextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ElegantPurpleAccent.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "T° ${cat.aiAgentTemperature}",
                                                    color = ElegantPurpleAccent,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = WhatsAppGreen.copy(alpha = 0.15f),
                                                modifier = Modifier.clickable { categoryForAgentConfig = cat }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Tune, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(10.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        "Prompt IA",
                                                        color = WhatsAppGreen,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (cat.aiAgentPrompt.isNotBlank()) {
                                        Text(
                                            cat.aiAgentPrompt.take(120) + if (cat.aiAgentPrompt.length > 120) "…" else "",
                                            color = ElegantTextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }

                                    // Ligne de routage instance ou agent parent
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { expandedAgentMenuCatId = cat.id },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Routage agent parent : ${assignedAgent?.name ?: "Automatique (par mots-clés)"}",
                                                color = ElegantTextSecondary,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                "Changer ▾",
                                                color = ElegantPurpleAccent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = expandedAgentMenuCatId == cat.id,
                                            onDismissRequest = { expandedAgentMenuCatId = null },
                                            modifier = Modifier.background(ElegantDarkSurface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Routage Automatique (Par défaut)", color = ElegantTextSecondary, fontSize = 12.sp) },
                                                leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, tint = ElegantTextSecondary, modifier = Modifier.size(16.dp)) },
                                                onClick = {
                                                    viewModel.updateCategoryAgent(cat.id, null)
                                                    expandedAgentMenuCatId = null
                                                }
                                            )
                                            agents.forEach { ag ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(ag.name, color = ElegantTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(ag.role, color = ElegantTextSecondary, fontSize = 10.sp)
                                                        }
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp)) },
                                                    onClick = {
                                                        viewModel.updateCategoryAgent(cat.id, ag.id)
                                                        expandedAgentMenuCatId = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = ElegantPurpleAccent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("fab_add_category")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nouvelle Catégorie")
        }
    }

    if (showAddDialog || categoryToEdit != null) {
        val initialCat = categoryToEdit
        var name by remember { mutableStateOf(initialCat?.name ?: "") }
        var nameAr by remember { mutableStateOf(initialCat?.nameAr ?: "") }
        var icon by remember { mutableStateOf(initialCat?.icon ?: "📦") }
        var description by remember { mutableStateOf(initialCat?.description ?: "") }
        var aiAgentName by remember { mutableStateOf(initialCat?.aiAgentName ?: "") }
        var aiAgentPrompt by remember { mutableStateOf(initialCat?.aiAgentPrompt ?: "") }
        var selectedAgentId by remember { mutableStateOf(initialCat?.assignedAgentId ?: agents.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                categoryToEdit = null
            },
            containerColor = ElegantDarkSurface,
            title = {
                Text(
                    if (initialCat == null) "Nouvelle Catégorie" else "Modifier la Catégorie",
                    color = ElegantTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = icon,
                            onValueChange = { icon = it.take(2) },
                            label = { Text("Icône") },
                            singleLine = true,
                            modifier = Modifier.width(70.dp)
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nom (FR) *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = nameAr,
                        onValueChange = { nameAr = it },
                        label = { Text("Nom en Arabe (Ex: الموضة)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description & mots-clés") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = aiAgentName,
                        onValueChange = { aiAgentName = it },
                        label = { Text("Nom de l'Agent IA dédié") },
                        placeholder = { Text("Ex: Agent Mode & Prêt-à-porter") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = aiAgentPrompt,
                        onValueChange = { aiAgentPrompt = it },
                        label = { Text("Directives & Prompt IA spécialisé") },
                        placeholder = { Text("Ex: Proposer les tailles S à XL, prix en MAD...") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Agent WhatsApp assigné :", color = ElegantTextSecondary, fontSize = 11.sp)

                    // Option automatique
                    val isAuto = selectedAgentId == null
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAuto) ElegantDarkSurfaceVariant else ElegantDarkBg,
                        border = BorderStroke(1.dp, if (isAuto) ElegantPurpleAccent else ElegantDarkBorder),
                        modifier = Modifier.fillMaxWidth().clickable { selectedAgentId = null }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, tint = if (isAuto) ElegantPurpleAccent else ElegantTextSecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Routage Automatique (Par défaut)", color = if (isAuto) ElegantTextPrimary else ElegantTextSecondary, fontSize = 12.sp)
                        }
                    }

                    // Choix Agent
                    agents.take(3).forEach { agent ->
                        val isChosen = selectedAgentId == agent.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isChosen) WhatsAppGreen.copy(alpha = 0.15f) else ElegantDarkBg,
                            border = BorderStroke(1.dp, if (isChosen) WhatsAppGreen else ElegantDarkBorder),
                            modifier = Modifier.fillMaxWidth().clickable { selectedAgentId = agent.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = null, tint = if (isChosen) WhatsAppGreen else ElegantTextSecondary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(agent.name, color = if (isChosen) ElegantTextPrimary else ElegantTextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bouton Enregistrer
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val cat = (initialCat ?: CategoryEntity(
                                    id = "cat-${UUID.randomUUID().toString().take(8)}",
                                    name = name,
                                    slug = name.lowercase().replace(" ", "-")
                                )).copy(
                                    name = name,
                                    nameAr = nameAr.ifBlank { null },
                                    icon = icon.ifBlank { "📦" },
                                    description = description,
                                    aiAgentName = if (aiAgentName.isNotBlank()) aiAgentName else "Agent $name",
                                    aiAgentPrompt = aiAgentPrompt,
                                    assignedAgentId = selectedAgentId,
                                    updatedAt = System.currentTimeMillis()
                                )
                                viewModel.saveCategory(cat)
                                showAddDialog = false
                                categoryToEdit = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElegantPurpleAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_category_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer la catégorie", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            showAddDialog = false
                            categoryToEdit = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annuler", color = ElegantTextSecondary)
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Modal de Configuration de l'Agent IA Spécialisé
    categoryForAgentConfig?.let { targetCat ->
        var agentName by remember { mutableStateOf(targetCat.aiAgentName.ifBlank { "Agent ${targetCat.name}" }) }
        var prompt by remember { mutableStateOf(targetCat.aiAgentPrompt) }
        var temperature by remember { mutableStateOf(targetCat.aiAgentTemperature.toFloat()) }

        AlertDialog(
            onDismissRequest = { categoryForAgentConfig = null },
            containerColor = ElegantDarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agent IA : ${targetCat.name}", color = ElegantTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Personnalisez le comportement de l'Agent IA pour les requêtes WhatsApp de cette catégorie.",
                        color = ElegantTextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = agentName,
                        onValueChange = { agentName = it },
                        label = { Text("Nom de l'agent IA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Créativité (Température) :", color = ElegantTextSecondary, fontSize = 11.sp)
                            Text(String.format(java.util.Locale.US, "%.2f", temperature), color = ElegantPurpleAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = ElegantPurpleAccent,
                                activeTrackColor = ElegantPurpleAccent
                            )
                        )
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Prompt Système Spécialisé") },
                        placeholder = { Text("Directives de vente, gestion des tailles, livraison...") },
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            viewModel.updateCategoryAgentDetails(
                                categoryId = targetCat.id,
                                agentId = targetCat.assignedAgentId,
                                agentName = agentName.trim(),
                                prompt = prompt.trim(),
                                temperature = temperature.toDouble()
                            )
                            categoryForAgentConfig = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer l'Agent IA", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { categoryForAgentConfig = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fermer", color = ElegantTextSecondary)
                    }
                }
            },
            confirmButton = {}
        )
    }
}
