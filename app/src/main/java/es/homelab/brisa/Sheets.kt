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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Logout
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
fun PageMenu(tab: Tab, dismiss: () -> Unit, newPrivate: () -> Unit, openHistory: () -> Unit, openSettings: () -> Unit, openDownloads: () -> Unit,
             openTab: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var size by remember { mutableFloatStateOf(Engine.textSize(ctx)) }
    var ublock by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { Engine.hasUblock(ctx) { ublock = it } }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            var showReport by remember { mutableStateOf(false) }
            ListItem(leadingContent = { Icon(if (tab.shieldOff) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield, null) },
                headlineContent = { Text(if (tab.shieldOff) "Protección desactivada en ${tab.host}" else "${tab.blocked} rastreadores bloqueados") },
                supportingContent = { Text("Ver informe de privacidad") },
                trailingContent = { if (!tab.isHome && !tab.private && tab.trackingPerm != null) Switch(!tab.shieldOff, { tab.toggleShield(); dismiss() }) },
                modifier = Modifier.clickable { showReport = true })
            if (showReport) ReportSheet({ showReport = false }) { u -> showReport = false; dismiss(); openTab(u) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Tamaño del texto", Modifier.weight(1f))
                OutlinedButton({ size = (size - 0.1f).coerceAtLeast(0.8f); Engine.setTextSize(ctx, size) }) { Text("A", fontSize = 13.sp) }
                Text("${(size * 100).toInt()} %", Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                OutlinedButton({ size = (size + 0.1f).coerceAtMost(1.6f); Engine.setTextSize(ctx, size) }) { Text("A", fontSize = 19.sp) }
            }
            if (tab.readerable || tab.reading) ListItem(leadingContent = { Icon(Icons.Outlined.Description, null) },
                headlineContent = { Text(if (tab.reading) "Salir del modo lectura" else "Modo lectura") },
                modifier = Modifier.clickable { tab.toggleReader(); dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Search, null) },
                headlineContent = { Text("Buscar en la página") },
                modifier = Modifier.clickable { tab.findText = ""; dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.History, null) },
                headlineContent = { Text("Historial") },
                modifier = Modifier.clickable { dismiss(); openHistory() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Download, null) },
                headlineContent = { Text("Descargas") },
                supportingContent = { Downloads.items.count { it.status == "Descargando" }.takeIf { it > 0 }?.let { Text("$it en curso") } },
                modifier = Modifier.clickable { dismiss(); openDownloads() })
            ListItem(leadingContent = { Icon(Icons.Outlined.Extension, null) },
                headlineContent = { Text(when (ublock) { true -> "uBlock Origin activo"; null -> "Extensiones…"; else -> "Instalar uBlock Origin" }) },
                supportingContent = { Text(if (ublock == true) "Ajustes, filtros y estadísticas" else "Bloqueador de anuncios") },
                trailingContent = { if (ublock == true) Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
                modifier = Modifier.clickable(enabled = ublock != null) {
                    if (ublock == true) {
                        Engine.ublockOptions(ctx) { u ->
                            if (u != null) { dismiss(); openTab(u) }
                            else android.widget.Toast.makeText(ctx, "No se pudo abrir uBlock Origin", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return@clickable
                    }
                    ublock = null
                    android.widget.Toast.makeText(ctx, "Instalando uBlock Origin…", android.widget.Toast.LENGTH_SHORT).show()
                    Engine.installUblock(ctx) { ok ->
                        ublock = ok
                        android.widget.Toast.makeText(ctx, if (ok) "uBlock Origin instalado" else "No se pudo instalar uBlock Origin", android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            ListItem(leadingContent = { Icon(Icons.Outlined.DesktopWindows, null) },
                headlineContent = { Text(if (tab.desktop) "Versión móvil" else "Versión de escritorio") },
                modifier = Modifier.clickable { tab.toggleDesktop(); dismiss() })
            ListItem(leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) },
                headlineContent = { Text("Nueva pestaña privada") },
                modifier = Modifier.clickable { dismiss(); newPrivate() })
            ListItem(leadingContent = { Icon(Icons.Outlined.OpenInNew, null) },
                headlineContent = { Text("Abrir en otra app") },
                modifier = Modifier.clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tab.url))) }; dismiss()
                })
            ListItem(leadingContent = { Icon(Icons.Outlined.Settings, null) },
                headlineContent = { Text("Ajustes") },
                modifier = Modifier.clickable { dismiss(); openSettings() })
            // #59: si tras un cambio de red (p. ej. quitar la VPN) no carga nada, reinicia sin ir a los ajustes de Android
            ListItem(leadingContent = { Icon(Icons.Outlined.RestartAlt, null) },
                headlineContent = { Text("Reiniciar Brisa") },
                supportingContent = { Text("Si las webs dejan de cargar tras cambiar de red" + if (Privacy.clearOnExit) "" else ". Conserva tus pestañas") },
                modifier = Modifier.clickable { dismiss(); (ctx as? MainActivity)?.restart() })
            // #43: «atrás» no cierra Brisa (la manda al fondo), así que salir de verdad y borrar es un botón
            if (Privacy.clearOnExit) ListItem(leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Salir y borrar datos", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Cierra Brisa y borra historial, cookies, caché y pestañas") },
                modifier = Modifier.clickable { dismiss(); (ctx as? MainActivity)?.quitAndWipe() })
        }
    }
}

