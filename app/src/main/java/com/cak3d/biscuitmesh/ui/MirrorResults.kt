package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.protocol.BiscuitMessage

private val R_PANEL = Color(0xFF101217)
private val R_DIM = Color(0xFF6B7078)
private val R_INK = Color(0xFFE8E6DC)
private val R_GREEN = Color(0xFF53D38A)
private val R_AMBER = Color(0xFFF2AC2A)
private val R_BLOOD = Color(0xFFCA1C24)
private val R_PLUM = Color(0xFFB24BFF)
private val R_CYAN = Color(0xFF22D3EE)
private val R_MONO = FontFamily.Monospace

private fun sigCol(rssi: Int): Color = when {
    rssi > -55 -> R_BLOOD
    rssi > -75 -> R_AMBER
    else -> R_PLUM
}

private fun sigFrac(rssi: Int): Float = ((rssi + 110f) / 90f).coerceIn(0.05f, 1f)

private data class Hit(val label: String, val sub: String, val rssi: Int, val secured: Boolean)

/**
 * A clean, labelled read-out of whatever the board is currently finding — Wi-Fi APs
 * (SSID + lock + channel), BLE devices (name/MAC), sub-GHz hits (freq), or a card for
 * the last NFC tag / decoded IR frame. Parses the same DATA: stream the device logs,
 * so the mirror shows real results instead of raw text. Nothing here is uploaded.
 */
@Composable
fun ScanResults(lines: List<BiscuitMessage>, activeOp: String?, modifier: Modifier = Modifier) {
    val tail = remember(lines) { lines.takeLast(200) }

    // --- NFC: the most recent genuine tag read (uid present, ISO14443A) ---
    val nfc = remember(tail) {
        tail.lastOrNull {
            it.type == BiscuitMessage.Type.DATA && it.subtype == "NFC" &&
                it.payload.endsWith("ISO14443A") && it.payload.substringBefore(',').isNotBlank()
        }?.payload
    }
    // --- IR: the most recent decoded sniff (DATA:IR:<proto>,<bits>,<code>) ---
    val ir = remember(tail) {
        tail.lastOrNull {
            it.type == BiscuitMessage.Type.DATA && it.subtype == "IR" &&
                !it.payload.startsWith("skip")
        }?.payload
    }
    // --- IR TX: the most recent transmit confirmation ---
    val irtx = remember(tail) {
        tail.lastOrNull { it.type == BiscuitMessage.Type.DATA && it.subtype == "IRTX" }?.payload
    }

    val aps = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "AP" }
            .mapNotNull { parseAp(it.payload) }
            .associateBy { it.sub }.values            // dedup by BSSID, keep latest
            .sortedByDescending { it.rssi }.take(40)
    }
    val bts = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "BT" }
            .mapNotNull { parseBt(it.payload) }
            .associateBy { it.sub }.values            // dedup by MAC, keep latest
            .sortedByDescending { it.rssi }.take(40)
    }
    val rfs = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "RF" }
            .mapNotNull { parseRf(it.payload) }
            .associateBy { it.label }.values          // dedup by freq, keep latest
            .sortedByDescending { it.rssi }.take(20)
    }

    // Choose what to show by what's active / freshest.
    val op = activeOp?.lowercase() ?: ""
    when {
        nfc != null && (op.contains("nfc") || (ir == null && aps.isEmpty() && bts.isEmpty())) ->
            NfcCard(nfc, modifier)
        ir != null && (op.contains("ir") || op.contains("sniff") || (aps.isEmpty() && bts.isEmpty())) ->
            IrCard(ir, irtx, modifier)
        aps.isNotEmpty() -> HitList("WI-FI · ${aps.size} APs", aps, R_CYAN, modifier)
        bts.isNotEmpty() -> HitList("BLE · ${bts.size} devices", bts, R_PLUM, modifier)
        rfs.isNotEmpty() -> HitList("SUB-GHZ · ${rfs.size} bands", rfs, R_GREEN, modifier)
        irtx != null -> IrCard(ir, irtx, modifier)
        else -> Unit
    }
}

