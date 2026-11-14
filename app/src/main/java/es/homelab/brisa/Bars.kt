@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package es.homelab.brisa

import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
fun AddressBar(tab: Tab, editing: Boolean, setEditing: (Boolean) -> Unit, onMenu: () -> Unit, onSwipe: (Int) -> Unit, dropDown: Boolean = false) {
    // Al tocar la barra se empieza en blanco; la dirección actual queda como sugerencia «Editar» por si se quiere retocar.
    var text by remember(editing) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Solo se cierra al PERDER el foco después de haberlo tenido; el aviso inicial «sin foco» se ignora.
    var hadFocus by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing) {
            androidx.compose.runtime.withFrameNanos { } // esperar a que el campo exista
            focus.requestFocus(); keyboard?.show()
        } else { fm.clearFocus(); keyboard?.hide() }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val ctx = LocalContext.current

    var remote by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(text, editing) {
        remote = emptyList()
        if (!editing || tab.private || !Settings.suggest || text.isBlank() || Settings.looksLikeAddress(text)) return@LaunchedEffect
        kotlinx.coroutines.delay(200)
        remote = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Settings.fetchSuggestions(ctx, text) }
            .filter { !it.equals(text.trim(), ignoreCase = true) }
    }
    var barWidth by remember { mutableIntStateOf(0) }
    val suggestionsUi: @Composable () -> Unit = { if (editing && !tab.private) {
        val hist = History.get(ctx).suggest(text, max = if (remote.isEmpty()) 6 else 3)
        val current = tab.url.takeUnless { tab.isHome }
        // Una palabra suelta (proxmox, nas…) se busca, pero se ofrece ir a esa máquina de la red
        val bareHost = text.trim().takeIf { it.matches(Regex("^[a-zA-Z0-9-]{2,}$")) }
        if (hist.isNotEmpty() || remote.isNotEmpty() || bareHost != null || (current != null && text.isEmpty())) Column(Modifier.padding(bottom = 8.dp).glass(RoundedCornerShape(22.dp))) {
            if (bareHost != null) Row(Modifier.fillMaxWidth().clickable { tab.open("http://$bareHost/"); setEditing(false) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Lan, null, Modifier.size(20.dp), tint = glassContentDim()) }
                Text("Ir a http://$bareHost/", Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
            }
            if (current != null && text.isEmpty()) Row(Modifier.fillMaxWidth().clickable { text = current }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(tab.host, 28.dp, 6.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(tab.title.ifBlank { tab.host }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                    Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = glassContentDim())
                }
                Icon(Icons.Outlined.Edit, "Editar dirección", Modifier.size(20.dp), tint = glassContentDim())
            }
            hist.forEach { e ->
                Row(Modifier.fillMaxWidth().clickable { tab.open(e.url); setEditing(false) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    SiteIcon(History.hostOf(e.url), 28.dp, 6.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.title.ifBlank { History.hostOf(e.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                        Text(History.hostOf(e.url), maxLines = 1, fontSize = 12.sp, color = glassContentDim())
                    }
                }
            }
            remote.forEach { q ->
                Row(Modifier.fillMaxWidth().clickable { tab.load(q); setEditing(false) }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = glassContentDim()) }
                    Text(q, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, color = glassContent())
                    Icon(Icons.Outlined.NorthWest, "Completar", Modifier.size(18.dp).clickable { text = q + " " }, tint = glassContentDim())
                }
            }
        }
    } }
    if (!dropDown) suggestionsUi()

    Box(Modifier.onSizeChanged { barWidth = it.width }, contentAlignment = Alignment.BottomCenter) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).glass(RoundedCornerShape(25.dp), tint = if (tab.private) PrivateGlass else null)
            .pointerInput(tab) {
                detectHorizontalDragGestures(onDragEnd = {
                    if (drag < -120) onSwipe(1) else if (drag > 120) onSwipe(-1); drag = 0f
                }) { _, d -> drag += d }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = glassContentDim(tab.private)
        val strong = glassContent(tab.private)
        if (editing) {
            Icon(Icons.Outlined.Search, null, tint = tint)
            BasicTextFieldUrl(text, { text = it }, color = strong, placeholder = if (tab.isHome) "Buscar o escribir web" else tab.host, modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus && editing) setEditing(false) }) {
                if (text.isNotBlank()) tab.load(text); setEditing(false)
            }
            if (text.isNotEmpty()) IconButton({ text = "" }) { Icon(Icons.Filled.Cancel, "Borrar", tint = tint) }
        } else {
            Text("aA", fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.clickable(onClick = onMenu).padding(6.dp))
            if (tab.readerable || tab.reading) IconButton({ tab.toggleReader() }, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Description, if (tab.reading) "Salir del modo lectura" else "Modo lectura",
                    tint = if (tab.reading) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(20.dp))
            }
            Row(Modifier.weight(1f).clickable { setEditing(true) }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                SecurityIcon(tab, 14.dp, tint)
                Text(if (tab.isHome) "Buscar o escribir web" else tab.host, color = if (tab.isHome) tint else strong, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            if (tab.progress < 100) IconButton({ tab.session.stop() }) { Icon(Icons.Filled.Close, "Detener", tint = tint) }
            else IconButton({ tab.session.reload() }, enabled = !tab.isHome) { Icon(Icons.Filled.Refresh, "Recargar", tint = tint) }
        }
    }
    // Progreso de carga: una línea fina dentro de la cápsula
    if (tab.progress < 100 && !editing) LinearProgressIndicator({ tab.progress / 100f },
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 3.dp).height(2.dp).clip(RoundedCornerShape(1.dp)),
        trackColor = androidx.compose.ui.graphics.Color.Transparent)
    }
    // Tablet: las sugerencias salen debajo de la barra, por encima de la página
    if (dropDown && editing) {
        val off = with(androidx.compose.ui.platform.LocalDensity.current) { 58.dp.roundToPx() }
        androidx.compose.ui.window.Popup(offset = androidx.compose.ui.unit.IntOffset(0, off),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            Box(Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { barWidth.toDp() })) { suggestionsUi() }
        }
    }
}