@Composable
fun HistorySheet(dismiss: () -> Unit, open: (String) -> Unit) {
    val h = History.get(LocalContext.current)
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es")) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (h.entries.isNotEmpty()) TextButton({ h.clear() }) { Text("Borrar todo") }
        }
        if (h.entries.isEmpty()) Text("Aún no hay historial.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(h.entries.size) { i ->
                val e = h.entries[i]
                ListItem(headlineContent = { Text(e.title.ifBlank { History.hostOf(e.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${History.hostOf(e.url)} · ${fmt.format(java.util.Date(e.time))}", maxLines = 1) },
                    modifier = Modifier.clickable { open(e.url); dismiss() })
            }
        }
    }
}

/** Mantener pulsado un enlace o una imagen. */
@Composable
fun LinkSheet(e: org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement, dismiss: () -> Unit,
              newTab: (String, Boolean) -> Unit, open: (String) -> Unit, fromPrivate: Boolean = false) {
    val ctx = LocalContext.current
    val link = e.linkUri
    val src = e.srcUri
    val isImg = e.type == org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE
    fun copy(t: String) {
        (ctx.getSystemService(android.content.ClipboardManager::class.java))
            .setPrimaryClip(android.content.ClipData.newPlainText("enlace", t))
    }
    fun share(t: String) = ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), null))
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(History.hostOf(link ?: src ?: ""), 32.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(e.title ?: e.altText ?: History.hostOf(link ?: src ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(link ?: src ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (link != null) {
                if (fromPrivate) {
                    // Desde una privada, lo normal es seguir en privado; salir de él tiene que ser explícito
                    ListItem(leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) }, headlineContent = { Text("Abrir en pestaña nueva") },
                        supportingContent = { Text("Privada") },
                        modifier = Modifier.clickable { newTab(link, true); dismiss() })
                    ListItem(leadingContent = { Icon(Icons.Outlined.Tab, null) }, headlineContent = { Text("Abrir en pestaña normal") },
                        supportingContent = { Text("Sale del modo privado: quedará en el historial") },
                        modifier = Modifier.clickable { newTab(link, false); dismiss() })
                } else {
                    ListItem(leadingContent = { Icon(Icons.Outlined.Tab, null) }, headlineContent = { Text("Abrir en pestaña nueva") },
                        modifier = Modifier.clickable { newTab(link, false); dismiss() })
                    ListItem(leadingContent = { Icon(Icons.Outlined.VisibilityOff, null) }, headlineContent = { Text("Abrir en pestaña privada") },
                        modifier = Modifier.clickable { newTab(link, true); dismiss() })
                }
                ListItem(leadingContent = { Icon(Icons.Outlined.ContentCopy, null) }, headlineContent = { Text("Copiar enlace") },
                    modifier = Modifier.clickable { copy(link); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.Share, null) }, headlineContent = { Text("Compartir enlace") },
                    modifier = Modifier.clickable { share(link); dismiss() })
            }
            if (isImg && src != null) {
                ListItem(leadingContent = { Icon(Icons.Outlined.Image, null) }, headlineContent = { Text("Abrir imagen") },
                    modifier = Modifier.clickable { open(src); dismiss() })
                ListItem(leadingContent = { Icon(Icons.Outlined.Link, null) }, headlineContent = { Text("Copiar dirección de la imagen") },
                    modifier = Modifier.clickable { copy(src); dismiss() })
            }
        }
    }
}

