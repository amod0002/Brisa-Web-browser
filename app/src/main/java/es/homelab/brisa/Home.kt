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
fun HomePage(favs: Favorites, private: Boolean, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val w = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val cols = if (w >= 600) (w / 110).coerceIn(5, 8) else 4
    val side = ((w - 960) / 2).coerceAtLeast(0).dp
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(horizontal = side).padding(20.dp).padding(bottom = 160.dp)) {
        if (private) Text("Navegación privada: no se guarda historial ni cookies al cerrar.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        Text("Favoritos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        val list = favs.list
        if (list.isEmpty()) Text("Toca el marcador de la barra para añadir webs aquí (Proxmox, Cloudreve, Immich…).", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                row.forEach { (name, url) ->
                    Column(Modifier.weight(1f).combinedClickableCompat({ open(url) }, { favs.toggle(name, url) }), horizontalAlignment = Alignment.CenterHorizontally) {
                        SiteIcon(History.hostOf(url), 58.dp, 14.dp)
                        Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (!private) {
            val freq = History.get(ctx).frequent(exclude = favs.list.map { History.hostOf(it.second) }.toSet())
            if (freq.isNotEmpty()) {
                Text("Visitados con frecuencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                freq.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        row.forEach { e ->
                            Column(Modifier.weight(1f).clickable { open(e.url) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                SiteIcon(History.hostOf(e.url), 58.dp, 14.dp)
                                Text(e.title.ifBlank { History.hostOf(e.url) }.substringBefore(" - ").substringBefore(" | "), fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp))
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Favoritos guardados en el móvil. Mantener pulsado uno lo quita. */
class Favorites(ctx: android.content.Context) {
    private val prefs = ctx.getSharedPreferences("favs", 0)
    var list by mutableStateOf(load())
        private set

    private fun load() = prefs.getString("l", "")!!.split('\n').filter { '\t' in it }
        .map { it.substringBefore('\t') to it.substringAfter('\t') }

    fun has(url: String) = list.any { it.second == url }
    fun toggle(name: String, url: String) {
        list = if (has(url)) list.filter { it.second != url } else list + (name to url)
        prefs.edit().putString("l", list.joinToString("\n") { "${it.first}\t${it.second}" }).apply()
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLong: () -> Unit) = combinedClickable(onClick = onClick, onLongClick = onLong)