@Composable
fun BasicTextFieldUrl(value: String, onChange: (String) -> Unit, modifier: Modifier,
                     color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
                     placeholder: String? = null, onGo: () -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, modifier.padding(horizontal = 8.dp), singleLine = true,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) Text(placeholder, color = color.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = color),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() })
    )
}

@Composable
fun FindBar(tab: Tab) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; focus.requestFocus() }
    Row(Modifier.fillMaxWidth().height(50.dp).glass(RoundedCornerShape(25.dp)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextFieldUrl(q, { q = it; tab.find(it) }, Modifier.weight(1f).focusRequester(focus), color = glassContent(), placeholder = "Buscar en la página") { tab.find(q) }
        Text(tab.findResult, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton({ tab.find(q, back = true) }) { Icon(Icons.Filled.KeyboardArrowUp, "Anterior") }
        IconButton({ tab.find(q) }) { Icon(Icons.Filled.KeyboardArrowDown, "Siguiente") }
        TextButton({ tab.endFind() }) { Text("OK") }
    }
}

/** Barra de tablet: navegación a la izquierda, dirección en el centro, acciones a la derecha. */
@Composable
fun TabletToolbar(tab: Tab, tabCount: Int, favs: Favorites, editing: Boolean, setEditing: (Boolean) -> Unit,
                  onMenu: () -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, ui: UiState, onDownloads: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ tab.session.goBack() }, enabled = tab.canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
        IconButton({ tab.session.goForward() }, enabled = tab.canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Adelante") }
        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                AddressBar(tab, editing, setEditing, onMenu = onMenu, onSwipe = {}, dropDown = true)
            }
        }
        IconButton({
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url), null))
        }, enabled = !tab.isHome) { Icon(Icons.Outlined.Share, "Compartir") }
        IconButton({ if (!tab.isHome) favs.toggle(tab.title.ifBlank { tab.host }, tab.url) }, enabled = !tab.isHome) {
            Icon(if (favs.has(tab.url)) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                if (favs.has(tab.url)) "Quitar de favoritos" else "Añadir a favoritos")
        }
        DownloadButton(ui, onDownloads)
        IconButton(onNewTab) { Icon(Icons.Filled.Add, "Nueva pestaña") }
        IconButton(onTabs) {
            Box(Modifier.size(22.dp).border(1.6.dp, LocalContentColor.current, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text("$tabCount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Pestañas abiertas: $tabCount" })
            }
        }
    }
}

/**
 * Botón de descargas con anillo de progreso. Aparece mientras hay descargas (y unos segundos después);
 * al terminar se convierte en ✓ con un rebote y una vibración suave; si falla, el anillo se pone rojo.
 */