@Composable
fun DownloadsSheet(dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("es")) }
    fun mb(b: Long) = if (b < 0) "?" else if (b < 1_048_576) "${b / 1024} KB" else String.format(java.util.Locale("es"), "%.1f MB", b / 1_048_576.0)
    ModalBottomSheet(onDismissRequest = dismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Descargas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (Downloads.items.any { it.status != "Descargando" }) TextButton({ Downloads.clear(ctx) }) { Text("Limpiar lista") }
        }
        if (Downloads.items.isEmpty()) Text("Lo que descargues aparecerá aquí y en Descargas/Brisa.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(Downloads.items.size, key = { System.identityHashCode(Downloads.items[it]) }) { i ->
                val d = Downloads.items[i]
                val smooth by animateFloatAsState(if (d.total > 0) d.done.toFloat() / d.total else 0f, tween(450), label = "progreso")
                ListItem(
                    leadingContent = { Icon(when {
                        d.mime.startsWith("image") -> Icons.Outlined.Image
                        d.mime.startsWith("video") -> Icons.Outlined.Movie
                        d.mime.startsWith("audio") -> Icons.Outlined.MusicNote
                        d.mime.contains("pdf") -> Icons.Outlined.PictureAsPdf
                        else -> Icons.Outlined.Description }, null) },
                    headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        if (d.status == "Descargando") Column {
                            if (d.total > 0) LinearProgressIndicator({ smooth }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            Text("${mb(d.done)} de ${mb(d.total)}")
                        } else Text(if (d.status == "Terminada") "${mb(d.total)} · ${fmt.format(java.util.Date(d.time))}" else d.status)
                    },
                    trailingContent = { if (d.status == "Descargando") IconButton({ d.cancel = true }) { Icon(Icons.Filled.Close, "Cancelar") } },
                    modifier = Modifier.animateItem().clickable(enabled = d.uri != null) { Downloads.open(ctx, d) })
            }
        }
    }
}

/** Informe de privacidad de los últimos 7 días. */
@Composable
fun ReportSheet(dismiss: () -> Unit, openTab: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Report.version   // repintar con datos nuevos
    val days = Report.perDay(7)
    val max = (days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val names = listOf("L", "M", "X", "J", "V", "S", "D")
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Informe de privacidad", style = MaterialTheme.typography.titleLarge)
            Text("Últimos 7 días · solo en este móvil", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Report.total(7)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("bloqueados por la protección de Gecko", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { (d, n) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(0.82f * n / max + 0.02f).clip(RoundedCornerShape(6.dp))
                            .background(if (d == java.time.LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer))
                        Text(names[d.dayOfWeek.value - 1], fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            val top = Report.topSites(7)
            if (top.isNotEmpty()) {
                Text("Webs con más rastreadores", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
                top.forEach { (h, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(h, 28.dp, 6.dp)
                        Text(h, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n", fontWeight = FontWeight.Medium)
                    }
                }
            } else Text("Aún no hay datos. Navega un poco y vuelve.", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            // #18: con uBlock activo es normal ver pocos o ningún bloqueo aquí, porque uBlock corta antes
            Text("Aquí solo se cuenta lo que bloquea la protección de Gecko. uBlock Origin actúa antes y bloquea casi todo por su cuenta, así que con él activo es normal ver pocos bloqueos (o ninguno). Sus cifras están en sus estadísticas.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            OutlinedButton({ Engine.ublockOptions(ctx) { u -> if (u != null) openTab(u) } }, Modifier.padding(top = 8.dp)) {
                Text("Estadísticas y filtros de uBlock Origin")
            }
            TextButton({ Report.clear() }, Modifier.padding(top = 4.dp)) { Text("Borrar informe") }
        }
    }
}