@Composable
private fun HitList(title: String, hits: List<Hit>, tint: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().background(R_PANEL, RoundedCornerShape(10.dp)).padding(10.dp),
    ) {
        Text(title, color = tint, fontFamily = R_MONO, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 168.dp).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(hits) { h ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (h.secured) Icon(Icons.Default.Lock, null, tint = R_DIM,
                        modifier = Modifier.size(11.dp).padding(end = 3.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.label, color = R_INK, fontFamily = R_MONO, fontSize = 12.sp, maxLines = 1)
                        if (h.sub.isNotBlank())
                            Text(h.sub, color = R_DIM, fontFamily = R_MONO, fontSize = 9.sp, maxLines = 1)
                    }
                    SigBar(h.rssi)
                    Text("${h.rssi}", color = R_DIM, fontFamily = R_MONO, fontSize = 10.sp,
                        modifier = Modifier.width(34.dp).padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SigBar(rssi: Int) {
    Canvas(Modifier.width(46.dp).height(8.dp)) {
        drawRect(R_DIM.copy(alpha = 0.18f), Offset.Zero, Size(size.width, size.height))
        drawRect(sigCol(rssi), Offset.Zero, Size(size.width * sigFrac(rssi), size.height))
    }
}

@Composable
private fun NfcCard(payload: String, modifier: Modifier) {
    // DATA:NFC:<uid>,<type>,<cap>,ISO14443A
    val p = payload.split(',')
    val uid = p.getOrNull(0).orEmpty()
    val type = p.getOrNull(1).orEmpty()
    val cap = p.getOrNull(2).orEmpty()
    Column(
        modifier.fillMaxWidth().background(R_PANEL, RoundedCornerShape(10.dp))
            .border(1.dp, R_AMBER.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Nfc, null, tint = R_AMBER, modifier = Modifier.size(18.dp))
            Text("  NFC TAG", color = R_AMBER, fontFamily = R_MONO, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(uid.uppercase(), color = R_INK, fontFamily = R_MONO, fontSize = 18.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (type.isNotBlank()) Chip(type, R_PLUM)
            if (cap.isNotBlank() && cap != "?") Chip(cap, R_CYAN)
            Chip("ISO 14443A", R_DIM)
        }
    }
}

@Composable
private fun IrCard(ir: String?, irtx: String?, modifier: Modifier) {
    // ir: DATA:IR:<proto>,<bits>,<code>   irtx: DATA:IRTX:<proto|tv|tvbgone>,<x>,<state>
    Column(
        modifier.fillMaxWidth().background(R_PANEL, RoundedCornerShape(10.dp))
            .border(1.dp, R_BLOOD.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SettingsRemote, null, tint = R_BLOOD, modifier = Modifier.size(18.dp))
            Text("  INFRARED", color = R_BLOOD, fontFamily = R_MONO, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (ir != null) {
            val p = ir.split(',')
            val proto = p.getOrNull(0).orEmpty()
            val bits = p.getOrNull(1).orEmpty()
            val code = p.getOrNull(2).orEmpty()
            Text(if (code.startsWith("0x")) code else "0x$code", color = R_INK, fontFamily = R_MONO,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (proto.isNotBlank()) Chip(proto, R_PLUM)
                if (bits.isNotBlank()) Chip("$bits bit", R_CYAN)
            }
        }
        if (irtx != null) {
            val t = irtx.split(',')
            val what = t.getOrNull(1)?.ifBlank { t.getOrNull(0).orEmpty() } ?: ""
            Text("TX ▸ ${t.getOrNull(0).orEmpty()} $what ${t.getOrNull(2).orEmpty()}".trim(),
                color = R_GREEN, fontFamily = R_MONO, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color) {
    Text(
        text, color = tint, fontFamily = R_MONO, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(tint.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// --- parsers (split from the right so commas inside an SSID/name are tolerated) ---

private fun parseAp(payload: String): Hit? {
    // <ssid>,<bssid>,<ch>,<rssi>,<auth>
    val f = payload.split(',')
    if (f.size < 5) return null
    val auth = f[f.size - 1].trim()
    val rssi = f[f.size - 2].trim().toIntOrNull() ?: return null
    val ch = f[f.size - 3].trim()
    val bssid = f[f.size - 4].trim()
    val ssid = f.subList(0, f.size - 4).joinToString(",").ifBlank { "‹hidden›" }
    val secured = auth.isNotBlank() && !auth.equals("open", true) && auth != "-"
    return Hit(ssid, "$bssid · ch $ch · $auth", rssi, secured)
}

private fun parseBt(payload: String): Hit? {
    // <name>,<addr>,<rssi>
    val f = payload.split(',')
    if (f.size < 3) return null
    val rssi = f[f.size - 1].trim().toIntOrNull() ?: return null
    val addr = f[f.size - 2].trim()
    val name = f.subList(0, f.size - 2).joinToString(",").ifBlank { "‹no name›" }
    return Hit(name, addr, rssi, false)
}

private fun parseRf(payload: String): Hit? {
    // <freq>,<rssi>
    val f = payload.split(',')
    if (f.size < 2) return null
    val rssi = f[1].trim().toIntOrNull() ?: return null
    val freq = f[0].trim()
    return Hit("$freq MHz", "", rssi, false)
}