@Composable
fun DownloadButton(ui: UiState, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val seenFinished = remember { Downloads.finished }
    val seenFailed = remember { Downloads.failed }
    var recent by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val bounce = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Downloads.finished) {
        if (Downloads.finished <= seenFinished) return@LaunchedEffect
        recent = true; done = true; error = false
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        bounce.animateTo(1.3f, spring(dampingRatio = 0.35f, stiffness = 600f))
        bounce.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
        kotlinx.coroutines.delay(1800); done = false
        kotlinx.coroutines.delay(3000); if (Downloads.progress() == null) recent = false
    }
    LaunchedEffect(Downloads.failed) {
        if (Downloads.failed <= seenFailed) return@LaunchedEffect
        recent = true; error = true
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT
            else android.view.HapticFeedbackConstants.LONG_PRESS)
        kotlinx.coroutines.delay(5000); if (Downloads.progress() == null) { recent = false; error = false }
    }
    val progress = Downloads.progress()
    LaunchedEffect(progress != null) { if (progress != null) { recent = true; error = false } }
    val smooth by animateFloatAsState((progress ?: 1f).coerceAtLeast(0f), tween(450), label = "anillo")

    AnimatedVisibility(progress != null || recent, enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(), exit = scaleOut() + fadeOut()) {
        IconButton(onClick, Modifier
            .onGloballyPositioned { ui.downloadTarget = it.boundsInRoot().center }
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
            Box(contentAlignment = Alignment.Center) {
                val ringColor = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                if (progress != null && progress < 0f) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = ringColor)
                else CircularProgressIndicator({ smooth }, Modifier.size(26.dp), strokeWidth = 2.5.dp, color = ringColor,
                    trackColor = LocalContentColor.current.copy(alpha = 0.18f))
                AnimatedContent(when { error -> 2; done -> 1; else -> 0 }, label = "icono") { st ->
                    Icon(when (st) { 2 -> Icons.Filled.PriorityHigh; 1 -> Icons.Filled.Check; else -> Icons.Filled.ArrowDownward },
                        "Descargas", Modifier.size(14.dp), tint = if (st == 2) MaterialTheme.colorScheme.error else LocalContentColor.current)
                }
            }
        }
    }
}

/** Al empezar una descarga, un icono vuela desde el centro hasta el botón de descargas (en arco, como Safari). */
@Composable
fun DownloadFly(ui: UiState) {
    val seen = remember { Downloads.started }
    var flying by remember { mutableStateOf(false) }
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Downloads.started) {
        if (Downloads.started <= seen) return@LaunchedEffect
        t.snapTo(0f); flying = true
        kotlinx.coroutines.delay(80)   // dejar que aparezca el botón para saber dónde está
        t.animateTo(1f, tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        flying = false
    }
    if (!flying) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
        val from = androidx.compose.ui.geometry.Offset(w / 2, h / 2)
        val to = ui.downloadTarget ?: androidx.compose.ui.geometry.Offset(w - with(d) { 60.dp.toPx() }, h - with(d) { 60.dp.toPx() })
        val arc = with(d) { 140.dp.toPx() }
        val half = with(d) { 28.dp.toPx() }
        Box(Modifier
            .offset {
                val v = t.value
                val x = from.x + (to.x - from.x) * v
                val y = from.y + (to.y - from.y) * v - kotlin.math.sin(Math.PI * v).toFloat() * arc
                androidx.compose.ui.unit.IntOffset((x - half).toInt(), (y - half).toInt())
            }
            .size(56.dp)
            .graphicsLayer {
                val v = t.value
                val sc = 1.35f - 0.95f * v
                scaleX = sc; scaleY = sc
                alpha = if (v > 0.85f) ((1f - v) / 0.15f).coerceIn(0f, 1f) else 1f
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ArrowDownward, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Icono de la web (o su inicial) en un cuadrado redondeado. */
@Composable
fun SiteIcon(host: String, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp = 8.dp) {
    val icon = WebIcons.get(LocalContext.current, host)
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(
        if (icon != null) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        if (icon != null) Image(icon, null, Modifier.fillMaxSize(0.72f))
        else Text(host.take(1).uppercase(), fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** Barra de una pestaña privada bloqueada: sin dominio ni dirección. Tocar = desbloquear; se puede ir a las pestañas. */
@Composable
fun LockedBar(tabCount: Int, onUnlock: () -> Unit, onTabs: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(50.dp).glass(RoundedCornerShape(25.dp), tint = PrivateGlass).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = onUnlock), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, "Pestañas privadas bloqueadas", Modifier.size(16.dp), tint = glassContent(true))
            Text("Pestaña privada bloqueada · Desbloquear", color = glassContent(true), fontSize = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        }
        IconButton(onTabs) {
            Box(Modifier.size(22.dp).border(1.6.dp, glassContent(true), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text("$tabCount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = glassContent(true),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Pestañas abiertas: $tabCount" })
            }
        }
    }
}

/**
 * Candado según la seguridad real que informa Gecko (#73), no según si la dirección empieza por https:
 * candado si es segura; aviso si se ha aceptado un certificado no válido; nada en http o páginas de error.
 */
@Composable
fun SecurityIcon(tab: Tab, size: androidx.compose.ui.unit.Dp, tint: androidx.compose.ui.graphics.Color) {
    when {
        tab.certException -> Icon(Icons.Outlined.GppMaybe, "Excepción de certificado", Modifier.size(size), tint = MaterialTheme.colorScheme.error)
        tab.secure && !tab.isHome -> Icon(Icons.Filled.Lock, "Conexión segura", Modifier.size(size), tint = tint)
    }
}
