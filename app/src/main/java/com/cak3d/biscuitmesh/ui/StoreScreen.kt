package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.StoreCatalog
import com.cak3d.biscuitmesh.data.StoreItem
import com.cak3d.biscuitmesh.data.StoreKind

/**
 * The Feathers store — spend your currency on titles, premium accent colours, and vanity renames
 * for your flock and handle. Cosmetics are owned once and can be equipped/unequipped for free after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(vm: MainViewModel, onBack: () -> Unit) {
    val feathers by vm.feathers.collectAsStateWithLifecycle()
    val owned by vm.owned.collectAsStateWithLifecycle()
    val equippedTitle by vm.equippedTitle.collectAsStateWithLifecycle()
    val equippedAccent by vm.equippedAccent.collectAsStateWithLifecycle()
    val equippedAvatar by vm.equippedAvatar.collectAsStateWithLifecycle()
    val equippedOverlay by vm.equippedOverlay.collectAsStateWithLifecycle()
    val premium by vm.premium.collectAsStateWithLifecycle()
    val msg by vm.storeMsg.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); vm.clearStoreMsg() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Text("🪶 $feathers", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Flock Premium.
            item { SectionLabel("FLOCK PREMIUM") }
            item {
                val premiumItem = StoreCatalog.items.first { it.kind == StoreKind.PREMIUM }
                StoreCard {
                    Text("Flock Premium ✦", fontWeight = FontWeight.Bold, color = Color(0xFFFFD166), fontSize = 16.sp)
                    Text(premiumItem.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    if (premium) {
                        Text("✓ Active", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                    } else {
                        Button(onClick = { vm.buyItem(premiumItem) }, enabled = feathers >= premiumItem.price) {
                            Text("Unlock · 🪶 ${premiumItem.price}")
                        }
                        Text("A \$2/month option would need Google Play Billing (not wired yet).",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            // Profile pictures.
            item { SectionLabel("PROFILE PICTURES") }
            items2(StoreCatalog.items.filter { it.kind == StoreKind.AVATAR }) { item ->
                val isOwned = item.id in owned || item.price == 0
                val isEquipped = equippedAvatar == item.value || (equippedAvatar.isBlank() && item.id == "avatar_crow")
                StoreCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarRing(item.value, equippedOverlay, 46.dp)
                            Text(item.name, fontWeight = FontWeight.Bold)
                        }
                        BuyEquip(item, isOwned, isEquipped, feathers,
                            onBuy = { vm.buyItem(item) }, onEquip = { vm.equipItem(item) }, onUnequip = {})
                    }
                }
            }

            // Overlays (rings around the avatar).
            item { SectionLabel("AVATAR OVERLAYS") }
            items2(StoreCatalog.items.filter { it.kind == StoreKind.OVERLAY }) { item ->
                val isOwned = item.id in owned
                val isEquipped = equippedOverlay == item.value
                StoreCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AvatarRing(equippedAvatar.ifBlank { "🐦‍⬛" }, item.value, 46.dp)
                            Column {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                if (item.premiumOnly) Text("Premium only", fontSize = 11.sp, color = Color(0xFFFFD166))
                            }
                        }
                        BuyEquip(item, isOwned, isEquipped, feathers,
                            onBuy = { vm.buyItem(item) }, onEquip = { vm.equipItem(item) },
                            onUnequip = { vm.unequipOverlay() })
                    }
                }
            }

            // Titles.
            item { SectionLabel("TITLES") }
            items2(StoreCatalog.items.filter { it.kind == StoreKind.TITLE }) { item ->
                val isOwned = item.id in owned
                val isEquipped = equippedTitle == item.value
                StoreCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        BuyEquip(item, isOwned, isEquipped, feathers,
                            onBuy = { vm.buyItem(item) }, onEquip = { vm.equipItem(item) },
                            onUnequip = { vm.unequipTitle() })
                    }
                }
            }

            // Accents.
            item { SectionLabel("ACCENT COLOURS") }
            items2(StoreCatalog.items.filter { it.kind == StoreKind.ACCENT }) { item ->
                val isOwned = item.id in owned
                val isEquipped = equippedAccent == item.value
                val color = runCatching { Color(android.graphics.Color.parseColor(item.value)) }.getOrDefault(Color.Gray)
                StoreCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(color))
                            Text(item.name, fontWeight = FontWeight.Bold)
                        }
                        BuyEquip(item, isOwned, isEquipped, feathers,
                            onBuy = { vm.buyItem(item) }, onEquip = { vm.equipItem(item) },
                            onUnequip = { vm.unequipAccent() })
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun BuyEquip(
    item: StoreItem, owned: Boolean, equipped: Boolean, feathers: Long,
    onBuy: () -> Unit, onEquip: () -> Unit, onUnequip: () -> Unit,
) {
    when {
        equipped -> OutlinedButton(onClick = onUnequip) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(" Equipped")
        }
        owned -> FilledTonalButton(onClick = onEquip) { Text("Equip") }
        else -> Button(onClick = onBuy, enabled = feathers >= item.price) { Text("🪶 ${item.price}") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun StoreCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Small helper so a plain list renders as individual LazyColumn items. */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(list: List<T>, row: @Composable (T) -> Unit) {
    items(list.size) { i -> row(list[i]) }
}
