@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.homelab.brisa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.mozilla.geckoview.GeckoView

@Composable
fun TabGrid(tabs: List<Tab>, current: Int, ui: UiState, select: (Int) -> Unit, onClose: (Int) -> Unit, onNew: (Boolean) -> Unit, onDone: () -> Unit) {
    var privateView by remember { mutableStateOf(tabs.getOrNull(current)?.private == true) }
    // Grupo que se está viendo: null = todas
    var groupView by remember { mutableStateOf(tabs.getOrNull(current)?.group) }
    var grouping by remember { mutableStateOf<Tab?>(null) }
    val groups = tabs.filter { !it.private }.mapNotNull { it.group }.distinct().sorted()
    if (groupView != null && groupView !in groups) groupView = null
    val shown = tabs.withIndex().filter { it.value.private == privateView && (privateView || groupView == null || it.value.group == groupView) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).systemBarsPadding()) {
        if (!privateView && groups.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(groupView == null, { groupView = null }, { Text("Todas") })
            groups.forEach { g -> FilterChip(groupView == g, { groupView = g }, { Text("$g · ${tabs.count { it.group == g }}") }) }
        }
        grouping?.let { t -> GroupDialog(t, groups, { grouping = null }) { g -> t.group = g; if (g != null) groupView = g } }
        val ctx = LocalContext.current
        if (privateView && ui.privateLocked && shown.isNotEmpty()) Box(Modifier.weight(1f).fillMaxWidth()) { PrivateLock { ui.unlockPrivate(ctx) } }
        else LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown.size) { k ->
                val (i, t) = shown[k]
                Column(Modifier.clip(RoundedCornerShape(16.dp))
                    .border(if (i == current) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .combinedClickableCompat({ select(i) }, { if (!t.private) grouping = t })) {
                    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!t.isHome) { SiteIcon(t.host, 18.dp, 4.dp); Spacer(Modifier.width(6.dp)) }
                        Text(t.title.ifBlank { if (t.isHome) "Inicio" else t.host }, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ onClose(i) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Cerrar", Modifier.size(16.dp)) }
                    }
                    // #66: si se soltó por falta de memoria, se vuelve a leer del disco
                    LaunchedEffect(t.id, t.thumb) { if (t.thumb == null && !t.private && !t.isHome) Thumbs.load(ctx, t.id) { b -> if (t.thumb == null) t.thumb = b } }
                    val th = t.thumb
                    if (th != null) Image(th.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                    else Box(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ onNew(privateView); if (!privateView) groupView?.let { g -> tabs.lastOrNull()?.group = g } }) { Icon(Icons.Filled.Add, "Nueva pestaña") }
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(privateView, { privateView = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Privada") }
                SegmentedButton(!privateView, { privateView = false }, SegmentedButtonDefaults.itemShape(1, 2)) {
                    Text("${tabs.count { !it.private }} pestañas")
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onDone) { Text("OK", fontWeight = FontWeight.Medium) }
        }
    }
}

/** Mantener pulsada una pestaña: elegir grupo, crear uno nuevo o quitarla del grupo. */
@Composable
fun GroupDialog(tab: Tab, groups: List<String>, dismiss: () -> Unit, set: (String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text("Grupo de la pestaña") },
        text = { Column {
            groups.forEach { g ->
                Row(Modifier.fillMaxWidth().clickable { set(g); dismiss() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(tab.group == g, null); Text(g, Modifier.padding(start = 8.dp))
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                label = { Text("Grupo nuevo") }, placeholder = { Text("Homelab, Compras, Viaje…") })
        } },
        confirmButton = { TextButton({ if (name.isNotBlank()) { set(name.trim()); dismiss() } }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { if (tab.group != null) TextButton({ set(null); dismiss() }) { Text("Quitar del grupo") } else TextButton(dismiss) { Text("Cancelar") } })
}

/** Tapa una pestaña privada bloqueada. */
@Composable
fun PrivateLock(unlock: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).clickable(onClick = unlock),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pestañas privadas bloqueadas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        FilledTonalButton(unlock, Modifier.padding(top = 16.dp)) { Text("Desbloquear") }
    }
}
